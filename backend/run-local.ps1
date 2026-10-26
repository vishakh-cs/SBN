$ErrorActionPreference = "Stop"

$portOwner = Get-NetTCPConnection -LocalPort 8080 -State Listen -ErrorAction SilentlyContinue | Select-Object -First 1
if ($portOwner) {
    $ownerProcess = Get-Process -Id $portOwner.OwningProcess -ErrorAction SilentlyContinue
    $ownerName = if ($ownerProcess) { $ownerProcess.ProcessName } else { "unknown process" }
    throw "Port 8080 is already in use by PID $($portOwner.OwningProcess) ($ownerName). If this is the running application, leave it open and start the frontend. Otherwise stop that process before running this script again."
}

$repoRoot = Split-Path -Parent $PSScriptRoot
$mysqlCandidates = @(
    (Get-Command mysql.exe -ErrorAction SilentlyContinue | Select-Object -ExpandProperty Source -First 1),
    "C:\Program Files\MySQL\MySQL Server 8.0\bin\mysql.exe",
    "C:\Program Files\MySQL\MySQL Server 9.0\bin\mysql.exe"
) | Where-Object { $_ -and (Test-Path $_) }

if (-not $mysqlCandidates) {
    throw "MySQL Server is running, but mysql.exe was not found. Add MySQL's bin folder to PATH or install the MySQL command-line client."
}

$mysql = $mysqlCandidates | Select-Object -First 1
$securePassword = Read-Host "Enter your local MySQL root password" -AsSecureString
$passwordPointer = [Runtime.InteropServices.Marshal]::SecureStringToBSTR($securePassword)
try {
    $mysqlPassword = [Runtime.InteropServices.Marshal]::PtrToStringBSTR($passwordPointer)
} finally {
    [Runtime.InteropServices.Marshal]::ZeroFreeBSTR($passwordPointer)
}

$previousMysqlPassword = $env:MYSQL_PWD
$previousDbPassword = $env:DB_PASSWORD
$previousDbUrl = $env:DB_URL
$previousDbUsername = $env:DB_USERNAME
$previousCookieSetting = $env:APP_SESSION_COOKIE_SECURE
try {
    $env:MYSQL_PWD = $mysqlPassword
    $appPassword = [Guid]::NewGuid().ToString("N")
    @"
CREATE DATABASE IF NOT EXISTS smart_donation_db CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
CREATE USER IF NOT EXISTS 'smart_donation'@'localhost' IDENTIFIED BY '$appPassword';
ALTER USER 'smart_donation'@'localhost' IDENTIFIED BY '$appPassword';
GRANT ALL PRIVILEGES ON smart_donation_db.* TO 'smart_donation'@'localhost';
"@ | & $mysql --host=localhost --port=3306 --user=root
    if ($LASTEXITCODE -ne 0) {
        throw "Could not create/access smart_donation_db. Check the MySQL root password and ensure MySQL is listening on port 3306."
    }

    $env:DB_URL = "jdbc:mysql://localhost:3306/smart_donation_db"
    $env:DB_USERNAME = "smart_donation"
    $env:DB_PASSWORD = $appPassword
    $env:APP_SESSION_COOKIE_SECURE = "false"

    $bundledMaven = Join-Path $repoRoot ".tools\apache-maven-3.9.9\bin\mvn.cmd"
    $maven = if (Test-Path $bundledMaven) { $bundledMaven } else {
        $command = Get-Command mvn -ErrorAction SilentlyContinue
        if ($command) { $command.Source } else { throw "Maven was not found. Install Maven or add mvn to PATH." }
    }
    if (-not (Get-Command java -ErrorAction SilentlyContinue)) {
        throw "Java 17 is required. Install a JDK and add java to PATH."
    }

    Write-Host "Starting the backend with the local administrator account (no demo dataset). Database: smart_donation_db"
    # Clean first so this local launcher cannot reuse stale controller bytecode.
    & $maven -f (Join-Path $PSScriptRoot "pom.xml") clean spring-boot:run "-Dspring-boot.run.profiles=local"
    exit $LASTEXITCODE
} finally {
    $env:MYSQL_PWD = $previousMysqlPassword
    $env:DB_PASSWORD = $previousDbPassword
    $env:DB_URL = $previousDbUrl
    $env:DB_USERNAME = $previousDbUsername
    $env:APP_SESSION_COOKIE_SECURE = $previousCookieSetting
}
