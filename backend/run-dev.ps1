$repoRoot = Split-Path -Parent $PSScriptRoot
$bundledMaven = Join-Path $repoRoot ".tools\apache-maven-3.9.9\bin\mvn.cmd"
$maven = if (Test-Path $bundledMaven) { $bundledMaven } else {
    $command = Get-Command mvn -ErrorAction SilentlyContinue
    if ($command) { $command.Source } else { throw "Maven was not found. Install Maven or add mvn to PATH." }
}

if (-not (Get-Command java -ErrorAction SilentlyContinue)) {
    throw "Java 17 is required. Install a JDK and add java to PATH."
}

& $maven -f (Join-Path $PSScriptRoot "pom.xml") spring-boot:run "-Dspring-boot.run.profiles=dev"
exit $LASTEXITCODE
