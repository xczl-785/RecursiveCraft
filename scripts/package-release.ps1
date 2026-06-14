param(
    [string]$OutputRoot = "dist",
    [string]$ReleaseSuffix = ""
)

$ErrorActionPreference = "Stop"

$repoRoot = Split-Path -Parent $PSScriptRoot
$dateStamp = Get-Date -Format "yyyy-MM-dd"
$releaseRoot = Join-Path $repoRoot $OutputRoot
$dayRoot = Join-Path $releaseRoot $dateStamp
$normalizedSuffix = $ReleaseSuffix.Trim()
if ($normalizedSuffix -and -not $normalizedSuffix.StartsWith("-")) {
    $normalizedSuffix = "-$normalizedSuffix"
}

if (-not (Test-Path $dayRoot)) {
    New-Item -ItemType Directory -Path $dayRoot | Out-Null
}

$branches = @("1.20.1", "1.21.1")

git -C $repoRoot worktree prune --expire now | Out-Null

foreach ($branch in $branches) {
    $worktreePath = Join-Path $repoRoot ".worktrees\pkg-$branch"
    if (Test-Path $worktreePath) {
        Remove-Item -LiteralPath ("\\?\$worktreePath") -Recurse -Force
    }

    git -C $repoRoot worktree add --detach $worktreePath $branch | Out-Null

    try {
        & (Join-Path $repoRoot "gradlew.bat") -p $worktreePath clean build --no-daemon

        $branchOutput = Join-Path $dayRoot "recursivecraft-$branch"
        if (Test-Path $branchOutput) {
            Remove-Item $branchOutput -Recurse -Force
        }
        New-Item -ItemType Directory -Path $branchOutput | Out-Null

        $version = Select-String -Path (Join-Path $worktreePath "gradle.properties") -Pattern '^mod_version=(.+)$' |
            ForEach-Object { $_.Matches[0].Groups[1].Value } |
            Select-Object -First 1
        $minecraftVersion = Select-String -Path (Join-Path $worktreePath "gradle.properties") -Pattern '^minecraft_version=(.+)$' |
            ForEach-Object { $_.Matches[0].Groups[1].Value } |
            Select-Object -First 1
        $platforms = Select-String -Path (Join-Path $worktreePath "gradle.properties") -Pattern '^enabled_platforms=(.+)$' |
            ForEach-Object { $_.Matches[0].Groups[1].Value.Split(",") } |
            ForEach-Object { $_.Trim() } |
            Where-Object { $_ } |
            Select-Object -Unique

        foreach ($platform in $platforms) {
            $jarPath = Join-Path $worktreePath "$platform\build\libs\recursivecraft-$version.jar"
            if (-not (Test-Path $jarPath)) {
                throw "Expected release jar not found: $jarPath"
            }
            Copy-Item $jarPath (Join-Path $branchOutput "recursivecraft-mc$minecraftVersion-$platform-v$version$normalizedSuffix.jar")
        }

        $zipPath = Join-Path $dayRoot "recursivecraft-$branch$normalizedSuffix.zip"
        if (Test-Path $zipPath) {
            Remove-Item $zipPath -Force
        }
        Compress-Archive -Path (Join-Path $branchOutput "*") -DestinationPath $zipPath
    }
    finally {
        if (Test-Path $worktreePath) {
            Remove-Item -LiteralPath ("\\?\$worktreePath") -Recurse -Force
        }
    }
}

if (Test-Path (Join-Path $repoRoot ".worktrees")) {
    if (-not (Get-ChildItem (Join-Path $repoRoot ".worktrees") -Force | Select-Object -First 1)) {
        Remove-Item (Join-Path $repoRoot ".worktrees") -Force
    }
}
