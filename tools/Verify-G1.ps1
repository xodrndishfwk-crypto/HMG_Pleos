param([switch]$Update)
$ErrorActionPreference = 'Stop'
$projectRoot = [IO.Path]::GetFullPath((Join-Path $PSScriptRoot '..'))
$manifestPath = Join-Path $projectRoot 'docs/g1/MANIFEST.json'
$groups = [ordered]@{
    contract = @('app/src/main/java/com/example/starlog2/contracts')
    fixture = @('fixtures/contracts')
    harness = @('app/src/test/java/com/example/starlog2/contracts', 'app/src/androidTest/java/com/example/starlog2/contracts', 'app/src/main/java/com/example/starlog2/platform/AndroidClock.kt', 'app/src/main/java/com/example/starlog2/integration/NavigationContractAdapter.kt', 'tools/Verify-G1.ps1', '.gitattributes', 'app/build.gradle.kts')
}
$entries = @()
foreach ($group in $groups.Keys) {
    foreach ($item in $groups[$group]) {
        $targetPath = Join-Path $projectRoot $item
        foreach ($file in (Get-ChildItem -LiteralPath $targetPath -File -Recurse | Sort-Object FullName)) {
            $entries += [ordered]@{ category=$group; path=$file.FullName.Substring($projectRoot.Length+1).Replace('\','/'); sha256=(Get-FileHash -LiteralPath $file.FullName -Algorithm SHA256).Hash.ToLowerInvariant() }
        }
    }
}
if ($Update) {
    $json = [ordered]@{ contractVersion='starlog-contracts-1.0.0'; files=$entries } | ConvertTo-Json -Depth 8
    [IO.File]::WriteAllText($manifestPath, $json.Replace("`r`n","`n")+"`n", [Text.UTF8Encoding]::new($false))
} else {
    $approved = Get-Content -LiteralPath $manifestPath -Raw | ConvertFrom-Json
    if ($approved.contractVersion -ne 'starlog-contracts-1.0.0' -or $approved.files.Count -ne $entries.Count) { throw 'Manifest version/count mismatch' }
    foreach ($entry in $entries) {
        $match = @($approved.files | Where-Object { $_.category -eq $entry.category -and $_.path -ceq $entry.path -and $_.sha256 -ceq $entry.sha256 })
        if ($match.Count -ne 1) { throw "Missing or changed approved file: $($entry.path)" }
    }
}
Write-Output "G1 manifest PASS ($($entries.Count) files)"
Write-Output "MANIFEST_SHA256=$((Get-FileHash -LiteralPath $manifestPath -Algorithm SHA256).Hash.ToLowerInvariant())"
