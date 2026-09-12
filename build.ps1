# JAR Decompiler & Workbench - PowerShell Build Script
[CmdletBinding()]
param()

$ErrorActionPreference = "Stop"

Write-Host "============================================================" -ForegroundColor Cyan
Write-Host "  JAR Decompiler & Workbench - Build Script" -ForegroundColor Cyan
Write-Host "============================================================" -ForegroundColor Cyan

# Locate csc.exe
$csc = "C:\Windows\Microsoft.NET\Framework64\v4.0.30319\csc.exe"
if (-not (Test-Path $csc)) {
    $csc = "C:\Windows\Microsoft.NET\Framework\v4.0.30319\csc.exe"
}
if (-not (Test-Path $csc)) {
    Write-Error "Microsoft .NET C# compiler (csc.exe) not found."
}

# Locate javac.exe and jar.exe
$cmdJavac = Get-Command javac.exe -ErrorAction SilentlyContinue
$javac = if ($cmdJavac) { $cmdJavac.Source } else { $null }
$cmdJar = Get-Command jar.exe -ErrorAction SilentlyContinue
$jarExe = if ($cmdJar) { $cmdJar.Source } else { $null }

if (-not $javac -or -not $jarExe) {
    $knownJdks = @(
        "C:\Program Files\Java\jdk-24\bin",
        "C:\Program Files\Java\jdk-21\bin",
        "C:\Program Files\Java\jdk-17\bin"
    )
    foreach ($k in $knownJdks) {
        if ((Test-Path "$k\javac.exe") -and (Test-Path "$k\jar.exe")) {
            $javac = "$k\javac.exe"
            $jarExe = "$k\jar.exe"
            break
        }
    }
}

if (-not $javac -or -not (Test-Path $javac)) {
    Write-Error "JDK compiler (javac.exe) not found. Please install JDK 17+ or configure PATH."
}

Write-Host "[1/4] Preparing directories..." -ForegroundColor Yellow
if (Test-Path "build") { Remove-Item -Recurse -Force "build" }
New-Item -ItemType Directory -Force -Path "build\classes" | Out-Null
New-Item -ItemType Directory -Force -Path "dist" | Out-Null

Write-Host "[2/4] Compiling Java workbench..." -ForegroundColor Yellow
& $javac -encoding UTF-8 -d "build\classes" "src\JarDecompiler.java"
if ($LASTEXITCODE -ne 0) { throw "Java compilation failed." }

Write-Host "[3/4] Packaging JarDecompiler.jar (with embedded sources)..." -ForegroundColor Yellow
Copy-Item "launcher\Launcher.cs" -Destination "build\classes\"
Copy-Item "src\JarDecompiler.java" -Destination "build\classes\"
& $jarExe -cvfe "build\JarDecompiler.jar" JarDecompiler -C "build\classes" . | Out-Null
if ($LASTEXITCODE -ne 0) { throw "JAR packaging failed." }

Write-Host "[4/4] Building standalone executable (JarDecompiler.exe)..." -ForegroundColor Yellow
$cscArgs = @(
    "/target:winexe",
    "/optimize+",
    "/r:System.Windows.Forms.dll",
    "/r:System.Drawing.dll",
    "/win32icon:launcher\app.ico",
    "/resource:build\JarDecompiler.jar,JarDecompiler.jar",
    "/resource:engines\vineflower-1.12.0.jar,vineflower-1.12.0.jar",
    "/resource:engines\cfr-0.152.jar,cfr-0.152.jar",
    "/resource:launcher\Launcher.cs,Launcher.cs",
    "/resource:src\JarDecompiler.java,JarDecompiler.java",
    "/out:dist\JarDecompiler.exe",
    "launcher\Launcher.cs"
)
& $csc $cscArgs
if ($LASTEXITCODE -ne 0) { throw "C# build failed." }

Copy-Item "dist\JarDecompiler.exe" -Destination "JarDecompiler.exe" -Force

$size = (Get-Item "JarDecompiler.exe").Length / 1MB
Write-Host ""
Write-Host "============================================================" -ForegroundColor Green
Write-Host "  BUILD SUCCESSFUL!" -ForegroundColor Green
Write-Host ("  Output: JarDecompiler.exe ({0:N2} MB Portable Single-File)" -f $size) -ForegroundColor Green
Write-Host "============================================================" -ForegroundColor Green
