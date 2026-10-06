# WebSocket++ Build Script
$ErrorActionPreference = "Stop"

Write-Host "[+] Compiling WebSocket++..." -ForegroundColor Cyan

$burpJar = "C:\Users\stockland\Desktop\Burp.Suite.Professional.2025.3.2\Burp.Suite.Professional.2025.3.2\burpsuite_pro_v2025.3.2.jar"
if (-not (Test-Path $burpJar)) {
    Write-Error "Burp Suite JAR not found at: $burpJar"
    exit 1
}

$jdkPath = "C:\Program Files\Java\jdk-22\bin"
$javac = Join-Path $jdkPath "javac.exe"
$jar = Join-Path $jdkPath "jar.exe"

if (-not (Test-Path $javac)) {
    $javac = "javac"
    $jar = "jar"
}

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
Copy-Item "websocket-plus-plus-1.0.0.jar" -Destination "WebSocketLogger-1.0.0.jar" -Force

Write-Host "[OK] Successfully built: websocket-plus-plus-1.0.0.jar" -ForegroundColor Green
