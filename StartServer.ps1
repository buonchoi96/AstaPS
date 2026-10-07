[CmdletBinding()]
param(
    [switch]$PrepareMongoOnly,
    [switch]$ResetDatabase
)

$ErrorActionPreference = 'Stop'

$RepoRoot = $PSScriptRoot
$GrasscutterJar = Join-Path $RepoRoot 'grasscutter.jar'
$MongoRoot = Join-Path $RepoRoot 'local\mongodb'
$MongoServerRoot = Join-Path $MongoRoot 'server'
$MongoCacheRoot = Join-Path $MongoRoot 'cache'
$MongoDataRoot = Join-Path $MongoRoot 'data'
$MongoLogRoot = Join-Path $MongoRoot 'logs'
$MongoLogPath = Join-Path $MongoLogRoot 'mongod.log'

$MongoVersion = '9.0.2'
$MongoArchiveName = "mongodb-windows-x86_64-$MongoVersion.zip"
$MongoArchiveUrl = "https://fastdl.mongodb.org/windows/$MongoArchiveName"
$MongoArchiveSha256 = '2cabba2e80b90bcd1c1096845711975199104148c849a007984cb3ee4d237406'
$MongoPort = 27017

$DispatchPort = 8088
$GameRoot = 'D:\Genshin Impact game'
$GameExecutable = Join-Path $GameRoot 'GenshinImpact.exe'
$PatchLogRoot = Join-Path $RepoRoot 'debug'
$PatchLogPath = Join-Path $PatchLogRoot 'animegamepatch.log'

# animegamepatch is enabled ONLY for the Genshin instance launched by this script.
# Outside this script, the official launcher uses the original Astrolabe.dll.
$PatchDllSource = 'F:\Game Servers\_refs\animegamepatch\target\release\ext.dll'

function Test-TcpPort {
    param(
        [Parameter(Mandatory = $true)]
        [int]$Port
    )

    $client = New-Object System.Net.Sockets.TcpClient
    try {
        $asyncResult = $client.BeginConnect('127.0.0.1', $Port, $null, $null)
        if (-not $asyncResult.AsyncWaitHandle.WaitOne(300)) {
            return $false
        }

        $client.EndConnect($asyncResult)
        return $true
    } catch {
        return $false
    } finally {
        $client.Close()
    }
}

function Get-CompatibleJava {
    $commands = @(
        Get-Command java -All -ErrorAction SilentlyContinue |
            Where-Object { $_.Version.Major -ge 17 } |
            Sort-Object Version -Descending
    )

    if ($commands.Count -gt 0) {
        return $commands[0].Source
    }

    if ($env:JAVA_HOME) {
        $javaHomeExecutable = Join-Path $env:JAVA_HOME 'bin\java.exe'
        if (Test-Path $javaHomeExecutable) {
            return $javaHomeExecutable
        }
    }

    throw 'Grasscutter requires Java 17 or newer, but no compatible Java runtime was found.'
}

function Get-LocalMongod {
    if (-not (Test-Path $MongoServerRoot)) {
        return $null
    }

    $mongod = Get-ChildItem -Path $MongoServerRoot -Filter 'mongod.exe' -File -Recurse -ErrorAction SilentlyContinue |
        Select-Object -First 1
    if ($null -eq $mongod) {
        return $null
    }

    return $mongod.FullName
}

function Install-LocalMongo {
    $existing = Get-LocalMongod
    if ($null -ne $existing) {
        return $existing
    }

    New-Item -ItemType Directory -Path $MongoCacheRoot -Force | Out-Null
    $archivePath = Join-Path $MongoCacheRoot $MongoArchiveName

    if (Test-Path $archivePath) {
        $actualHash = (Get-FileHash -Path $archivePath -Algorithm SHA256).Hash.ToLowerInvariant()
        if ($actualHash -ne $MongoArchiveSha256) {
            Write-Host '[MongoDB] Cached archive hash mismatch; downloading a clean copy.'
            Remove-Item -Path $archivePath -Force
        }
    }

    if (-not (Test-Path $archivePath)) {
        Write-Host "[MongoDB] Downloading MongoDB Community Server $MongoVersion..."
        Invoke-WebRequest -Uri $MongoArchiveUrl -OutFile $archivePath -UseBasicParsing
    }

    $actualHash = (Get-FileHash -Path $archivePath -Algorithm SHA256).Hash.ToLowerInvariant()
    if ($actualHash -ne $MongoArchiveSha256) {
        throw "MongoDB archive SHA-256 mismatch. Expected $MongoArchiveSha256, got $actualHash."
    }

    $extractRoot = Join-Path $MongoRoot 'extract'
    if (Test-Path $extractRoot) {
        Remove-Item -Path $extractRoot -Recurse -Force
    }

    New-Item -ItemType Directory -Path $extractRoot -Force | Out-Null
    Write-Host '[MongoDB] Extracting local MongoDB runtime...'
    Expand-Archive -Path $archivePath -DestinationPath $extractRoot -Force

    if (Test-Path $MongoServerRoot) {
        Remove-Item -Path $MongoServerRoot -Recurse -Force
    }
    Move-Item -Path $extractRoot -Destination $MongoServerRoot

    $mongod = Get-LocalMongod
    if ($null -eq $mongod) {
        throw 'MongoDB extraction completed, but mongod.exe was not found.'
    }

    Remove-Item -Path $archivePath -Force -ErrorAction SilentlyContinue
    return $mongod
}

function Reset-LocalMongoData {
    if (Test-TcpPort -Port $MongoPort) {
        throw "Port $MongoPort is already in use. Stop the existing MongoDB process before resetting the local database."
    }

    if (Test-Path $MongoDataRoot) {
        Remove-Item -Path $MongoDataRoot -Recurse -Force
    }
    if (Test-Path $MongoLogRoot) {
        Remove-Item -Path $MongoLogRoot -Recurse -Force
    }

    New-Item -ItemType Directory -Path $MongoDataRoot -Force | Out-Null
    New-Item -ItemType Directory -Path $MongoLogRoot -Force | Out-Null
    Write-Host "[MongoDB] Database reset: $MongoDataRoot"
}

function Start-LocalMongo {
    param(
        [Parameter(Mandatory = $true)]
        [string]$MongodPath
    )

    if (Test-TcpPort -Port $MongoPort) {
        throw "Port $MongoPort is already in use. Refusing to attach Grasscutter to a MongoDB instance outside this repo."
    }

    New-Item -ItemType Directory -Path $MongoDataRoot -Force | Out-Null
    New-Item -ItemType Directory -Path $MongoLogRoot -Force | Out-Null

    $arguments = @(
        '--dbpath', ('"{0}"' -f $MongoDataRoot),
        '--bind_ip', '127.0.0.1',
        '--port', $MongoPort,
        '--logpath', ('"{0}"' -f $MongoLogPath),
        '--logappend'
    )

    Write-Host "[MongoDB] Starting with dbPath: $MongoDataRoot"
    $process = Start-Process -FilePath $MongodPath -ArgumentList $arguments -PassThru -WindowStyle Hidden

    for ($attempt = 0; $attempt -lt 80; $attempt++) {
        if ($process.HasExited) {
            $tail = ''
            if (Test-Path $MongoLogPath) {
                $tail = (Get-Content -Path $MongoLogPath -Tail 30) -join [Environment]::NewLine
            }
            throw "MongoDB exited before becoming ready. Exit code: $($process.ExitCode).$([Environment]::NewLine)$tail"
        }

        if (Test-TcpPort -Port $MongoPort) {
            Write-Host "[MongoDB] Ready on 127.0.0.1:$MongoPort (PID $($process.Id))."
            return $process
        }

        Start-Sleep -Milliseconds 250
        $process.Refresh()
    }

    Stop-Process -Id $process.Id -Force -ErrorAction SilentlyContinue
    throw 'MongoDB did not become ready within 20 seconds.'
}


function Initialize-ManagedProcessJob {
    if (-not ('AstaPS.ProcessJob' -as [type])) {
        Add-Type -TypeDefinition @'
using System;
using System.ComponentModel;
using System.Runtime.InteropServices;

namespace AstaPS
{
    public static class ProcessJob
    {
        private const uint JOB_OBJECT_LIMIT_KILL_ON_JOB_CLOSE = 0x00002000;

        private enum JOBOBJECTINFOCLASS
        {
            JobObjectExtendedLimitInformation = 9
        }

        [StructLayout(LayoutKind.Sequential)]
        private struct JOBOBJECT_BASIC_LIMIT_INFORMATION
        {
            public long PerProcessUserTimeLimit;
            public long PerJobUserTimeLimit;
            public uint LimitFlags;
            public UIntPtr MinimumWorkingSetSize;
            public UIntPtr MaximumWorkingSetSize;
            public uint ActiveProcessLimit;
            public UIntPtr Affinity;
            public uint PriorityClass;
            public uint SchedulingClass;
        }

        [StructLayout(LayoutKind.Sequential)]
        private struct IO_COUNTERS
        {
            public ulong ReadOperationCount;
            public ulong WriteOperationCount;
            public ulong OtherOperationCount;
            public ulong ReadTransferCount;
            public ulong WriteTransferCount;
            public ulong OtherTransferCount;
        }

        [StructLayout(LayoutKind.Sequential)]
        private struct JOBOBJECT_EXTENDED_LIMIT_INFORMATION
        {
            public JOBOBJECT_BASIC_LIMIT_INFORMATION BasicLimitInformation;
            public IO_COUNTERS IoInfo;
            public UIntPtr ProcessMemoryLimit;
            public UIntPtr JobMemoryLimit;
            public UIntPtr PeakProcessMemoryUsed;
            public UIntPtr PeakJobMemoryUsed;
        }

        [DllImport("kernel32.dll", CharSet = CharSet.Unicode, SetLastError = true)]
        private static extern IntPtr CreateJobObject(IntPtr lpJobAttributes, string lpName);

        [DllImport("kernel32.dll", SetLastError = true)]
        private static extern bool SetInformationJobObject(
            IntPtr hJob,
            JOBOBJECTINFOCLASS JobObjectInfoClass,
            IntPtr lpJobObjectInfo,
            uint cbJobObjectInfoLength);

        [DllImport("kernel32.dll", SetLastError = true)]
        private static extern bool AssignProcessToJobObject(IntPtr hJob, IntPtr hProcess);

        [DllImport("kernel32.dll", SetLastError = true)]
        private static extern bool CloseHandle(IntPtr hObject);

        public static IntPtr CreateKillOnCloseJob()
        {
            IntPtr job = CreateJobObject(IntPtr.Zero, null);
            if (job == IntPtr.Zero)
            {
                throw new Win32Exception(Marshal.GetLastWin32Error(), "CreateJobObject failed.");
            }

            var info = new JOBOBJECT_EXTENDED_LIMIT_INFORMATION();
            info.BasicLimitInformation.LimitFlags = JOB_OBJECT_LIMIT_KILL_ON_JOB_CLOSE;

            int length = Marshal.SizeOf(typeof(JOBOBJECT_EXTENDED_LIMIT_INFORMATION));
            IntPtr buffer = Marshal.AllocHGlobal(length);

            try
            {
                Marshal.StructureToPtr(info, buffer, false);
                if (!SetInformationJobObject(
                    job,
                    JOBOBJECTINFOCLASS.JobObjectExtendedLimitInformation,
                    buffer,
                    (uint)length))
                {
                    int error = Marshal.GetLastWin32Error();
                    CloseHandle(job);
                    throw new Win32Exception(error, "SetInformationJobObject failed.");
                }
            }
            finally
            {
                Marshal.FreeHGlobal(buffer);
            }

            return job;
        }

        public static void AddProcess(IntPtr job, IntPtr process)
        {
            if (!AssignProcessToJobObject(job, process))
            {
                throw new Win32Exception(
                    Marshal.GetLastWin32Error(),
                    "AssignProcessToJobObject failed.");
            }
        }

        public static void CloseJob(IntPtr job)
        {
            if (job != IntPtr.Zero)
            {
                CloseHandle(job);
            }
        }
    }
}
'@
    }

    return [AstaPS.ProcessJob]::CreateKillOnCloseJob()
}

function Add-ManagedProcess {
    param(
        [Parameter(Mandatory = $true)]
        [IntPtr]$JobHandle,

        [Parameter(Mandatory = $true)]
        [System.Diagnostics.Process]$Process,

        [Parameter(Mandatory = $true)]
        [string]$Name
    )

    $Process.Refresh()
    if ($Process.HasExited) {
        throw "$Name exited before it could be attached to the managed process job."
    }

    [AstaPS.ProcessJob]::AddProcess($JobHandle, $Process.Handle)
    Write-Host "[$Name] Managed PID $($Process.Id)."
}

function Wait-ForDispatch {
    param(
        [Parameter(Mandatory = $true)]
        [System.Diagnostics.Process]$ServerProcess
    )

    Write-Host "[Grasscutter] Waiting for dispatch on 127.0.0.1:$DispatchPort before launching Genshin..."

    while (-not (Test-TcpPort -Port $DispatchPort)) {
        $ServerProcess.Refresh()
        if ($ServerProcess.HasExited) {
            throw "Grasscutter exited before dispatch port $DispatchPort became ready. Exit code: $($ServerProcess.ExitCode)."
        }

        Start-Sleep -Milliseconds 500
    }

    Write-Host "[Grasscutter] Dispatch ready on 127.0.0.1:$DispatchPort."
}

function Stop-ProcessTree {
    param(
        [Parameter(Mandatory = $true)]
        [int]$ProcessId,

        [string]$Name = 'Process'
    )

    $process = Get-Process -Id $ProcessId -ErrorAction SilentlyContinue
    if ($null -eq $process) {
        return
    }

    Write-Host "[$Name] Stopping PID $ProcessId and its child processes..."
    & "$env:SystemRoot\System32\taskkill.exe" /PID $ProcessId /T /F 2>$null | Out-Null
}

function Get-AnimeGamePatchTarget {
    $rootTarget = Join-Path $GameRoot 'Astrolabe.dll'
    $pluginTarget = Join-Path $GameRoot 'GenshinImpact_Data\Plugins\Astrolabe.dll'

    foreach ($candidate in @($rootTarget, $pluginTarget)) {
        $original = Join-Path (Split-Path -Parent $candidate) 'Astrolabe_orig.dll'
        if (Test-Path $original) {
            return $candidate
        }
    }

    if (Test-Path $rootTarget) {
        return $rootTarget
    }
    if (Test-Path $pluginTarget) {
        return $pluginTarget
    }

    throw @"
Could not find Astrolabe.dll.

Checked:
  $rootTarget
  $pluginTarget
"@
}

function Restore-StaleAnimeGamePatch {
    param(
        [Parameter(Mandatory = $true)]
        [string]$TargetDll
    )

    $targetDirectory = Split-Path -Parent $TargetDll
    $originalDll = Join-Path $targetDirectory 'Astrolabe_orig.dll'

    if (-not (Test-Path $originalDll)) {
        return
    }

    Write-Host '[Genshin] Previous temporary patch state detected; restoring original DLL first...'

    if (Get-Process -Name 'GenshinImpact' -ErrorAction SilentlyContinue) {
        throw 'GenshinImpact.exe is still running while Astrolabe_orig.dll exists. Close the game before recovery.'
    }

    if (Test-Path $TargetDll) {
        Remove-Item -Path $TargetDll -Force
    }

    Move-Item -Path $originalDll -Destination $TargetDll -Force
    Write-Host '[Genshin] Clean original Astrolabe.dll restored.'
}

function Enable-TemporaryAnimeGamePatch {
    if (Get-Process -Name 'GenshinImpact' -ErrorAction SilentlyContinue) {
        throw 'GenshinImpact.exe is already running. Close it before starting the private-server launcher.'
    }

    if (-not (Test-Path $PatchDllSource)) {
        throw "animegamepatch ext.dll was not found at: $PatchDllSource"
    }

    $targetDll = Get-AnimeGamePatchTarget

    # Always recover a stale interrupted swap before creating a new one.
    Restore-StaleAnimeGamePatch -TargetDll $targetDll

    if (-not (Test-Path $targetDll)) {
        throw "Original Astrolabe.dll was not found at: $targetDll"
    }

    $targetDirectory = Split-Path -Parent $targetDll
    $originalDll = Join-Path $targetDirectory 'Astrolabe_orig.dll'

    if (Test-Path $originalDll) {
        throw "Refusing to overwrite existing backup: $originalDll"
    }

    Write-Host '[Genshin] Enabling animegamepatch for THIS launch only...'
    Write-Host "[Genshin] Source: $PatchDllSource"
    Write-Host "[Genshin] Target: $targetDll"

    # Original exists as Astrolabe_orig.dll only while this private-server launch is active.
    Move-Item -Path $targetDll -Destination $originalDll -Force

    try {
        Copy-Item -Path $PatchDllSource -Destination $targetDll -Force

        $sourceHash = (Get-FileHash -Path $PatchDllSource -Algorithm SHA256).Hash
        $targetHash = (Get-FileHash -Path $targetDll -Algorithm SHA256).Hash
        if ($sourceHash -ne $targetHash) {
            throw 'Patch verification failed: Astrolabe.dll does not match ext.dll.'
        }
    } catch {
        if (Test-Path $targetDll) {
            Remove-Item -Path $targetDll -Force -ErrorAction SilentlyContinue
        }
        if (Test-Path $originalDll) {
            Move-Item -Path $originalDll -Destination $targetDll -Force
        }
        throw
    }

    Write-Host '[Genshin] Temporary proxy installed.'
    return [PSCustomObject]@{
        TargetDll   = $targetDll
        OriginalDll = $originalDll
        PatchDll    = $PatchDllSource
    }
}

function Disable-TemporaryAnimeGamePatch {
    param(
        [Parameter(Mandatory = $true)]
        [string]$TargetDll,

        [Parameter(Mandatory = $true)]
        [string]$OriginalDll,

        [int]$RetryCount = 150
    )

    if (-not (Test-Path $OriginalDll)) {
        return
    }

    for ($attempt = 0; $attempt -lt $RetryCount; $attempt++) {
        try {
            if (Get-Process -Name 'GenshinImpact' -ErrorAction SilentlyContinue) {
                Start-Sleep -Milliseconds 200
                continue
            }

            if (Test-Path $TargetDll) {
                Remove-Item -Path $TargetDll -Force -ErrorAction Stop
            }

            Move-Item -Path $OriginalDll -Destination $TargetDll -Force -ErrorAction Stop
            Write-Host '[Genshin] Original Astrolabe.dll restored. Official launcher is unpatched.' -ForegroundColor Green
            return
        } catch {
            if ($attempt -ge ($RetryCount - 1)) {
                throw
            }
            Start-Sleep -Milliseconds 200
        }
    }

    throw "Timed out while restoring original Astrolabe.dll: $TargetDll"
}

function Start-GenshinWatchdog {
    param(
        [Parameter(Mandatory = $true)]
        [int]$ControllerProcessId,

        [Parameter(Mandatory = $true)]
        [int]$GameProcessId,

        [Parameter(Mandatory = $true)]
        [string]$PatchTargetDll,

        [Parameter(Mandatory = $true)]
        [string]$PatchOriginalDll
    )

    # This watchdog stays outside the Job Object.
    # If PowerShell is hard-killed, it kills the Genshin tree and restores the DLL.
    # If Genshin exits normally, it restores the DLL while AstaPS may remain running.
    $watchdogScript = @"
`$controllerPid = $ControllerProcessId
`$gamePid = $GameProcessId
`$targetDll = '$($PatchTargetDll.Replace("'", "''"))'
`$originalDll = '$($PatchOriginalDll.Replace("'", "''"))'
`$taskkill = Join-Path `$env:SystemRoot 'System32\taskkill.exe'

while (`$true) {
    `$controllerAlive = `$null -ne (Get-Process -Id `$controllerPid -ErrorAction SilentlyContinue)
    `$gameAlive = `$null -ne (Get-Process -Id `$gamePid -ErrorAction SilentlyContinue)

    if (-not `$controllerAlive) {
        if (`$gameAlive) {
            & `$taskkill /PID `$gamePid /T /F 2>`$null | Out-Null
        }
        break
    }

    if (-not `$gameAlive) {
        break
    }

    Start-Sleep -Milliseconds 200
}

for (`$attempt = 0; `$attempt -lt 150; `$attempt++) {
    if (Get-Process -Name 'GenshinImpact' -ErrorAction SilentlyContinue) {
        Start-Sleep -Milliseconds 200
        continue
    }

    if (-not (Test-Path `$originalDll)) {
        break
    }

    try {
        if (Test-Path `$targetDll) {
            Remove-Item -Path `$targetDll -Force -ErrorAction Stop
        }
        Move-Item -Path `$originalDll -Destination `$targetDll -Force -ErrorAction Stop
        break
    } catch {
        Start-Sleep -Milliseconds 200
    }
}
"@

    $encoded = [Convert]::ToBase64String(
        [Text.Encoding]::Unicode.GetBytes($watchdogScript)
    )

    $hostProcess = Get-Process -Id $PID
    $hostExecutable = $hostProcess.Path
    if (-not $hostExecutable -or -not (Test-Path $hostExecutable)) {
        throw 'Could not resolve the current PowerShell executable for the Genshin watchdog.'
    }

    $arguments = @(
        '-NoLogo',
        '-NoProfile',
        '-NonInteractive',
        '-WindowStyle', 'Hidden',
        '-EncodedCommand', $encoded
    )

    $watchdog = Start-Process `
        -FilePath $hostExecutable `
        -ArgumentList $arguments `
        -WindowStyle Hidden `
        -PassThru

    Write-Host "[Genshin] Watchdog PID $($watchdog.Id) monitors controller PID $ControllerProcessId and game PID $GameProcessId."
    return $watchdog
}

function Start-LocalGenshin {
    New-Item -ItemType Directory -Path $PatchLogRoot -Force | Out-Null

    $env:LUNAGC_PATCH_LOG = $PatchLogPath
    Write-Host "[Genshin] animegamepatch log: $PatchLogPath"

    $patchState = Enable-TemporaryAnimeGamePatch

    Write-Host "[Genshin] Launching PATCHED instance: $GameExecutable"
    try {
        $process = Start-Process `
            -FilePath $GameExecutable `
            -WorkingDirectory $GameRoot `
            -PassThru
    } catch {
        Disable-TemporaryAnimeGamePatch `
            -TargetDll $patchState.TargetDll `
            -OriginalDll $patchState.OriginalDll
        throw
    }

    return [PSCustomObject]@{
        Process    = $process
        PatchState = $patchState
    }
}

if (-not (Test-Path $GrasscutterJar) -and -not $PrepareMongoOnly) {
    throw "grasscutter.jar was not found at: $GrasscutterJar"
}

if (-not $PrepareMongoOnly -and -not (Test-Path $GameExecutable)) {
    throw "GenshinImpact.exe was not found at: $GameExecutable"
}

$mongodPath = Install-LocalMongo

if ($ResetDatabase) {
    Reset-LocalMongoData
}

if ($PrepareMongoOnly) {
    Write-Host "[MongoDB] Local runtime ready: $mongodPath"
    Write-Host "[MongoDB] Local dbPath: $MongoDataRoot"
    exit 0
}

$mongoProcess = $null
$serverProcess = $null
$gameProcess = $null
$gameWatchdogProcess = $null
$gamePatchState = $null
$managedJob = [IntPtr]::Zero
$serverExitCode = 1
$javaExecutable = Get-CompatibleJava

$hadPatchLogEnv = Test-Path Env:LUNAGC_PATCH_LOG
$previousPatchLogEnv = $env:LUNAGC_PATCH_LOG

try {
    # JOB_OBJECT_LIMIT_KILL_ON_JOB_CLOSE makes Windows terminate MongoDB and
    # Grasscutter even if this PowerShell process is killed hard and finally cannot run.
    # Genshin is handled by a separate watchdog because the client can reject job assignment.
    $managedJob = Initialize-ManagedProcessJob

    $mongoProcess = Start-LocalMongo -MongodPath $mongodPath
    Add-ManagedProcess -JobHandle $managedJob -Process $mongoProcess -Name 'MongoDB'

    Write-Host "[Grasscutter] Using Java: $javaExecutable"
    Write-Host '[Grasscutter] Starting grasscutter.jar -debug...'
    $serverProcess = Start-Process `
        -FilePath $javaExecutable `
        -ArgumentList @('-jar', 'grasscutter.jar', '-debug') `
        -WorkingDirectory $RepoRoot `
        -NoNewWindow `
        -PassThru
    Add-ManagedProcess -JobHandle $managedJob -Process $serverProcess -Name 'Grasscutter'

    Wait-ForDispatch -ServerProcess $serverProcess

    $gameLaunch = Start-LocalGenshin
    $gameProcess = $gameLaunch.Process
    $gamePatchState = $gameLaunch.PatchState

    # Genshin 7.x can reject AssignProcessToJobObject. Manage it separately with
    # a watchdog keyed to this PowerShell PID. The watchdog also restores the
    # original Astrolabe.dll as soon as this game instance exits.
    $gameWatchdogProcess = Start-GenshinWatchdog `
        -ControllerProcessId $PID `
        -GameProcessId $gameProcess.Id `
        -PatchTargetDll $gamePatchState.TargetDll `
        -PatchOriginalDll $gamePatchState.OriginalDll

    Write-Host ''
    Write-Host '[AstaPS] MongoDB + Grasscutter are protected by a Windows Job Object.'
    Write-Host '[AstaPS] This Genshin instance is temporarily patched; normal launcher is restored after shutdown.'
    Write-Host '[AstaPS] Press Ctrl+C to stop MongoDB + Grasscutter + Genshin and restore Astrolabe.dll.'
    Write-Host '[AstaPS] Waiting for Grasscutter to exit...'
    Write-Host ''

    Wait-Process -Id $serverProcess.Id
    $serverProcess.Refresh()
    $serverExitCode = $serverProcess.ExitCode
} finally {
    Write-Host ''
    Write-Host '[AstaPS] Shutdown requested: stopping MongoDB, Grasscutter and Genshin, then restoring Astrolabe.dll...'

    if ($managedJob -ne [IntPtr]::Zero) {
        # Closing the job handle triggers KILL_ON_JOB_CLOSE for every attached process.
        [AstaPS.ProcessJob]::CloseJob($managedJob)
        $managedJob = [IntPtr]::Zero
    }

    # Genshin is deliberately outside the Job Object because recent clients may
    # reject AssignProcessToJobObject. Kill its entire process tree explicitly.
    if ($null -ne $gameProcess) {
        try {
            Stop-ProcessTree -ProcessId $gameProcess.Id -Name 'Genshin'
        } catch {
            Write-Warning "[Genshin] Cleanup warning: $($_.Exception.Message)"
        }
    }

    # Restore the normal client layout during graceful shutdown. The watchdog
    # performs the same restoration if this PowerShell process is killed hard.
    if ($null -ne $gamePatchState) {
        try {
            Disable-TemporaryAnimeGamePatch `
                -TargetDll $gamePatchState.TargetDll `
                -OriginalDll $gamePatchState.OriginalDll
        } catch {
            Write-Warning "[Genshin] Could not restore original Astrolabe.dll yet: $($_.Exception.Message)"
        }
    }

    # The watchdog is only needed to survive a hard controller termination.
    # During graceful cleanup the game is already gone, so stop the watchdog too.
    if ($null -ne $gameWatchdogProcess) {
        try {
            $gameWatchdogProcess.Refresh()
            if (-not $gameWatchdogProcess.HasExited) {
                Stop-Process -Id $gameWatchdogProcess.Id -Force -ErrorAction SilentlyContinue
            }
        } catch {
            # It may have already exited after observing the controller/process state.
        }
    }

    # Fallback cleanup for MongoDB/Grasscutter in case a process could not be
    # terminated through the Job Object.
    foreach ($process in @($serverProcess, $mongoProcess)) {
        if ($null -ne $process) {
            try {
                $process.Refresh()
                if (-not $process.HasExited) {
                    Stop-Process -Id $process.Id -Force -ErrorAction SilentlyContinue
                }
            } catch {
                # The process may already have disappeared after the job handle closed.
            }
        }
    }

    if ($hadPatchLogEnv) {
        $env:LUNAGC_PATCH_LOG = $previousPatchLogEnv
    } else {
        Remove-Item Env:LUNAGC_PATCH_LOG -ErrorAction SilentlyContinue
    }
}

exit $serverExitCode
