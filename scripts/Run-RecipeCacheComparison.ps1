[CmdletBinding()]
param(
    [ValidateRange(1, 196608)][int]$Count = 30000,
    [ValidateRange(1, 10000)][int]$Cycles = 64,
    [ValidateRange(0, 10000)][int]$Warmup = 200,
    [ValidatePattern('^[1-9][0-9]*[MG]$')][string]$Heap = '12G',
    [ValidatePattern('^[A-Za-z0-9_-]+$')][string]$BatchId = ('recipe-cache-' + (Get-Date -Format 'yyyyMMddHHmmss'))
)

$ErrorActionPreference = 'Stop'
$recipeProject = Split-Path -Parent $PSScriptRoot
$recipeReportRoot = Join-Path $recipeProject "build/reports/recipe-cache-comparison/$BatchId"
if (Test-Path -LiteralPath $recipeReportRoot) { throw "Report already exists: $recipeReportRoot" }
New-Item -ItemType Directory -Path $recipeReportRoot | Out-Null
$recipeRows = [Collections.Generic.List[object]]::new()
[ordered]@{
    batchId = $BatchId; machineCount = $Count; operationsPerLane = $Cycles
    warmupTicks = $Warmup; heap = $Heap
    scenario = 'recipe-different'; patterns = @('cached', 'cycle')
    supplementalSmokeOnlyRecipes = @('BRUSHED: minecraft:clay_ball -> minecraft:brick', 'BRUSHED: minecraft:slime_ball -> minecraft:string')
    switchingTrigger = 'One complete verified output operation on the real output slot/tank; switch each lane only at that boundary.'
    serialExecution = $true
    scope = 'Existing 39 registered core machine variants. Every lane completes the specified operation count. Real World ticks and installed maximum upgrades.'
} | ConvertTo-Json -Depth 5 | Set-Content -LiteralPath (Join-Path $recipeReportRoot 'manifest.json') -Encoding UTF8
Push-Location $recipeProject
try {
    foreach ($recipePattern in @('cached', 'cycle')) {
        $recipeLive = @(Get-CimInstance Win32_Process | Where-Object { $_.Name -match '^java' -and $_.CommandLine -like '*mekanism.machine.stress*' })
        if ($recipeLive.Count -gt 0) { throw "Another stress JVM is running: $($recipeLive.ProcessId -join ',')" }
        $recipeRunId = "$BatchId-$recipePattern"
        $recipeCaseDir = Join-Path $recipeProject "build/reports/machine-stress-smoke/recipe-different-$Count-server-$recipeRunId"
        $recipeLog = Join-Path $recipeReportRoot "$recipePattern-gradle.log"
        Write-Output "CACHE_CASE_BEGIN pattern=$recipePattern count=$Count cycles=$Cycles runId=$recipeRunId"
        $recipeArgs = @('runServer', '--no-daemon', '-Pmachine_stress_smoke_test=true',
            '-Pmachine_stress_scenario=recipe-different', "-Pmachine_stress_count=$Count",
            "-Pmachine_stress_recipe_cycles=$Cycles", "-Pmachine_stress_recipe_pattern=$recipePattern",
            "-Pmachine_stress_run_id=$recipeRunId", "-Pmachine_stress_warmup=$Warmup", "-Pmachine_stress_heap=$Heap")
        $ErrorActionPreference = 'Continue'
        try { & (Join-Path $recipeProject 'gradlew.bat') @recipeArgs *> $recipeLog; $recipeExit = $LASTEXITCODE }
        finally { $ErrorActionPreference = 'Stop' }
        $recipeFailure = $null
        $recipeResult = $null
        $recipeLaneCount = 0
        $recipeReuses = 0L
        $recipeReplacements = 0L
        try {
            if ($recipeExit -ne 0) { throw "Gradle exited with $recipeExit" }
            $recipeResult = Get-Content -Raw (Join-Path $recipeCaseDir 'result.json') | ConvertFrom-Json
            if (!$recipeResult.functionalPass -or $recipeResult.count -ne $Count -or $recipeResult.runId -ne $recipeRunId) { throw 'Functional/identity check failed' }
            if (!$recipeResult.sparkActiveConfirmed -or !$recipeResult.sparkStoppedConfirmed -or !$recipeResult.sparkInactiveConfirmed) { throw 'Spark lifecycle was incomplete' }
            if ($recipeResult.recipeCycles.pattern -ne $recipePattern -or $recipeResult.recipeCycles.targetOperationsPerLane -ne $Cycles) { throw 'Wrong cycle target' }
            foreach ($recipeArtifact in @('profile.sparkprofile', 'spark-messages.txt', 'recipe-cycle-lanes.csv', 'recipe-cycle-catalog.json', 'latest.log')) {
                $recipeArtifactPath = Join-Path $recipeCaseDir $recipeArtifact
                if (!(Test-Path -LiteralPath $recipeArtifactPath) -or (Get-Item -LiteralPath $recipeArtifactPath).Length -eq 0) { throw "Missing $recipeArtifact" }
            }
            $recipeSeen = [Collections.Generic.HashSet[string]]::new()
            $recipeExpectedLanes = 0
            foreach ($recipeType in $recipeResult.recipeCycles.types.PSObject.Properties) { $recipeExpectedLanes += $recipeType.Value.lanes }
            Import-Csv -LiteralPath (Join-Path $recipeCaseDir 'recipe-cycle-lanes.csv') | ForEach-Object {
                $recipeLane = $_
                if (!$recipeSeen.Add("$($recipeLane.machine):$($recipeLane.lane)")) { throw 'Duplicate lane evidence' }
                if ([int]$recipeLane.completed -ne $Cycles -or [long]$recipeLane.endTick -le [long]$recipeLane.startTick) { throw 'A lane did not complete the requested cycles' }
                $recipeExpectedA = if ($recipePattern -eq 'cached') { $Cycles } else { [int][Math]::Ceiling($Cycles / 3.0) }
                $recipeExpectedB = if ($recipePattern -eq 'cached') { 0 } else { [int][Math]::Floor(($Cycles + 1) / 3.0) }
                $recipeExpectedC = if ($recipePattern -eq 'cached') { 0 } else { [int][Math]::Floor($Cycles / 3.0) }
                if ([int]$recipeLane.A -ne $recipeExpectedA -or [int]$recipeLane.B -ne $recipeExpectedB -or [int]$recipeLane.C -ne $recipeExpectedC) { throw 'A lane did not follow the requested A/B/C sequence' }
                if ($recipePattern -eq 'cached' -and ([long]$recipeLane.cacheBindingReuses -ne $Cycles -or [long]$recipeLane.cacheBindingReplacements -ne 0)) { throw 'A repeated recipe did not retain its warmed cache binding' }
                if ($recipePattern -eq 'cycle' -and ([long]$recipeLane.cacheBindingReuses -ne 0 -or [long]$recipeLane.cacheBindingReplacements -ne $Cycles)) { throw 'A recipe switch did not replace the previous cache binding' }
                $recipeLaneCount++
                $recipeReuses += [long]$recipeLane.cacheBindingReuses
                $recipeReplacements += [long]$recipeLane.cacheBindingReplacements
            }
            if ($recipeLaneCount -ne $recipeExpectedLanes -or $recipeLaneCount -lt $Count -or $recipeReuses + $recipeReplacements -ne [long]$recipeLaneCount * $Cycles) { throw 'Incomplete lane/cache evidence' }
        } catch { $recipeFailure = $_.Exception.Message }
        $recipeRow = [ordered]@{
            pattern = $recipePattern; machineCount = $Count; operationsPerLane = $Cycles
            status = $(if ($null -eq $recipeFailure) { 'PASS' } else { 'FAILED' })
            exitCode = $recipeExit; failure = $recipeFailure; reportDirectory = $recipeCaseDir
            lanes = $recipeLaneCount; totalOperations = [long]$recipeLaneCount * $Cycles
            cacheBindingReuses = $recipeReuses; cacheBindingReplacements = $recipeReplacements
        }
        if ($null -ne $recipeResult) {
            $recipeRow['sampleTicks'] = @($recipeResult.samples).Count
            $recipeRow['machineMeanMs'] = $recipeResult.machineMeanNanos / 1000000.0
            $recipeRow['machineP95Ms'] = $recipeResult.machineP95Nanos / 1000000.0
            $recipeRow['machineP99Ms'] = $recipeResult.machineP99Nanos / 1000000.0
            $recipeRow['machineMaxMs'] = $recipeResult.machineMaxNanos / 1000000.0
            $recipeRow['methodTiming'] = $recipeResult.recipeMethodTiming
        }
        $recipeRows.Add([pscustomobject]$recipeRow)
        $recipeRows | ConvertTo-Json -Depth 10 | Set-Content -LiteralPath (Join-Path $recipeReportRoot 'summary.json') -Encoding UTF8
        Write-Output "CACHE_CASE_END pattern=$recipePattern status=$($recipeRow.status) lanes=$recipeLaneCount failure=$recipeFailure"
        if ($null -ne $recipeFailure) { throw "Cache comparison failed; inspect $recipeReportRoot" }
    }
} finally { Pop-Location }
Write-Output "CACHE_COMPARISON_REPORT $recipeReportRoot"
