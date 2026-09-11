[CmdletBinding()]
param(
    [ValidatePattern('^[A-Za-z0-9_-]+$')]
    [string]$RunId = ('baseline-' + (Get-Date -Format 'yyyyMMddHHmmss'))
)

$ErrorActionPreference = 'Stop'
$recipeProject = [IO.Path]::GetFullPath((Split-Path -Parent $PSScriptRoot))
$recipeEvidence = [IO.Path]::GetFullPath((Join-Path $recipeProject ".analysis/async-recipe-rebuild/$RunId"))
if (!$recipeEvidence.StartsWith($recipeProject + [IO.Path]::DirectorySeparatorChar, [StringComparison]::OrdinalIgnoreCase)) {
    throw 'Evidence directory must stay inside the project.'
}
if (Test-Path -LiteralPath $recipeEvidence) { throw "Baseline already exists: $recipeEvidence" }
New-Item -ItemType Directory -Path $recipeEvidence | Out-Null
$recipeFiles = @('build.gradle', 'gradle.properties', 'settings.gradle', 'gradlew', 'gradlew.bat', 'tags.properties')
foreach ($recipeDirectory in @('src', 'gradle', 'scripts', 'run/config', 'run/scripts')) {
    if (!(Test-Path -LiteralPath (Join-Path $recipeProject $recipeDirectory))) { continue }
    $recipeFiles += @(Get-ChildItem -LiteralPath (Join-Path $recipeProject $recipeDirectory) -File -Recurse |
        Where-Object { $_.Extension -ne '.sparkprofile' } |
        ForEach-Object { [IO.Path]::GetRelativePath($recipeProject, $_.FullName) })
}
foreach ($recipeOptional in @('run/eula.txt', 'run/server.properties')) {
    if (Test-Path -LiteralPath (Join-Path $recipeProject $recipeOptional)) { $recipeFiles += $recipeOptional }
}
$recipeManifest = [Collections.Generic.List[object]]::new()
foreach ($recipeRelative in $recipeFiles | Sort-Object -Unique) {
    $recipeSource = Join-Path $recipeProject $recipeRelative
    if (!(Test-Path -LiteralPath $recipeSource -PathType Leaf)) { continue }
    $recipeDestination = Join-Path $recipeEvidence $recipeRelative
    New-Item -ItemType Directory -Path (Split-Path -Parent $recipeDestination) -Force | Out-Null
    Copy-Item -LiteralPath $recipeSource -Destination $recipeDestination
    $recipeOriginalHash = (Get-FileHash -LiteralPath $recipeSource -Algorithm SHA256).Hash
    $recipeCopiedHash = (Get-FileHash -LiteralPath $recipeDestination -Algorithm SHA256).Hash
    if ($recipeOriginalHash -ne $recipeCopiedHash) { throw "Baseline changed while copying: $recipeRelative" }
    $recipeManifest.Add([ordered]@{ path = $recipeRelative.Replace('\', '/'); sha256 = $recipeCopiedHash })
}
$recipeLibraries = @(Get-ChildItem -LiteralPath (Join-Path $recipeProject 'libs') -File -Filter '*.jar' |
    ForEach-Object { [ordered]@{ name = $_.Name; length = $_.Length; sha256 = (Get-FileHash -LiteralPath $_.FullName -Algorithm SHA256).Hash } })
[ordered]@{
    runId = $RunId
    capturedAt = (Get-Date).ToString('o')
    project = $recipeProject
    userBaseline = 'State restored by the user before reimplementing the change associated with 848cf1ea7; no Git reset was performed here.'
    productionSourcesChangedByThisWork = $false
    testStatus = 'Production compile succeeded. Regular test compilation is blocked by tests referencing the removed async implementation. Focused lookup checks are reported separately.'
    files = $recipeManifest
    libraries = $recipeLibraries
} | ConvertTo-Json -Depth 6 | Set-Content -LiteralPath (Join-Path $recipeEvidence 'manifest.json') -Encoding UTF8
Write-Output "BASELINE_SAVED $recipeEvidence files=$($recipeManifest.Count) libraries=$($recipeLibraries.Count)"
