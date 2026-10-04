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

if (-not (Test-Path $GrasscutterJar) -and -not $PrepareMongoOnly) {
    throw "grasscutter.jar was not found at: $GrasscutterJar"
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
$serverExitCode = 1
$javaExecutable = Get-CompatibleJava
Push-Location $RepoRoot
try {
    $mongoProcess = Start-LocalMongo -MongodPath $mongodPath
    Write-Host "[Grasscutter] Using Java: $javaExecutable"
    Write-Host '[Grasscutter] Starting grasscutter.jar...'
    & $javaExecutable -jar $GrasscutterJar
    $serverExitCode = $LASTEXITCODE
} finally {
    Pop-Location
    if ($null -ne $mongoProcess -and -not $mongoProcess.HasExited) {
        Write-Host "[MongoDB] Stopping PID $($mongoProcess.Id)..."
        Stop-Process -Id $mongoProcess.Id -ErrorAction SilentlyContinue
        try {
            Wait-Process -Id $mongoProcess.Id -Timeout 10 -ErrorAction Stop
        } catch {
            Stop-Process -Id $mongoProcess.Id -Force -ErrorAction SilentlyContinue
        }
    }
}

exit $serverExitCode
