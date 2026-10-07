# WebSocket++ Build Script
$ErrorActionPreference = "Stop"

Write-Host "[+] Compiling WebSocket++..." -ForegroundColor Cyan

# Locate Burp Suite JAR dynamically without hardcoding personal paths
$candidateBurpJars = @(
    $env:BURP_JAR,
    "C:\Program Files\BurpSuitePro\burpsuite_pro.jar",
    (Join-Path $env:USERPROFILE "BurpSuitePro\burpsuite_pro.jar"),
    (Join-Path $PSScriptRoot "burpsuite_pro.jar")
) | Where-Object { $_ -and (Test-Path $_) }

if (-not $candidateBurpJars) {
    $foundJars = Get-ChildItem -Path (Join-Path $env:USERPROFILE "Desktop") -Recurse -Filter "burpsuite_pro*.jar" -ErrorAction SilentlyContinue | Select-Object -First 1 -ExpandProperty FullName
    if ($foundJars) {
        $candidateBurpJars = @($foundJars)
    }
}

$burpJar = $candidateBurpJars | Select-Object -First 1

if (-not $burpJar) {
    Write-Error "Burp Suite JAR not found. Please set `$env:BURP_JAR or place burpsuite_pro.jar in the project directory."
    exit 1
}

$jdkPath = if ($env:JAVA_HOME -and (Test-Path (Join-Path $env:JAVA_HOME "bin\javac.exe"))) {
    Join-Path $env:JAVA_HOME "bin"
} elseif (Test-Path "C:\Program Files\Java\jdk-22\bin\javac.exe") {
    "C:\Program Files\Java\jdk-22\bin"
} else {
    ""
}

$javac = if ($jdkPath) { Join-Path $jdkPath "javac.exe" } else { "javac" }
$jar = if ($jdkPath) { Join-Path $jdkPath "jar.exe" } else { "jar" }

New-Item -ItemType Directory -Path "build\classes" -Force | Out-Null
New-Item -ItemType Directory -Path "build\META-INF" -Force | Out-Null

"Manifest-Version: 1.0`r`nExtension-Name: WebSocket++`r`nExtension-Version: 1.0.0`r`nCreated-By: Antigravity AI`r`n" | Set-Content -Path "build\META-INF\MANIFEST.MF" -Encoding ASCII

$sources = Get-ChildItem -Path "src\main\java" -Recurse -Filter "*.java" | ForEach-Object { $_.FullName }

& $javac -cp $burpJar -d "build\classes" $sources
if ($LASTEXITCODE -ne 0) {
    Write-Error "Compilation failed!"
    exit 1
}

# Clean any duplicate burp/api if present
Remove-Item -Path "build\classes\burp\api" -Recurse -Force -ErrorAction SilentlyContinue

& $jar cfm "websocket-plus-plus-1.0.0.jar" "build\META-INF\MANIFEST.MF" -C "build\classes" .
if ($LASTEXITCODE -ne 0) {
    Write-Error "Packaging JAR failed!"
    exit 1
}

Write-Host "[OK] Successfully built: websocket-plus-plus-1.0.0.jar" -ForegroundColor Green
