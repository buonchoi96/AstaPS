[CmdletBinding()]
param(
    [string]$RepoRoot = 'F:\Game Servers\AstaPS (7.1.0)',
    [string]$Branch = 'feature/beyond-editor-re',
    [string]$JavaHome = 'C:\Program Files\Java\jdk-21.0.12'
)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

# These files are intentionally customized on this machine.
# AutoPull temporarily backs them up, syncs Git, then restores them.
$PreserveLocalPaths = @(
    '.gitignore',
    'StartServer.ps1'
)

function Invoke-Git {
    param(
        [Parameter(Mandatory = $true)]
        [string[]]$Arguments
    )

    & git @Arguments
    if ($LASTEXITCODE -ne 0) {
        throw "git $($Arguments -join ' ') failed with exit code $LASTEXITCODE."
    }
}

function Test-GitTracked {
    param(
        [Parameter(Mandatory = $true)]
        [string]$Path
    )

    & git ls-files --error-unmatch -- "$Path" *> $null
    return ($LASTEXITCODE -eq 0)
}

function Restore-ProtectedCopies {
    param(
        [Parameter(Mandatory = $true)]
        [hashtable]$BackupMap
    )

    foreach ($relativePath in $BackupMap.Keys) {
        $source = $BackupMap[$relativePath]
        $destination = Join-Path $RepoRoot $relativePath
        $parent = Split-Path -Parent $destination

        if ($parent -and -not (Test-Path -LiteralPath $parent)) {
            New-Item -ItemType Directory -Path $parent -Force | Out-Null
        }

        Copy-Item -LiteralPath $source -Destination $destination -Force
        Write-Host "[AutoPull] Restored local file: $relativePath" -ForegroundColor DarkCyan
    }
}

if (-not (Test-Path -LiteralPath $RepoRoot)) {
    throw "Repository folder does not exist: $RepoRoot"
}

Set-Location -LiteralPath $RepoRoot

if (-not (Test-Path -LiteralPath '.git')) {
    throw "Not a Git repository: $RepoRoot"
}

if (-not (Get-Command git -ErrorAction SilentlyContinue)) {
    throw 'git.exe was not found in PATH.'
}

$gradleWrapper = Join-Path $RepoRoot 'gradlew.bat'
if (-not (Test-Path -LiteralPath $gradleWrapper)) {
    throw "Gradle wrapper was not found: $gradleWrapper"
}

if (-not (Test-Path -LiteralPath $JavaHome)) {
    throw "JAVA_HOME does not exist: $JavaHome"
}

$env:JAVA_HOME = $JavaHome
$env:Path = "$(Join-Path $JavaHome 'bin');$env:Path"

$backupRoot = Join-Path ([System.IO.Path]::GetTempPath()) (
    'AstaPS-AutoPull-' + [guid]::NewGuid().ToString('N')
)
New-Item -ItemType Directory -Path $backupRoot -Force | Out-Null

$backupMap = @{}
$protectedCopiesRestored = $false

try {
    Write-Host ''
    Write-Host '=== AstaPS AutoPull ===' -ForegroundColor Cyan
    Write-Host "[AutoPull] Repo:   $RepoRoot"
    Write-Host "[AutoPull] Branch: $Branch"
    Write-Host ''

    # ---------------------------------------------------------------------
    # 1. Preserve intentional local files.
    # ---------------------------------------------------------------------
    foreach ($relativePath in $PreserveLocalPaths) {
        $status = @(
            & git status --porcelain --untracked-files=all -- "$relativePath"
        )
        if ($LASTEXITCODE -ne 0) {
            throw "Could not inspect Git status for $relativePath."
        }

        if ($status.Count -eq 0) {
            continue
        }

        $source = Join-Path $RepoRoot $relativePath
        if (Test-Path -LiteralPath $source) {
            $safeName = $relativePath -replace '[\\/:*?"<>|]', '_'
            $backup = Join-Path $backupRoot $safeName

            Copy-Item -LiteralPath $source -Destination $backup -Force
            $backupMap[$relativePath] = $backup
            Write-Host "[AutoPull] Preserving local change: $relativePath" -ForegroundColor Yellow
        }

        # Normalize the working tree temporarily so git pull --ff-only cannot
        # be blocked by the local launcher/.gitignore edits.
        if (Test-GitTracked -Path $relativePath) {
            Invoke-Git -Arguments @(
                'restore',
                '--source=HEAD',
                '--staged',
                '--worktree',
                '--',
                $relativePath
            )
        }
        elseif (Test-Path -LiteralPath $source) {
            Remove-Item -LiteralPath $source -Force
        }
    }

    # ---------------------------------------------------------------------
    # 2. Generated protocol files are never allowed to block the pull.
    #
    # This fixes the exact failure:
    #   local modification of EnterScenePeerNotifyOuterClass.java
    #   -> git pull aborts
    #   -> AutoPull builds the old a6f2a25 source tree.
    # ---------------------------------------------------------------------
    Write-Host '[AutoPull] Resetting generated protocol files to current HEAD...'

    Invoke-Git -Arguments @(
        'restore',
        '--source=HEAD',
        '--staged',
        '--worktree',
        '--',
        'src/generated'
    )

    # Remove untracked generated leftovers from failed protoc/Gradle runs.
    Invoke-Git -Arguments @(
        'clean',
        '-fd',
        '--',
        'src/generated'
    )

    # Do not destroy arbitrary source edits. Anything else tracked and dirty
    # is treated as user work and causes a safe abort.
    $remainingTracked = @(
        & git status --porcelain --untracked-files=no
    )
    if ($LASTEXITCODE -ne 0) {
        throw 'Could not inspect the repository working tree.'
    }

    if ($remainingTracked.Count -gt 0) {
        Write-Host ''
        Write-Host '[AutoPull] Refusing to overwrite other tracked local changes:' -ForegroundColor Red
        $remainingTracked | ForEach-Object {
            Write-Host "  $_" -ForegroundColor Red
        }
        throw 'Commit, stash, or restore the files listed above, then run AutoPull again.'
    }

    # ---------------------------------------------------------------------
    # 3. Update the branch.
    # ---------------------------------------------------------------------
    Write-Host ''
    Write-Host '[AutoPull] Fetching origin...'
    Invoke-Git -Arguments @('fetch', 'origin')

    Write-Host "[AutoPull] Switching to $Branch..."
    Invoke-Git -Arguments @('switch', $Branch)

    Write-Host '[AutoPull] Fast-forwarding branch...'
    Invoke-Git -Arguments @(
        'pull',
        '--ff-only',
        'origin',
        $Branch
    )

    # Put the machine-local launcher files back after Git is synchronized.
    Restore-ProtectedCopies -BackupMap $backupMap
    $protectedCopiesRestored = $true

    Write-Host ''
    Write-Host '[AutoPull] Current HEAD:' -ForegroundColor Cyan
    Invoke-Git -Arguments @('log', '-8', '--oneline')

    $headOutput = @(& git rev-parse --short=12 HEAD)
    if ($LASTEXITCODE -ne 0 -or $headOutput.Count -eq 0) {
        throw 'Could not resolve the current Git HEAD.'
    }
    $head = $headOutput[0]

    Write-Host "[AutoPull] Building commit: $head" -ForegroundColor Cyan

    # ---------------------------------------------------------------------
    # 4. Build the exact JAR used by StartServer.ps1.
    #
    # build.gradle now emits <repo>\grasscutter.jar directly.
    # Delete the old JAR first so a failed build can never leave a stale
    # executable behind that looks like a successful rebuild.
    # ---------------------------------------------------------------------
    $jarPath = Join-Path $RepoRoot 'grasscutter.jar'

    if (Test-Path -LiteralPath $jarPath) {
        Write-Host '[AutoPull] Removing previous grasscutter.jar...'
        Remove-Item -LiteralPath $jarPath -Force
    }

    Write-Host ''
    Write-Host '[AutoPull] Running clean JAR build...' -ForegroundColor Cyan

    & $gradleWrapper clean jar -PskipHandbook=1 --no-build-cache
    if ($LASTEXITCODE -ne 0) {
        throw "Gradle build failed with exit code $LASTEXITCODE. The old grasscutter.jar was already removed, so it cannot be launched accidentally."
    }

    if (-not (Test-Path -LiteralPath $jarPath)) {
        throw "Gradle reported success but did not create the launcher JAR: $jarPath"
    }

    # ---------------------------------------------------------------------
    # 5. Verify that the runtime JAR really contains the current login patch.
    #
    # ddba588 added this literal to QuestManager. This check prevents another
    # situation where AutoPull says 'built' while StartServer launches stale
    # bytecode.
    # ---------------------------------------------------------------------
    $javap = Join-Path $JavaHome 'bin\javap.exe'
    if (-not (Test-Path -LiteralPath $javap)) {
        throw "javap.exe was not found: $javap"
    }

    Write-Host '[AutoPull] Verifying compiled login fix in grasscutter.jar...'

    $javapOutput = @(
        & $javap -classpath $jarPath -verbose emu.grasscutter.game.quest.QuestManager 2>&1
    )
    if ($LASTEXITCODE -ne 0) {
        throw 'javap failed while validating grasscutter.jar.'
    }

    $requiredMarker = 'QuestManager login rewind skipped'
    $compiledText = $javapOutput -join "`n"

    if ($compiledText -notmatch [regex]::Escape($requiredMarker)) {
        throw @"
Built JAR validation failed.

The compiled QuestManager does not contain:
  $requiredMarker

AutoPull refuses to accept this JAR because it is stale, incomplete, or from the wrong source tree.
"@
    }

    $jar = Get-Item -LiteralPath $jarPath
    $sha256 = (Get-FileHash -LiteralPath $jarPath -Algorithm SHA256).Hash

    Write-Host ''
    Write-Host '=== AutoPull completed successfully ===' -ForegroundColor Green
    Write-Host "[AutoPull] Git HEAD:      $head"
    Write-Host "[AutoPull] JAR:           $($jar.FullName)"
    Write-Host "[AutoPull] JAR timestamp: $($jar.LastWriteTime)"
    Write-Host "[AutoPull] JAR size:      $($jar.Length) bytes"
    Write-Host "[AutoPull] JAR SHA-256:   $sha256"
    Write-Host '[AutoPull] Binary marker: OK' -ForegroundColor Green
    Write-Host ''
    Write-Host 'You can now run StartServer.ps1.' -ForegroundColor Green
}
catch {
    # Restore the local launcher files even if fetch/pull/build fails.
    if (-not $protectedCopiesRestored -and $backupMap.Count -gt 0) {
        try {
            Restore-ProtectedCopies -BackupMap $backupMap
            $protectedCopiesRestored = $true
        }
        catch {
            Write-Warning "Failed to restore one or more local files. Backup directory: $backupRoot"
        }
    }

    Write-Host ''
    Write-Host '=== AutoPull FAILED ===' -ForegroundColor Red
    Write-Host $_.Exception.Message -ForegroundColor Red
    Write-Host ''

    if (-not $protectedCopiesRestored -and $backupMap.Count -gt 0) {
        Write-Host 'Your preserved local-file backup is still available at:' -ForegroundColor Yellow
        Write-Host "  $backupRoot" -ForegroundColor Yellow
    }

    exit 1
}
finally {
    if ($protectedCopiesRestored -or $backupMap.Count -eq 0) {
        Remove-Item -LiteralPath $backupRoot -Recurse -Force -ErrorAction SilentlyContinue
    }
}
