@echo off
setlocal

echo ============================================================
echo   JAR Decompiler and Workbench - Build Script
echo ============================================================

set "CSC_PATH=C:\Windows\Microsoft.NET\Framework64\v4.0.30319\csc.exe"
if not exist "%CSC_PATH%" (
    set "CSC_PATH=C:\Windows\Microsoft.NET\Framework\v4.0.30319\csc.exe"
)

if not exist "%CSC_PATH%" (
    echo [ERROR] C# Compiler csc.exe was not found in .NET Framework directory.
    exit /b 1
)

where jar >nul 2>nul
if %errorlevel% neq 0 (
    if exist "C:\Program Files\Java\jdk-24\bin\jar.exe" (
        set "PATH=C:\Program Files\Java\jdk-24\bin;%PATH%"
    ) else if exist "C:\Program Files\Java\jdk-21\bin\jar.exe" (
        set "PATH=C:\Program Files\Java\jdk-21\bin;%PATH%"
    ) else if exist "C:\Program Files\Java\jdk-17\bin\jar.exe" (
        set "PATH=C:\Program Files\Java\jdk-17\bin;%PATH%"
    )
)

where jar >nul 2>nul
if %errorlevel% neq 0 (
    echo [ERROR] jar.exe was not found. Please install JDK 17+ or set JAVA_HOME.
    exit /b 1
)

echo [1/4] Preparing directories...
if exist "build" rmdir /s /q "build"
if not exist "dist" mkdir "dist"
mkdir "build\classes"

echo [2/4] Compiling Java workbench (JarDecompiler.java)...
javac -encoding UTF-8 -d "build\classes" "src\JarDecompiler.java"
if %errorlevel% neq 0 (
    echo [ERROR] Java compilation failed.
    exit /b 1
)

echo [3/4] Packaging JarDecompiler.jar...
jar -cvfe "build\JarDecompiler.jar" JarDecompiler -C "build\classes" . >nul
if %errorlevel% neq 0 (
    echo [ERROR] JAR packaging failed.
    exit /b 1
)

echo [4/4] Building standalone executable (JarDecompiler.exe)...
"%CSC_PATH%" /target:winexe /optimize+ /r:System.Windows.Forms.dll /r:System.Drawing.dll /win32icon:"launcher\app.ico" /resource:"build\JarDecompiler.jar",JarDecompiler.jar /resource:"engines\vineflower-1.12.0.jar",vineflower-1.12.0.jar /resource:"engines\cfr-0.152.jar",cfr-0.152.jar /out:"dist\JarDecompiler.exe" "launcher\Launcher.cs"
if %errorlevel% neq 0 (
    echo [ERROR] C# executable build failed.
    exit /b 1
)

copy /y "dist\JarDecompiler.exe" "JarDecompiler.exe" >nul

echo.
echo ============================================================
echo   BUILD SUCCESSFUL!
echo   Output: JarDecompiler.exe (Standalone Single-File Executable)
echo ============================================================
echo.
exit /b 0
