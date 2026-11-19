$ErrorActionPreference = 'Stop'
$repoRoot = Split-Path -Parent $PSScriptRoot
Set-Location -LiteralPath $repoRoot
$jar = Join-Path $repoRoot 'grasscutter-7.2.0.jar'
$resources = Join-Path $repoRoot 'resources'
$dbDir = Join-Path $repoRoot 'database'
$logDir = Join-Path $repoRoot 'logs'
$mongoLog = Join-Path $logDir 'mongodb.log'
$port = 27017

function Test-LocalMongoPort {
    $client = New-Object System.Net.Sockets.TcpClient
    try {
        $connection = $client.ConnectAsync('127.0.0.1', $port)
        return ($connection.Wait(300) -and $client.Connected)
    } catch { return $false }
    finally { $client.Dispose() }
}

function Find-Mongod {
    if ($env:ASTAPS_MONGOD) {
        if (Test-Path -LiteralPath $env:ASTAPS_MONGOD -PathType Leaf) {
            return (Resolve-Path -LiteralPath $env:ASTAPS_MONGOD).Path
        }
        throw "ASTAPS_MONGOD points to a missing file: $env:ASTAPS_MONGOD"
    }
    $command = Get-Command mongod.exe -ErrorAction SilentlyContinue
    if ($command) { return $command.Source }
    $roots = @(
        (Join-Path $repoRoot 'local\mongodb'),
        'E:\MongoDB',
        'C:\Program Files\MongoDB\Server'
    )
    foreach ($root in $roots) {
        if (Test-Path -LiteralPath $root -PathType Container) {
            $exe = Get-ChildItem -LiteralPath $root -Filter mongod.exe -File -Recurse -ErrorAction SilentlyContinue | Select-Object -First 1
            if ($exe) { return $exe.FullName }
        }
    }
    throw 'MongoDB Community mongod.exe not found. Install it and set ASTAPS_MONGOD to its full path.'
}

function Find-Mongosh {
    $command = Get-Command mongosh.exe -ErrorAction SilentlyContinue
    if ($command) { return $command.Source }
    foreach ($root in @('E:\MongoDB', (Join-Path $repoRoot 'local\mongodb'))) {
        if (Test-Path -LiteralPath $root -PathType Container) {
            $exe = Get-ChildItem -LiteralPath $root -Filter mongosh.exe -File -Recurse -ErrorAction SilentlyContinue | Select-Object -First 1
            if ($exe) { return $exe.FullName }
        }
    }
    return $null
}

if (!(Test-Path -LiteralPath $jar -PathType Leaf)) {
    throw "Missing $jar. Build with .\gradlew.bat jar -PskipHandbook=1"
}
if (!(Test-Path -LiteralPath (Join-Path $resources 'ExcelBinOutput') -PathType Container)) {
    throw "Missing resources/ExcelBinOutput. Link resources/ to ../resources before launching."
}
if (Test-LocalMongoPort) {
    throw "Port 127.0.0.1:$port is already occupied. Stop that listener first to avoid using an unrelated database."
}

$mongod = Find-Mongod
New-Item -ItemType Directory -Path $dbDir, $logDir -Force | Out-Null
$mongoArgs = @(
    '--dbpath', ('"{0}"' -f $dbDir),
    '--bind_ip', '127.0.0.1',
    '--port', "$port",
    '--logpath', ('"{0}"' -f $mongoLog),
    '--logappend'
)
Write-Host "Starting MongoDB: $mongod"
Write-Host "Database files: $dbDir"
$mongo = Start-Process -FilePath $mongod -ArgumentList $mongoArgs -WindowStyle Hidden -PassThru
try {
    $ready = $false
    for ($i = 0; $i -lt 40; $i++) {
        if ($mongo.HasExited) { throw "MongoDB exited early. Check $mongoLog" }
        if (Test-LocalMongoPort) { $ready = $true; break }
        Start-Sleep -Milliseconds 500
    }
    if (!$ready) { throw "MongoDB did not become ready. Check $mongoLog" }
    Write-Host "MongoDB ready on 127.0.0.1:$port"
    Write-Host "Starting AstaPS: $jar"
    & java -jar $jar
    $serverExitCode = $LASTEXITCODE
} finally {
    if ($mongo -and !$mongo.HasExited) {
        Write-Host "Gracefully shutting down MongoDB process $($mongo.Id)..."
        $mongosh = Find-Mongosh
        if ($mongosh) {
            try {
                & $mongosh "mongodb://127.0.0.1:$port/admin?directConnection=true" --quiet --eval 'db.shutdownServer()' | Out-Null
            } catch {
                Write-Warning "Graceful MongoDB shutdown command failed: $_"
            }
            for ($i = 0; $i -lt 20 -and !$mongo.HasExited; $i++) {
                Start-Sleep -Milliseconds 250
            }
        }
        if (!$mongo.HasExited) {
            Write-Warning "Graceful shutdown not available; terminating MongoDB. Review $mongoLog before restarting."
            Stop-Process -Id $mongo.Id -ErrorAction SilentlyContinue
            $mongo.WaitForExit(5000) | Out-Null
        }
    }
}
if ($serverExitCode -is [int]) { exit $serverExitCode }
exit 1
