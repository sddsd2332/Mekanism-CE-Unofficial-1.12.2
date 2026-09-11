[CmdletBinding()]
param(
    [int[]]$Counts = @(1000, 5000, 10000, 30000),
    [ValidateSet('no-recipe', 'recipe-different', 'mixed')]
    [string[]]$Scenarios = @('no-recipe', 'recipe-different', 'mixed'),
    [ValidateRange(0, 100000)]
    [int]$Warmup = 20,
    [ValidateRange(1, 100000)]
    [int]$Samples = 20,
    [ValidatePattern('^[1-9][0-9]*[MG]$')]
    [string]$Heap = '12G',
    [Nullable[double]]$MaxMachineMs,
    [ValidatePattern('^[A-Za-z0-9_-]{1,32}$')]
    [string]$BatchId = (Get-Date -Format 'yyyyMMddHHmmss'),
    [switch]$ListOnly
)

$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path -Parent $PSScriptRoot
$reportRoot = Join-Path $projectRoot "build/reports/machine-stress-smoke/server-matrix-$BatchId"
$invariant = [System.Globalization.CultureInfo]::InvariantCulture
$cases = @()
if ($Counts.Count -eq 0 -or $Scenarios.Count -eq 0) { throw 'The matrix must contain at least one case.' }
if ($null -ne $MaxMachineMs -and ($MaxMachineMs -le 0 -or [double]::IsNaN($MaxMachineMs) -or [double]::IsInfinity($MaxMachineMs))) {
    throw 'MaxMachineMs must be positive and finite.'
}
foreach ($count in $Counts) {
    if ($count -lt 1 -or $count -gt 196608) { throw "Invalid machine count: $count" }
    foreach ($scenario in $Scenarios) {
        $runId = "$BatchId-$($cases.Count + 1)"
        $cases += [pscustomobject]@{
            Count = $count
            Scenario = $scenario
            RunId = $runId
            ReportDirectory = Join-Path $projectRoot "build/reports/machine-stress-smoke/$scenario-$count-server-$runId"
        }
    }
}
if ($ListOnly) {
    $cases | Format-Table Count, Scenario, RunId -AutoSize
    return
}
if (Test-Path -LiteralPath $reportRoot) { throw "Matrix results already exist: $reportRoot" }
$live = @(Get-CimInstance Win32_Process | Where-Object {
    $_.Name -match '^java' -and $_.CommandLine -like '*mekanism.machine.stress*'
})
if ($live.Count -gt 0) { throw "A machine stress JVM is already running: $($live.ProcessId -join ', ')" }
New-Item -ItemType Directory -Path $reportRoot | Out-Null
$manifest = [ordered]@{
    BatchId = $BatchId
    Environment = 'dedicated-server'
    EvaluationMode = $(if ($null -eq $MaxMachineMs) { 'measure-only' } else { 'threshold' })
    MaxMachineMs = $MaxMachineMs
    Warmup = $Warmup
    Samples = $Samples
    Heap = $Heap
    Cases = $cases
}
$manifest | ConvertTo-Json -Depth 6 | Set-Content -LiteralPath (Join-Path $reportRoot 'matrix.json') -Encoding UTF8
$rows = [System.Collections.Generic.List[object]]::new()

function Convert-Nanos($Report, [string]$Property, [double]$Divisor) {
    if ($null -eq $Report -or $null -eq $Report.$Property) { return $null }
    return [double]$Report.$Property / $Divisor
}

Push-Location $projectRoot
try {
    foreach ($case in $cases) {
        Write-Host "MATRIX_BEGIN count=$($case.Count) scenario=$($case.Scenario) runId=$($case.RunId)"
        $arguments = @('runServer', '--no-daemon', '-Pmachine_stress_smoke_test=true',
            "-Pmachine_stress_scenario=$($case.Scenario)", "-Pmachine_stress_count=$($case.Count)",
            "-Pmachine_stress_run_id=$($case.RunId)", "-Pmachine_stress_warmup=$Warmup",
            "-Pmachine_stress_samples=$Samples", "-Pmachine_stress_heap=$Heap")
        if ($null -ne $MaxMachineMs) {
            $arguments += '-Pmachine_stress_max_ms=' + $MaxMachineMs.ToString($invariant)
        }
        $caseLog = Join-Path $reportRoot "$($case.Scenario)-$($case.Count)-gradle.log"
        # Preserve stderr without treating JVM startup warnings as PowerShell failures.
        $ErrorActionPreference = 'Continue'
        try {
            & (Join-Path $projectRoot 'gradlew.bat') @arguments *> $caseLog
            $code = $LASTEXITCODE
        } finally {
            $ErrorActionPreference = 'Stop'
        }
        $report = $null
        $failure = $null
        try {
            $resultFile = Join-Path $case.ReportDirectory 'result.json'
            if (!(Test-Path -LiteralPath $resultFile)) { throw 'No result.json was produced.' }
            $report = Get-Content -LiteralPath $resultFile -Raw | ConvertFrom-Json
            if ($report.count -ne $case.Count -or $report.scenario -ne $case.Scenario -or $report.runId -ne $case.RunId) {
                throw 'Report identity does not match the requested case.'
            }
            if (!$report.functionalPass -or @($report.samples).Count -ne $Samples) {
                throw "Functional or sample-count check failed: $($report.failure)"
            }
            foreach ($artifact in @('profile.sparkprofile', 'spark-messages.txt', 'latest.log')) {
                $artifactPath = Join-Path $case.ReportDirectory $artifact
                if (!(Test-Path -LiteralPath $artifactPath) -or (Get-Item -LiteralPath $artifactPath).Length -eq 0) {
                    throw "Missing evidence: $artifact"
                }
            }
            if (!$report.sparkActiveConfirmed -or !$report.sparkStoppedConfirmed -or !$report.sparkInactiveConfirmed) {
                throw 'Spark lifecycle evidence is incomplete.'
            }
            if ($code -ne 0) { throw "Gradle exited with code $code; see $caseLog" }
        } catch {
            $failure = $_.Exception.Message
        }
        $row = [pscustomobject][ordered]@{
            Count = $case.Count
            Scenario = $case.Scenario
            Status = $(if ($null -eq $failure) { 'RECORDED' } else { 'FAILED' })
            FunctionalPass = ($null -ne $report -and $report.functionalPass)
            PerformancePass = $(if ($null -ne $report) { $report.performancePass } else { $null })
            MeanMs = Convert-Nanos $report 'machineMeanNanos' 1000000
            P95Ms = Convert-Nanos $report 'machineP95Nanos' 1000000
            MaxMs = Convert-Nanos $report 'machineMaxNanos' 1000000
            MeanSeconds = Convert-Nanos $report 'machineMeanNanos' 1000000000
            MaxSeconds = Convert-Nanos $report 'machineMaxNanos' 1000000000
            ReportDirectory = $case.ReportDirectory
            ExitCode = $code
            Error = $failure
        }
        $rows.Add($row)
        ConvertTo-Json -InputObject @($rows.ToArray()) -Depth 6 |
            Set-Content -LiteralPath (Join-Path $reportRoot 'summary.json') -Encoding UTF8
        $rows | Export-Csv -LiteralPath (Join-Path $reportRoot 'summary.csv') -NoTypeInformation -Encoding UTF8
        Write-Host "MATRIX_END count=$($case.Count) scenario=$($case.Scenario) status=$($row.Status) meanMs=$($row.MeanMs) maxMs=$($row.MaxMs)"
        $remaining = @(Get-CimInstance Win32_Process | Where-Object {
            $_.Name -match '^java' -and $_.CommandLine -like "*mekanism.machine.stress.runId=$($case.RunId)*"
        })
        if ($remaining.Count -gt 0) { throw "Case left a live JVM; refusing to overlap benchmarks: $($remaining.ProcessId -join ', ')" }
    }
} finally {
    Pop-Location
}
$rows | Format-Table Count, Scenario, Status, MeanSeconds, MaxSeconds -AutoSize
Write-Host "MATRIX_REPORT $reportRoot"
if (@($rows | Where-Object { $_.Status -ne 'RECORDED' }).Count -gt 0) { exit 1 }
