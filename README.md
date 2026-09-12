# JAR Decompiler & Code Workbench

[![License: MIT](https://img.shields.io/badge/License-MIT-blue.svg)](LICENSE)
[![Java](https://img.shields.io/badge/Java-17%2B-orange.svg)](https://www.oracle.com/java/)
[![Platform](https://img.shields.io/badge/Platform-Windows%2010%20%7C%2011-0078D6.svg)](https://microsoft.com/windows)
[![Single-File](https://img.shields.io/badge/Executable-Single--File%20(3.7%20MB)-brightgreen.svg)](JarDecompiler.exe)

A modern, high-accuracy Java archive decompiler, code workbench, and in-app JAR editor for Windows 10 and 11. 

Designed to replace dated 1990s decompiler UIs with a contemporary Windows 11 design language, native common dialogs, live syntax-highlighted code editing, automatic `javac` recompilation, and 1-click **"Save As JAR..."** export.

---

## ✨ Features

- **🚀 100% Single-File Portable Executable (`JarDecompiler.exe`)**:
  - The standalone `.exe` is only **~3.7 MB** and embeds the application bytecode and both decompiler engines.
  - Zero setup: share just `JarDecompiler.exe` with anyone. It auto-unpacks runtime payloads to `%LOCALAPPDATA%\JarDecompiler` if needed.
- **🗔 Native Windows 11 Dialogs**:
  - Replaced legacy Win32 file choosers with true modern COM `IFileOpenDialog` and `SaveFileDialog` (Quick access, OneDrive, modern search, and breadcrumbs).
- **📂 Modern Package & File Tree Explorer**:
  - `Segoe UI` typography with 28px row height, smooth selection pills, and file badges (`☕ .java`, `⚙ config`, `📋 manifest`, `🖼 image`).
- **✏️ In-App Code Editor & Repackager**:
  - Direct live editing for `.java`, `.json`, `.xml`, `.properties`, `.txt`, and `MANIFEST.MF`.
  - Full `Ctrl+Z` / `Ctrl+Y` undo/redo history, dirty state tracking (`● Modified`), and `Ctrl+S` file save.
  - **Auto-Recompilation**: Automatically invokes JDK `javac` to recompile modified sources with error reporting.
  - **"Save As JAR..." Button**: Prominently featured on the toolbar to export updated JAR archives with all classes and resources intact.
- **✨ Two-Tier Anti-Obfuscation & Deobfuscator**:
  - **In-Editor Instant Deobfuscator (`Ctrl+Alt+D`)**: Dedicated "Deobfuscate" button in the editor header instantly decodes unicode/hex escape strings (`\u0048...`), folds constant boolean/numeric predicates (`1 == 1`, `true && true`, `1 == 2`), and renames synthetic identifiers (`var0`, `var1`, `a`, `b`) to typed names (`str0`, `num1`, `flag2`). Fully undoable with `Ctrl+Z`.
  - **Deep Anti-Obfuscation Re-Decompilation**: Toolbar dropdown "Deep Deobfuscate Project" or CLI `-d, --deobfuscate` activates aggressive deobfuscation flags in Vineflower (`--rename-members`, `--variable-renaming=jad`, `--synthetic-not-set`) and CFR (`--antiobf`, `--renamedupmembers`, `--renameillegalidents`, `--renamesmallmembers`).
- **🔍 Syntax Highlighting & In-Viewer Search**:
  - VS Code / GitHub Light syntax highlighting palette.
  - Synchronized line numbers gutter.
  - Built-in `Ctrl+F` search bar with live match counter and Next / Previous navigation.
- **⚙️ Dual Decompiler Engines**:
  - **Vineflower** (Recommended - IntelliJ IDEA engine with modern Java 21+ pattern matching support).
  - **CFR** (High-compatibility engine for edge-case obfuscation).
  - Optional `javap` disassembly mode.
- **🖱️ Drag & Drop Fluent Drop Zone**:
  - Drag `.jar`, `.war`, or `.zip` files directly into the window or onto `JarDecompiler.exe` in Windows File Explorer.

---

## 🚀 Quick Start

### Run the Standalone Executable
Double-click `JarDecompiler.exe` or run:
```powershell
.\JarDecompiler.exe
```

### Drag & Drop
Drag any `.jar` file directly onto `JarDecompiler.exe` in Windows File Explorer.

### Command Line Interface (CLI)
```powershell
# Decompile to default directory:
.\JarDecompiler.exe myapp.jar

# Decompile with deep anti-obfuscation & identifier renaming:
.\JarDecompiler.exe myapp.jar -d

# Specify custom output folder and CFR engine:
.\JarDecompiler.exe myapp.jar -o .\sources -e cfr

# Open output directory in Windows File Explorer when complete:
.\JarDecompiler.exe myapp.jar --open
```

---

## 🛠️ How to Build from Source

### Prerequisites
1. **JDK 17 or higher** (JDK 17, 21, or 24 recommended).
2. **Windows 10 / 11** (uses the built-in Microsoft .NET Framework `csc.exe` compiler).

### 1-Click Build (Command Prompt / PowerShell)
Run the build script:
```cmd
build.bat
```
Or via PowerShell:
```powershell
.\build.ps1
```

The script will:
1. Compile `src/JarDecompiler.java` into bytecode.
2. Package `build/JarDecompiler.jar`.
3. Compile `launcher/Launcher.cs` into `JarDecompiler.exe` with all JARs, source files, and icons embedded.

---

## 🔍 How to Inspect & Build the .EXE Alone

The native Windows executable source is completely open in [`launcher/Launcher.cs`](launcher/Launcher.cs).

### View the .EXE Source Code:
- **In the Repository**: View [`launcher/Launcher.cs`](launcher/Launcher.cs).
- **In the GUI Workbench**: Click the **"📜 App Source"** button on the top toolbar to view `Launcher.cs` directly with syntax highlighting!
- **From Command Line**: Run `.\JarDecompiler.exe --source` to export the full source code from any standalone executable copy.

### Build Only `JarDecompiler.exe`:
- **Method 1 (Built-in Windows Compiler - 0 Installs Needed)**:
  ```cmd
  cd launcher
  build_launcher.bat
  ```
- **Method 2 (.NET SDK / CLI)**:
  ```powershell
  dotnet build launcher/Launcher.csproj -c Release
  ```
- **Method 3 (Visual Studio)**:
  Open `launcher/Launcher.csproj` in Visual Studio and build.

---

## 📁 Repository Structure

```text
├── src/
│   └── JarDecompiler.java         # Main Java application (Workbench GUI, Editor, Decompile Engine, RecompileService)
├── launcher/
│   ├── Launcher.cs                # Native C# launcher (Win11 dialogs, embedded resource loader, CLI console attach)
│   ├── Launcher.csproj            # Visual Studio and dotnet build project file
│   ├── build_launcher.bat         # 1-Click C# build script (uses built-in csc.exe)
│   ├── app.ico                    # Windows application icon
│   └── README.md                  # Launcher documentation & build guide
├── engines/
│   ├── vineflower-1.12.0.jar      # Bundled Vineflower engine
│   └── cfr-0.152.jar              # Bundled CFR engine
├── samples/
│   ├── test-app.jar               # Sample test application
│   ├── crackme.jar                # Level 1 CrackMe challenge
│   └── crackme_harder.jar         # Level 2 Multi-Gate CrackMe challenge
├── build.bat                      # 1-Click Windows batch build script
├── build.ps1                      # PowerShell build script
├── .gitignore                     # Standard Git ignore rules
├── LICENSE                        # MIT License
└── README.md                      # Documentation
```

---

## 🎯 Included Sample Challenges

The `samples/` folder includes test archives for benchmarking and verifying the editor workflow:

- **`test-app.jar`**: Contains Java 21+ records, enums, switch expressions, and config resources.
- **`crackme.jar` (Level 1)**: Locked login vault with an impossible timestamp check. Open in `JarDecompiler.exe`, set `SecurityManager.authenticate` to return `true`, click **"Save As JAR..."**, and unlock the vault!
- **`crackme_harder.jar` (Level 2)**: Military-grade Citadel terminal featuring a 3-gate security pipeline (`DongleVerifier`, `QuantumEnclave`, `IntegrityGuard`) and real-time diagnostic stream.

---

## 📄 License

This project is licensed under the [MIT License](LICENSE).
Decompiler engines (`vineflower` and `cfr`) are subject to their respective open-source licenses.
