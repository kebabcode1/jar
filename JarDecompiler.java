import java.awt.*;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.StringSelection;
import java.awt.dnd.*;
import java.awt.event.*;
import java.io.*;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.Duration;
import java.util.*;
import java.util.List;
import java.util.jar.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import javax.swing.*;
import javax.swing.border.*;
import javax.swing.event.*;
import javax.swing.plaf.basic.BasicTreeUI;
import javax.swing.text.*;
import javax.swing.tree.*;
import javax.swing.undo.UndoManager;

/**
 * JarDecompiler - Universal JAR Decompiler & Editor with Modern Windows 11 Workbench.
 * 
 * Features:
 *  - Interactive In-App JAR Editor (Edit .java code and config files live)
 *  - Save As JAR capability with automatic javac recompilation and archive repackaging
 *  - Real Modern Windows 11 IFileOpenDialog & SaveFileDialog
 *  - Modern File & Package Explorer Tree with Segoe UI, smooth selection pills, and file size badges
 *  - Modern Syntax-Styled Code Editor with line numbers, search, undo/redo (Ctrl+Z/Ctrl+Y), and Ctrl+S
 *  - Drag & Drop Fluent Card drop zone
 *  - Native Windows .EXE and CLI suite
 */
public class JarDecompiler {

    public static final String VERSION = "1.5.0";
    public static final String VINEFLOWER_VERSION = "1.12.0";
    public static final String CFR_VERSION = "0.152";

    public static final String VINEFLOWER_URL =
            "https://repo1.maven.org/maven2/org/vineflower/vineflower/" + VINEFLOWER_VERSION + "/vineflower-" + VINEFLOWER_VERSION + ".jar";
    public static final String CFR_URL =
            "https://repo1.maven.org/maven2/org/benf/cfr/" + CFR_VERSION + "/cfr-" + CFR_VERSION + ".jar";

    public static final Font FONT_UI = new Font("Segoe UI", Font.PLAIN, 13);
    public static final Font FONT_UI_BOLD = new Font("Segoe UI", Font.BOLD, 13);
    public static final Font FONT_CODE = new Font("Consolas", Font.PLAIN, 13);

    public enum Engine {
        VINEFLOWER("Vineflower (IntelliJ IDEA Engine - Recommended)", "vineflower-" + VINEFLOWER_VERSION + ".jar", VINEFLOWER_URL),
        CFR("CFR (High Compatibility Decompiler)", "cfr-" + CFR_VERSION + ".jar", CFR_URL),
        JAVAP("javap (JDK Bytecode Disassembler Fallback)", null, null);

        public final String displayName;
        public final String jarName;
        public final String downloadUrl;

        Engine(String displayName, String jarName, String downloadUrl) {
            this.displayName = displayName;
            this.jarName = jarName;
            this.downloadUrl = downloadUrl;
        }

        public static Engine fromString(String name) {
            if (name == null) return VINEFLOWER;
            String lower = name.trim().toLowerCase();
            if (lower.contains("cfr")) return CFR;
            if (lower.contains("javap") || lower.contains("disasm")) return JAVAP;
            return VINEFLOWER;
        }
    }

    public static class DecompileOptions {
        public Path jarPath;
        public Path outputDir;
        public Engine engine = Engine.VINEFLOWER;
        public String filterPattern = null;
        public boolean extractResources = true;
        public boolean openFolderWhenDone = false;
        public boolean deobfuscate = false;
    }

    public static class DecompileResult {
        public boolean success;
        public int classesCount;
        public int resourcesCount;
        public long elapsedMs;
        public String errorMessage;
        public Path outputDirectory;
    }

    // =========================================================================
    // Entry Point
    // =========================================================================

    public static void main(String[] args) {
        if (args.length == 0) {
            if (!GraphicsEnvironment.isHeadless()) {
                launchGui(null);
            } else {
                printHelp();
            }
            return;
        }

        DecompileOptions options = new DecompileOptions();
        boolean forceGui = false;

        for (int i = 0; i < args.length; i++) {
            String arg = args[i];
            switch (arg) {
                case "-h":
                case "--help":
                    printHelp();
                    return;
                case "-v":
                case "--version":
                    System.out.println("JarDecompiler version " + VERSION);
                    return;
                case "--gui":
                    forceGui = true;
                    break;
                case "-o":
                case "--output":
                    if (i + 1 < args.length) options.outputDir = Paths.get(args[++i]);
                    break;
                case "-e":
                case "--engine":
                    if (i + 1 < args.length) options.engine = Engine.fromString(args[++i]);
                    break;
                case "-f":
                case "--filter":
                    if (i + 1 < args.length) options.filterPattern = args[++i];
                    break;
                case "--no-resources":
                    options.extractResources = false;
                    break;
                case "-d":
                case "--deobfuscate":
                    options.deobfuscate = true;
                    break;
                case "--open":
                    options.openFolderWhenDone = true;
                    break;
                default:
                    if (!arg.startsWith("-") && options.jarPath == null) {
                        options.jarPath = Paths.get(arg);
                    } else {
                        System.err.println("Unknown option: " + arg);
                        printHelp();
                        System.exit(1);
                    }
                    break;
            }
        }

        if (forceGui) {
            launchGui(options.jarPath);
            return;
        }

        if (options.jarPath == null) {
            if (!GraphicsEnvironment.isHeadless()) {
                launchGui(null);
            } else {
                System.err.println("Error: Missing input JAR file.\n");
                printHelp();
                System.exit(1);
            }
            return;
        }

        // Run CLI decompilation
        System.out.println("=================================================");
        System.out.println("          JAR Decompiler v" + VERSION);
        System.out.println("=================================================");
        System.out.println("Input JAR  : " + options.jarPath.toAbsolutePath());
        System.out.println("Engine     : " + options.engine.displayName);

        DecompileResult result = executeDecompilation(options, System.out::println, null);

        if (result.success) {
            System.out.println("\n[SUCCESS] Decompilation finished in " + (result.elapsedMs / 1000.0) + "s");
            System.out.println("  Classes   : " + result.classesCount);
            System.out.println("  Resources : " + result.resourcesCount);
            System.out.println("  Output    : " + result.outputDirectory.toAbsolutePath());
            if (options.openFolderWhenDone) {
                openExplorer(result.outputDirectory);
            }
        } else {
            System.err.println("\n[FAILED] " + result.errorMessage);
            System.exit(1);
        }
    }

    private static void printHelp() {
        System.out.println("""
            JAR Decompiler - Decompile and edit Java archives into readable source code.

            USAGE:
              JarDecompiler.exe [JAR_FILE] [OPTIONS]
              java -jar JarDecompiler.jar [JAR_FILE] [OPTIONS]
              ./decompile.ps1 -Path [JAR_FILE]

            OPTIONS:
              -o, --output <DIR>         Destination directory for decompiled source code.
                                         (Default: ./decompiled_<jar_name>)
              -e, --engine <ENGINE>      Decompiler engine: vineflower | cfr | javap.
                                         (Default: vineflower)
              -f, --filter <PATTERN>     Regex filter for packages/classes to decompile
                                         (e.g., "com/example/.*" or "com.example.*").
              --no-resources             Do not extract non-class resource files.
              -d, --deobfuscate          Enable deep anti-obfuscation & identifier renaming.
              --open                     Open output directory in file explorer when done.
              --gui                      Launch interactive GUI workbench.
              -v, --version              Show version information.
              -h, --help                 Show this help message.

            EXAMPLES:
              JarDecompiler.exe myapp.jar
              JarDecompiler.exe myapp.jar -o ./src -e cfr
              JarDecompiler.exe --gui
            """);
    }

    // =========================================================================
    // Core Decompiler Execution Logic
    // =========================================================================

    public static DecompileResult executeDecompilation(
            DecompileOptions options,
            java.util.function.Consumer<String> logger,
            java.util.function.Consumer<Double> progress) {

        long startTime = System.currentTimeMillis();
        DecompileResult res = new DecompileResult();

        if (options.jarPath == null || !Files.isRegularFile(options.jarPath)) {
            res.success = false;
            res.errorMessage = "Input file does not exist or is not a file: " + options.jarPath;
            return res;
        }

        if (options.outputDir == null) {
            String fileName = options.jarPath.getFileName().toString();
            String baseName = fileName.contains(".") ? fileName.substring(0, fileName.lastIndexOf('.')) : fileName;
            Path parent = options.jarPath.getParent();
            if (parent == null) parent = Paths.get(".");
            options.outputDir = parent.resolve("decompiled_" + baseName);
        }
        res.outputDirectory = options.outputDir;

        try {
            Files.createDirectories(options.outputDir);
        } catch (IOException e) {
            res.success = false;
            res.errorMessage = "Could not create output directory: " + e.getMessage();
            return res;
        }

        int totalClasses = 0;
        int totalResources = 0;
        try (JarFile jar = new JarFile(options.jarPath.toFile())) {
            Enumeration<JarEntry> entries = jar.entries();
            while (entries.hasMoreElements()) {
                JarEntry entry = entries.nextElement();
                if (entry.isDirectory()) continue;
                if (entry.getName().endsWith(".class")) {
                    totalClasses++;
                } else {
                    totalResources++;
                }
            }
        } catch (Exception e) {
            res.success = false;
            res.errorMessage = "Failed to inspect JAR contents: " + e.getMessage();
            return res;
        }

        logger.accept("Found " + totalClasses + " classes and " + totalResources + " resources in JAR.");

        if (options.extractResources && totalResources > 0) {
            logger.accept("Extracting resource files...");
            int extractedRes = extractNonClassResources(options.jarPath, options.outputDir, logger);
            res.resourcesCount = extractedRes;
            logger.accept("Extracted " + extractedRes + " resource files.");
        }

        if (progress != null) progress.accept(0.2);

        try {
            if (options.engine == Engine.JAVAP) {
                logger.accept("Decompiling (disassembling) using JDK javap...");
                res.classesCount = runJavapDecompile(options, logger, progress);
                res.success = true;
            } else {
                Path engineJar = resolveEngine(options.engine, logger);
                if (engineJar == null || !Files.exists(engineJar)) {
                    logger.accept("Engine JAR unavailable; falling back to JDK javap disassembler.");
                    res.classesCount = runJavapDecompile(options, logger, progress);
                    res.success = true;
                } else {
                    logger.accept("Executing engine: " + options.engine.name() + " (" + engineJar.getFileName() + ")");
                    boolean ok = runEngineProcess(options, engineJar, logger, progress);
                    if (!ok) {
                        res.success = false;
                        res.errorMessage = "Decompiler engine exited with non-zero status.";
                        return res;
                    }
                    res.classesCount = countGeneratedJavaFiles(options.outputDir);
                    if (options.deobfuscate) {
                        logger.accept("Post-processing project files with source deobfuscator (cleaning non-ASCII names, beautifying synthetic variables, decoding unicode)...");
                        int deobfCount = postProcessDeobfuscateDirectory(options.outputDir, logger);
                        logger.accept("Deobfuscated and cleaned " + deobfCount + " source files.");
                    }
                    res.success = true;
                }
            }
        } catch (Exception e) {
            res.success = false;
            res.errorMessage = "Decompilation error: " + e.getMessage();
            return res;
        }

        if (progress != null) progress.accept(1.0);
        res.elapsedMs = System.currentTimeMillis() - startTime;
        return res;
    }

    private static int extractNonClassResources(Path jarPath, Path outDir, java.util.function.Consumer<String> logger) {
        int count = 0;
        try (JarFile jar = new JarFile(jarPath.toFile())) {
            Enumeration<JarEntry> entries = jar.entries();
            while (entries.hasMoreElements()) {
                JarEntry entry = entries.nextElement();
                if (entry.isDirectory() || entry.getName().endsWith(".class")) continue;

                Path destFile = outDir.resolve(entry.getName());
                if (!destFile.normalize().startsWith(outDir.normalize())) {
                    continue;
                }
                Files.createDirectories(destFile.getParent());
                try (InputStream in = jar.getInputStream(entry)) {
                    Files.copy(in, destFile, StandardCopyOption.REPLACE_EXISTING);
                    count++;
                }
            }
        } catch (Exception e) {
            logger.accept("Warning while extracting resources: " + e.getMessage());
        }
        return count;
    }

    private static Path resolveEngine(Engine engine, java.util.function.Consumer<String> logger) {
        if (engine == Engine.JAVAP || engine.jarName == null) return null;

        Path localEngineDir = Paths.get("engines").resolve(engine.jarName);
        if (Files.exists(localEngineDir)) return localEngineDir.toAbsolutePath();

        try {
            Path appDir = Paths.get(JarDecompiler.class.getProtectionDomain().getCodeSource().getLocation().toURI()).getParent();
            if (appDir != null) {
                Path candidate = appDir.resolve("engines").resolve(engine.jarName);
                if (Files.exists(candidate)) return candidate;
                candidate = appDir.resolve(engine.jarName);
                if (Files.exists(candidate)) return candidate;
            }
        } catch (Exception ignored) {}

        Path userCacheDir = Paths.get(System.getProperty("user.home"), ".jardecompiler", "engines");
        Path cachedFile = userCacheDir.resolve(engine.jarName);
        if (Files.exists(cachedFile)) return cachedFile;

        logger.accept("Engine " + engine.name() + " not found locally. Downloading from Maven Central...");
        try {
            Files.createDirectories(userCacheDir);
            logger.accept("Connecting to " + engine.downloadUrl);
            HttpClient client = HttpClient.newBuilder()
                    .followRedirects(HttpClient.Redirect.NORMAL)
                    .connectTimeout(Duration.ofSeconds(15))
                    .build();

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(engine.downloadUrl))
                    .header("User-Agent", "JarDecompiler/" + VERSION)
                    .GET()
                    .build();

            Path tempDownload = userCacheDir.resolve(engine.jarName + ".tmp");
            HttpResponse<Path> response = client.send(request, HttpResponse.BodyHandlers.ofFile(tempDownload));

            if (response.statusCode() == 200) {
                Files.move(tempDownload, cachedFile, StandardCopyOption.REPLACE_EXISTING);
                logger.accept("Downloaded " + engine.jarName + " successfully to " + cachedFile);
                return cachedFile;
            } else {
                logger.accept("Failed to download engine JAR. HTTP code: " + response.statusCode());
                Files.deleteIfExists(tempDownload);
            }
        } catch (Exception e) {
            logger.accept("Download error: " + e.getMessage());
        }

        return null;
    }

    private static boolean runEngineProcess(
            DecompileOptions options,
            Path engineJar,
            java.util.function.Consumer<String> logger,
            java.util.function.Consumer<Double> progress) throws Exception {

        List<String> command = new ArrayList<>();
        command.add(getJavaExecutablePath());
        command.add("-jar");
        command.add(engineJar.toAbsolutePath().toString());

        if (options.engine == Engine.CFR) {
            command.add(options.jarPath.toAbsolutePath().toString());
            command.add("--outputdir");
            command.add(options.outputDir.toAbsolutePath().toString());
            command.add("--silent");
            command.add("false");
            if (options.filterPattern != null && !options.filterPattern.isBlank()) {
                command.add("--jarfilter");
                command.add(options.filterPattern.replace(".", "\\."));
            }
            if (options.deobfuscate) {
                command.add("--antiobf");
                command.add("true");
                command.add("--obfattr");
                command.add("true");
                command.add("--obfcontrol");
                command.add("true");
                command.add("--rename");
                command.add("true");
                command.add("--renamedupmembers");
                command.add("true");
                command.add("--renameillegalidents");
                command.add("true");
                command.add("--renameenumidents");
                command.add("true");
                command.add("--renamesmallmembers");
                command.add("3");
                command.add("--removebadgenerics");
                command.add("true");
                command.add("--removedeadconditionals");
                command.add("true");
                command.add("--sugarasserts");
                command.add("true");
                command.add("--constobf");
                command.add("true");
            }
        } else {
            command.add("--pattern-matching=true");
            command.add("--thread-count=4");
            if (options.deobfuscate) {
                command.add("--rename-members=true");
                command.add("--variable-renaming=jad");
                command.add("--rename-parameters=true");
                command.add("--synthetic-not-set=true");
                command.add("--ternary-constant-simplification=true");
                command.add("--prettify-ifs=true");
            }
            command.add(options.jarPath.toAbsolutePath().toString());
            command.add(options.outputDir.toAbsolutePath().toString());
        }

        logger.accept("Running process: " + String.join(" ", command));

        ProcessBuilder pb = new ProcessBuilder(command);
        pb.redirectErrorStream(true);
        Process process = pb.start();

        try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
            String line;
            while ((line = reader.readLine()) != null) {
                logger.accept(line);
            }
        }

        int exitCode = process.waitFor();
        return exitCode == 0;
    }

    private static int runJavapDecompile(
            DecompileOptions options,
            java.util.function.Consumer<String> logger,
            java.util.function.Consumer<Double> progress) {

        int processed = 0;
        String javapPath = getJavapExecutablePath();
        logger.accept("Using javap executable: " + javapPath);

        Pattern pattern = null;
        if (options.filterPattern != null && !options.filterPattern.isBlank()) {
            String regex = options.filterPattern.replace(".", "/").replace("*", ".*");
            pattern = Pattern.compile(regex);
        }

        try (JarFile jar = new JarFile(options.jarPath.toFile())) {
            List<JarEntry> classEntries = new ArrayList<>();
            Enumeration<JarEntry> entries = jar.entries();
            while (entries.hasMoreElements()) {
                JarEntry entry = entries.nextElement();
                if (!entry.isDirectory() && entry.getName().endsWith(".class")) {
                    if (pattern == null || pattern.matcher(entry.getName()).find()) {
                        classEntries.add(entry);
                    }
                }
            }

            Path tempExtractDir = Files.createTempDirectory("jardecompiler_extract_");
            try {
                int total = classEntries.size();
                for (int i = 0; i < total; i++) {
                    JarEntry entry = classEntries.get(i);
                    Path tempClass = tempExtractDir.resolve(entry.getName());
                    Files.createDirectories(tempClass.getParent());
                    try (InputStream in = jar.getInputStream(entry)) {
                        Files.copy(in, tempClass, StandardCopyOption.REPLACE_EXISTING);
                    }

                    ProcessBuilder pb = new ProcessBuilder(
                            javapPath,
                            "-c",
                            "-p",
                            "-v",
                            tempClass.toAbsolutePath().toString()
                    );
                    pb.redirectErrorStream(true);
                    Process p = pb.start();

                    StringBuilder output = new StringBuilder();
                    try (BufferedReader r = new BufferedReader(new InputStreamReader(p.getInputStream()))) {
                        String l;
                        while ((l = r.readLine()) != null) {
                            output.append(l).append(System.lineSeparator());
                        }
                    }
                    p.waitFor();

                    String relName = entry.getName().substring(0, entry.getName().length() - ".class".length()) + ".java";
                    Path outFile = options.outputDir.resolve(relName);
                    Files.createDirectories(outFile.getParent());
                    Files.writeString(outFile, "// Disassembled with javap\n" + output.toString());
                    processed++;

                    if (progress != null && total > 0) {
                        progress.accept(0.2 + (0.8 * processed / total));
                    }
                }
            } finally {
                deleteRecursively(tempExtractDir);
            }
        } catch (Exception e) {
            logger.accept("javap disassembler error: " + e.getMessage());
        }

        return processed;
    }

    private static int countGeneratedJavaFiles(Path dir) {
        if (!Files.isDirectory(dir)) return 0;
        int count = 0;
        try (var stream = Files.walk(dir)) {
            count = (int) stream.filter(p -> Files.isRegularFile(p) && p.toString().endsWith(".java")).count();
        } catch (Exception ignored) {}
        return count;
    }

    private static int postProcessDeobfuscateDirectory(Path dir, java.util.function.Consumer<String> logger) {
        if (!Files.isDirectory(dir)) return 0;
        int[] count = new int[]{0};
        try (var stream = Files.walk(dir)) {
            stream.filter(p -> Files.isRegularFile(p) && p.toString().endsWith(".java"))
                  .forEach(p -> {
                      try {
                          String content = Files.readString(p, StandardCharsets.UTF_8);
                          DeobfuscationService.DeobfuscateResult result = DeobfuscationService.deobfuscateSource(content);
                          if (result.modified) {
                              Files.writeString(p, result.transformedCode, StandardCharsets.UTF_8);
                              count[0]++;
                          }
                      } catch (Exception ignored) {}
                  });
        } catch (Exception e) {
            if (logger != null) logger.accept("Post-processing notice: " + e.getMessage());
        }
        return count[0];
    }

    private static String getJavaExecutablePath() {
        String javaHome = System.getProperty("java.home");
        Path bin = Paths.get(javaHome, "bin", "java.exe");
        if (Files.exists(bin)) return bin.toAbsolutePath().toString();
        bin = Paths.get(javaHome, "bin", "java");
        if (Files.exists(bin)) return bin.toAbsolutePath().toString();
        return "java";
    }

    private static String getJavacExecutablePath() {
        String javaHome = System.getProperty("java.home");
        Path bin = Paths.get(javaHome, "bin", "javac.exe");
        if (Files.exists(bin)) return bin.toAbsolutePath().toString();
        bin = Paths.get(javaHome, "bin", "javac");
        if (Files.exists(bin)) return bin.toAbsolutePath().toString();
        return "javac";
    }

    private static String getJavapExecutablePath() {
        String javaHome = System.getProperty("java.home");
        Path bin = Paths.get(javaHome, "bin", "javap.exe");
        if (Files.exists(bin)) return bin.toAbsolutePath().toString();
        bin = Paths.get(javaHome, "bin", "javap");
        if (Files.exists(bin)) return bin.toAbsolutePath().toString();
        return "javap";
    }

    private static void deleteRecursively(Path path) {
        try {
            if (Files.isDirectory(path)) {
                try (var stream = Files.list(path)) {
                    for (Path child : stream.toList()) {
                        deleteRecursively(child);
                    }
                }
            }
            Files.deleteIfExists(path);
        } catch (Exception ignored) {}
    }

    public static void openExplorer(Path path) {
        try {
            if (System.getProperty("os.name").toLowerCase().contains("win")) {
                if (Files.isRegularFile(path)) {
                    new ProcessBuilder("explorer.exe", "/select,", path.toAbsolutePath().toString()).start();
                } else {
                    new ProcessBuilder("explorer.exe", path.toAbsolutePath().toString()).start();
                }
            } else if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.OPEN)) {
                Desktop.getDesktop().open(path.toFile());
            }
        } catch (Exception e) {
            System.err.println("Could not open file explorer: " + e.getMessage());
        }
    }

    // =========================================================================
    // Modern Windows 11 File Dialog (IFileOpenDialog & SaveFileDialog)
    // =========================================================================

    private static Path findJarDecompilerExe() {
        try {
            Path p = Paths.get("JarDecompiler.exe");
            if (Files.isRegularFile(p)) return p.toAbsolutePath();

            Path codeSource = Paths.get(JarDecompiler.class.getProtectionDomain().getCodeSource().getLocation().toURI());
            Path dir = Files.isDirectory(codeSource) ? codeSource : codeSource.getParent();
            if (dir != null) {
                Path candidate = dir.resolve("JarDecompiler.exe");
                if (Files.isRegularFile(candidate)) return candidate.toAbsolutePath();
            }
        } catch (Exception ignored) {}
        return null;
    }

    public static Path showModernOpenFileDialog(Component parent, String title, String filter) {
        Path exePath = findJarDecompilerExe();
        if (exePath != null && Files.isRegularFile(exePath)) {
            try {
                ProcessBuilder pb = new ProcessBuilder(
                        exePath.toAbsolutePath().toString(),
                        "--pick-file",
                        title,
                        filter
                );
                Process p = pb.start();
                try (BufferedReader r = new BufferedReader(new InputStreamReader(p.getInputStream(), StandardCharsets.UTF_8))) {
                    String line = r.readLine();
                    p.waitFor();
                    if (line != null && !line.trim().isEmpty()) {
                        Path res = Paths.get(line.trim());
                        if (Files.isRegularFile(res)) return res;
                    }
                }
                return null;
            } catch (Exception ignored) {}
        }

        if (System.getProperty("os.name").toLowerCase().contains("win")) {
            try {
                String script = "[System.Reflection.Assembly]::LoadWithPartialName('System.Windows.Forms') | Out-Null; " +
                        "[System.Windows.Forms.Application]::EnableVisualStyles(); " +
                        "$d = New-Object System.Windows.Forms.OpenFileDialog; " +
                        "$d.AutoUpgradeEnabled = $true; " +
                        "$d.Title = '" + title.replace("'", "''") + "'; " +
                        "$d.Filter = '" + filter.replace("'", "''") + "'; " +
                        "$d.RestoreDirectory = $true; " +
                        "if ($d.ShowDialog() -eq [System.Windows.Forms.DialogResult]::OK) { Write-Output $d.FileName }";

                ProcessBuilder pb = new ProcessBuilder("powershell.exe", "-NoProfile", "-Command", script);
                Process p = pb.start();
                try (BufferedReader r = new BufferedReader(new InputStreamReader(p.getInputStream(), StandardCharsets.UTF_8))) {
                    String line = r.readLine();
                    p.waitFor();
                    if (line != null && !line.trim().isEmpty()) {
                        Path res = Paths.get(line.trim());
                        if (Files.isRegularFile(res)) return res;
                    }
                }
                return null;
            } catch (Exception ignored) {}
        }

        JFileChooser jfc = new JFileChooser();
        jfc.setDialogTitle(title);
        if (jfc.showOpenDialog(parent) == JFileChooser.APPROVE_OPTION) {
            return jfc.getSelectedFile().toPath();
        }
        return null;
    }

    public static Path showModernSaveFileDialog(Component parent, String defaultName, String filter, String title) {
        Path exePath = findJarDecompilerExe();
        if (exePath != null && Files.isRegularFile(exePath)) {
            try {
                ProcessBuilder pb = new ProcessBuilder(
                        exePath.toAbsolutePath().toString(),
                        "--save-file",
                        defaultName,
                        filter,
                        title
                );
                Process p = pb.start();
                try (BufferedReader r = new BufferedReader(new InputStreamReader(p.getInputStream(), StandardCharsets.UTF_8))) {
                    String line = r.readLine();
                    p.waitFor();
                    if (line != null && !line.trim().isEmpty()) {
                        return Paths.get(line.trim());
                    }
                }
                return null;
            } catch (Exception ignored) {}
        }

        if (System.getProperty("os.name").toLowerCase().contains("win")) {
            try {
                String script = "[System.Reflection.Assembly]::LoadWithPartialName('System.Windows.Forms') | Out-Null; " +
                        "[System.Windows.Forms.Application]::EnableVisualStyles(); " +
                        "$d = New-Object System.Windows.Forms.SaveFileDialog; " +
                        "$d.AutoUpgradeEnabled = $true; " +
                        "$d.FileName = '" + defaultName.replace("'", "''") + "'; " +
                        "$d.Filter = '" + filter.replace("'", "''") + "'; " +
                        "$d.Title = '" + title.replace("'", "''") + "'; " +
                        "$d.RestoreDirectory = $true; " +
                        "if ($d.ShowDialog() -eq [System.Windows.Forms.DialogResult]::OK) { Write-Output $d.FileName }";

                ProcessBuilder pb = new ProcessBuilder("powershell.exe", "-NoProfile", "-Command", script);
                Process p = pb.start();
                try (BufferedReader r = new BufferedReader(new InputStreamReader(p.getInputStream(), StandardCharsets.UTF_8))) {
                    String line = r.readLine();
                    p.waitFor();
                    if (line != null && !line.trim().isEmpty()) {
                        return Paths.get(line.trim());
                    }
                }
                return null;
            } catch (Exception ignored) {}
        }

        JFileChooser jfc = new JFileChooser();
        jfc.setSelectedFile(new File(defaultName));
        jfc.setDialogTitle(title);
        if (jfc.showSaveDialog(parent) == JFileChooser.APPROVE_OPTION) {
            return jfc.getSelectedFile().toPath();
        }
        return null;
    }

    public static String loadEmbeddedSource(String filename) {
        List<Path> candidates = List.of(
                Paths.get(filename),
                Paths.get("launcher", filename),
                Paths.get("src", filename),
                Paths.get("open-source", filename),
                Paths.get("open-source", "launcher", filename),
                Paths.get("open-source", "src", filename)
        );
        for (Path p : candidates) {
            if (Files.isRegularFile(p)) {
                try {
                    return Files.readString(p, StandardCharsets.UTF_8);
                } catch (Exception ignored) {}
            }
        }

        try (InputStream in = JarDecompiler.class.getResourceAsStream("/" + filename)) {
            if (in != null) {
                return new String(in.readAllBytes(), StandardCharsets.UTF_8);
            }
        } catch (Exception ignored) {}

        return "// Source file " + filename + " could not be located.";
    }

    // =========================================================================
    // Recompilation & JAR Packaging Engine
    // =========================================================================

    public static class RecompileService {
        public static class RecompileResult {
            public boolean success;
            public String compilerOutput;
            public int filesCompiled;
        }

        public static RecompileResult compileProject(Path projectDir, Path originalJarPath) {
            RecompileResult res = new RecompileResult();
            List<String> javaFiles = new ArrayList<>();

            try (var stream = Files.walk(projectDir)) {
                stream.filter(p -> Files.isRegularFile(p) && p.toString().endsWith(".java"))
                        .forEach(p -> javaFiles.add(p.toAbsolutePath().toString()));
            } catch (Exception e) {
                res.success = false;
                res.compilerOutput = "Failed to scan for Java source files: " + e.getMessage();
                return res;
            }

            if (javaFiles.isEmpty()) {
                res.success = true;
                res.filesCompiled = 0;
                res.compilerOutput = "No Java source files found to compile.";
                return res;
            }

            List<String> command = new ArrayList<>();
            command.add(getJavacExecutablePath());

            // Build classpath (projectDir + original JAR dependencies)
            String sep = File.pathSeparator;
            String cp = projectDir.toAbsolutePath().toString();
            if (originalJarPath != null && Files.isRegularFile(originalJarPath)) {
                cp += sep + originalJarPath.toAbsolutePath().toString();
            }

            command.add("-cp");
            command.add(cp);
            command.add("-d");
            command.add(projectDir.toAbsolutePath().toString());
            command.add("-encoding");
            command.add("UTF-8");
            command.addAll(javaFiles);

            try {
                ProcessBuilder pb = new ProcessBuilder(command);
                pb.redirectErrorStream(true);
                Process proc = pb.start();

                StringBuilder sb = new StringBuilder();
                try (BufferedReader reader = new BufferedReader(new InputStreamReader(proc.getInputStream(), StandardCharsets.UTF_8))) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        sb.append(line).append("\n");
                    }
                }
                int exitCode = proc.waitFor();
                res.success = (exitCode == 0);
                res.compilerOutput = sb.toString();
                res.filesCompiled = javaFiles.size();
            } catch (Exception e) {
                res.success = false;
                res.compilerOutput = "javac execution error: " + e.getMessage();
            }

            return res;
        }

        public static void packJar(Path sourceDir, Path destinationJar) throws Exception {
            Path absDest = destinationJar.toAbsolutePath().normalize();
            if (absDest.getParent() != null) {
                Files.createDirectories(absDest.getParent());
            }

            // Load manifest if present
            Path manifestFile = sourceDir.resolve("META-INF").resolve("MANIFEST.MF");
            Manifest manifest;
            if (Files.isRegularFile(manifestFile)) {
                try (InputStream in = Files.newInputStream(manifestFile)) {
                    manifest = new Manifest(in);
                }
            } else {
                manifest = new Manifest();
                manifest.getMainAttributes().put(Attributes.Name.MANIFEST_VERSION, "1.0");
            }

            try (JarOutputStream jos = new JarOutputStream(Files.newOutputStream(absDest), manifest)) {
                try (var stream = Files.walk(sourceDir)) {
                    List<Path> allPaths = stream.filter(Files::isRegularFile).toList();
                    for (Path p : allPaths) {
                        String rel = sourceDir.relativize(p).toString().replace("\\", "/");
                        if (rel.equalsIgnoreCase("META-INF/MANIFEST.MF")) {
                            continue; // Automatically added by JarOutputStream
                        }
                        if (rel.endsWith(".java")) {
                            continue; // Omit source files from binary JAR
                        }

                        JarEntry entry = new JarEntry(rel);
                        entry.setTime(Files.getLastModifiedTime(p).toMillis());
                        jos.putNextEntry(entry);
                        Files.copy(p, jos);
                        jos.closeEntry();
                    }
                }
            }
        }
    }

    // =========================================================================
    // Deobfuscation Service: Source Cleaner & Anti-Obfuscation Pipeline
    // =========================================================================

    public static class DeobfuscationService {

        public static class DeobfuscateResult {
            public final String transformedCode;
            public final int unicodeCount;
            public final int identifiersRenamed;
            public final int predicatesFolded;
            public final int nonAsciiCleaned;
            public final boolean modified;

            public DeobfuscateResult(String transformedCode, int unicodeCount, int identifiersRenamed, int predicatesFolded, int nonAsciiCleaned) {
                this.transformedCode = transformedCode;
                this.unicodeCount = unicodeCount;
                this.identifiersRenamed = identifiersRenamed;
                this.predicatesFolded = predicatesFolded;
                this.nonAsciiCleaned = nonAsciiCleaned;
                this.modified = (unicodeCount + identifiersRenamed + predicatesFolded + nonAsciiCleaned) > 0;
            }
        }

        public static DeobfuscateResult deobfuscateSource(String source) {
            if (source == null || source.isEmpty()) {
                return new DeobfuscateResult(source, 0, 0, 0, 0);
            }

            int unicodeCount = 0;
            int predicatesFolded = 0;
            int identifiersRenamed = 0;
            int nonAsciiCleaned = 0;

            // 1. Decode Unicode escapes (\u0048\u0065\u006c\u006c\u006f)
            StringBuilder sb = new StringBuilder();
            Pattern unicodePattern = Pattern.compile("\\\\u([0-9a-fA-F]{4})");
            Matcher m = unicodePattern.matcher(source);
            while (m.find()) {
                int codePoint = Integer.parseInt(m.group(1), 16);
                String replacement;
                if (codePoint == '"') {
                    replacement = "\\\"";
                } else if (codePoint == '\\') {
                    replacement = "\\\\";
                } else if (codePoint == '\n') {
                    replacement = "\\n";
                } else if (codePoint == '\r') {
                    replacement = "\\r";
                } else if (codePoint == '\t') {
                    replacement = "\\t";
                } else if (codePoint >= 32 && codePoint <= 126) {
                    replacement = Character.toString((char) codePoint);
                    unicodeCount++;
                } else if (Character.isLetterOrDigit(codePoint)) {
                    replacement = Character.toString((char) codePoint);
                    unicodeCount++;
                } else {
                    replacement = m.group(0);
                }
                m.appendReplacement(sb, Matcher.quoteReplacement(replacement));
            }
            m.appendTail(sb);
            String current = sb.toString();

            // 2. Non-ASCII / CJK / Cyrillic Obfuscation Sanitizer
            // Clean non-ASCII in package statements (e.g. package 袚嫮.鸏瀱.汉餑; -> package pkg_1.pkg_2.pkg_3;)
            Pattern pkgPattern = Pattern.compile("(package\\s+)([^;]+)(;)");
            Matcher pkgMatcher = pkgPattern.matcher(current);
            if (pkgMatcher.find()) {
                String fullPkg = pkgMatcher.group(2).trim();
                if (hasNonAscii(fullPkg)) {
                    String[] parts = fullPkg.split("\\.");
                    StringBuilder newPkg = new StringBuilder();
                    for (int i = 0; i < parts.length; i++) {
                        if (i > 0) newPkg.append(".");
                        if (hasNonAscii(parts[i])) {
                            newPkg.append("pkg_").append(i + 1);
                        } else {
                            newPkg.append(parts[i]);
                        }
                    }
                    current = current.replace(fullPkg, newPkg.toString());
                    nonAsciiCleaned++;
                }
            }

            // Clean any remaining non-ASCII tokens (Chinese, Cyrillic, symbols)
            Pattern nonAsciiWordPattern = Pattern.compile("[^\\s;.,(){\\}\\[\\]<>\"'/+=*&|!~?:-]*[^\\x00-\\x7F]+[^\\s;.,(){\\}\\[\\]<>\"'/+=*&|!~?:-]*");
            Matcher nonAsciiMatcher = nonAsciiWordPattern.matcher(current);
            Map<String, String> nonAsciiRenames = new LinkedHashMap<>();
            int obfIdx = 1;
            while (nonAsciiMatcher.find()) {
                String word = nonAsciiMatcher.group();
                if (!nonAsciiRenames.containsKey(word)) {
                    String replacementName = "ObfClass_" + (obfIdx++);
                    nonAsciiRenames.put(word, replacementName);
                }
            }
            for (Map.Entry<String, String> entry : nonAsciiRenames.entrySet()) {
                current = current.replace(entry.getKey(), entry.getValue());
                nonAsciiCleaned++;
            }

            // 3. Constant folding & opaque predicates
            String[] foldingRules = {
                "true\\s*&&\\s*true", "true",
                "false\\s*\\|\\|\\s*false", "false",
                "true\\s*\\|\\|\\s*false", "true",
                "false\\s*\\|\\|\\s*true", "true",
                "1\\s*==\\s*1", "true",
                "0\\s*==\\s*0", "true",
                "1\\s*==\\s*2", "false",
                "0\\s*==\\s*1", "false",
                "1\\s*!=\\s*1", "false",
                "1\\s*!=\\s*2", "true",
                "!false", "true",
                "!true", "false",
                "true\\s*\\?\\s*([^:?]+?)\\s*:\\s*([^:?;]+?)\\s*;", "$1;",
                "false\\s*\\?\\s*([^:?]+?)\\s*:\\s*([^:?;]+?)\\s*;", "$2;"
            };
            for (int i = 0; i < foldingRules.length; i += 2) {
                Pattern p = Pattern.compile(foldingRules[i]);
                Matcher matcher = p.matcher(current);
                int count = 0;
                StringBuffer buffer = new StringBuffer();
                while (matcher.find()) {
                    matcher.appendReplacement(buffer, foldingRules[i + 1]);
                    count++;
                }
                matcher.appendTail(buffer);
                if (count > 0) {
                    predicatesFolded += count;
                    current = buffer.toString();
                }
            }

            // Dead code annotation
            Pattern deadIfPattern = Pattern.compile("if\\s*\\(\\s*false\\s*\\)");
            Matcher deadIfMatcher = deadIfPattern.matcher(current);
            if (deadIfMatcher.find()) {
                current = deadIfMatcher.replaceAll("/* Dead branch */ if (false)");
                predicatesFolded++;
            }

            // 4. Synthetic Identifier Beautification (e.g. d0, d1, d2, f, f1, i0, var1)
            Pattern declPattern = Pattern.compile("([A-Za-z0-9_$.<>\\[\\]]+)\\s+([a-zA-Z_$][a-zA-Z0-9_$]*)\\s*(?:=|;|,|\\)|:)");
            Matcher declMatcher = declPattern.matcher(current);
            Map<String, String> renames = new LinkedHashMap<>();

            while (declMatcher.find()) {
                String rawType = declMatcher.group(1);
                String varName = declMatcher.group(2);

                if (rawType.equals("return") || rawType.equals("throw") || rawType.equals("package") ||
                    rawType.equals("import") || rawType.equals("class") || rawType.equals("interface") ||
                    rawType.equals("enum") || rawType.equals("record")) {
                    continue;
                }

                boolean isSynthetic = varName.matches("^(var|p|param|arg|v|_)\\d+$") ||
                                      (varName.length() == 1 && Character.isLowerCase(varName.charAt(0))) ||
                                      varName.matches("^[a-z]\\d+$");

                if (isSynthetic && !renames.containsKey(varName)) {
                    String prefix = getPrefixForType(rawType);
                    String num = "";
                    if (varName.matches(".*\\d+$")) {
                        num = varName.replaceAll("^\\D+", "");
                    } else {
                        num = String.valueOf(renames.size() + 1);
                    }
                    String candidate = prefix + num;
                    int cNum = 1;
                    while ((current.contains(candidate) || renames.containsValue(candidate)) && !candidate.equals(varName)) {
                        candidate = prefix + (cNum++);
                    }
                    renames.put(varName, candidate);
                }
            }

            if (!renames.isEmpty()) {
                for (Map.Entry<String, String> r : renames.entrySet()) {
                    String oldName = r.getKey();
                    String newName = r.getValue();
                    Pattern wordPattern = Pattern.compile("\\b" + Pattern.quote(oldName) + "\\b");
                    Matcher wordMatcher = wordPattern.matcher(current);
                    if (wordMatcher.find()) {
                        current = wordMatcher.replaceAll(newName);
                        identifiersRenamed++;
                    }
                }
            }

            return new DeobfuscateResult(current, unicodeCount, identifiersRenamed, predicatesFolded, nonAsciiCleaned);
        }

        private static boolean hasNonAscii(String str) {
            if (str == null) return false;
            for (int i = 0; i < str.length(); i++) {
                if (str.charAt(i) > 127) return true;
            }
            return false;
        }

        private static String getPrefixForType(String rawType) {
            if (rawType.contains("<")) {
                rawType = rawType.substring(0, rawType.indexOf('<'));
            }
            if (rawType.contains(".")) {
                rawType = rawType.substring(rawType.lastIndexOf('.') + 1);
            }

            switch (rawType) {
                case "String": return "str";
                case "int": return "num";
                case "long": return "longVal";
                case "boolean": return "flag";
                case "double": return "dVal";
                case "float": return "fVal";
                case "byte[]": return "bytes";
                case "byte": return "bVal";
                case "char": return "ch";
                case "File": return "file";
                case "Path": return "path";
                case "Exception": return "ex";
                case "Throwable": return "err";
                case "StringBuilder": return "sb";
                case "List": return "list";
                case "Map": return "map";
                case "Set": return "set";
                case "Consumer": return "consumer";
                case "Supplier": return "supplier";
                case "Function": return "func";
                case "Predicate": return "predicate";
                case "InputStream": return "inStream";
                case "OutputStream": return "outStream";
                default:
                    if (rawType.length() > 0 && Character.isUpperCase(rawType.charAt(0))) {
                        return Character.toLowerCase(rawType.charAt(0)) + rawType.substring(1);
                    }
                    return "obj";
            }
        }
    }

    // =========================================================================
    // GUI Workbench: Launch & Main Frame
    // =========================================================================

    public static void launchGui(Path preselectedJar) {
        SwingUtilities.invokeLater(() -> {
            try {
                UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
            } catch (Exception ignored) {}
            WorkbenchFrame frame = new WorkbenchFrame(preselectedJar);
            frame.setVisible(true);
        });
    }

    public static class WorkbenchFrame extends JFrame {
        private Path currentJarPath;
        private Path currentOutputDir;
        private Engine currentEngine = Engine.VINEFLOWER;

        private DropZonePanel dropZone;
        private FileTreePanel fileTreePanel;
        private CodeViewerPanel codeViewerPanel;
        private JComboBox<Engine> cmbEngine;
        private JButton btnDecompile;
        private JButton btnDeobfuscate;
        private JButton btnSaveAsJar;
        private JButton btnOpenExplorer;
        private JProgressBar progressBar;
        private JLabel lblStatus;
        private JDialog logDialog;
        private JTextArea txtLogArea;

        private SwingWorker<DecompileResult, String> decompileWorker;

        public WorkbenchFrame(Path initialJar) {
            setTitle("JAR Decompiler & Editor");
            setSize(1220, 800);
            setMinimumSize(new Dimension(900, 600));
            setLocationRelativeTo(null);
            setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);

            initLogDialog();
            initUI();

            if (initialJar != null && Files.isRegularFile(initialJar)) {
                loadAndDecompileJar(initialJar);
            }
        }

        private void initLogDialog() {
            logDialog = new JDialog(this, "Decompilation Console Output", false);
            logDialog.setSize(700, 440);
            logDialog.setLocationRelativeTo(this);
            txtLogArea = new JTextArea();
            txtLogArea.setEditable(false);
            txtLogArea.setFont(FONT_CODE);
            txtLogArea.setBackground(new Color(248, 250, 252));
            txtLogArea.setBorder(new EmptyBorder(8, 8, 8, 8));
            logDialog.getContentPane().add(new JScrollPane(txtLogArea));
        }

        private void initUI() {
            JPanel root = new JPanel(new BorderLayout(0, 0));
            root.setBackground(new Color(243, 244, 246));

            // 1. Top Section: Modern Drop Zone Card & Fluent Controls Bar
            JPanel topContainer = new JPanel(new BorderLayout(0, 6));
            topContainer.setOpaque(false);
            topContainer.setBorder(new EmptyBorder(10, 14, 6, 14));

            dropZone = new DropZonePanel(this, this::onJarSelected);
            topContainer.add(dropZone, BorderLayout.NORTH);

            // Controls Bar
            JPanel controlsBar = new JPanel(new BorderLayout(8, 0));
            controlsBar.setOpaque(false);
            controlsBar.setBorder(new EmptyBorder(4, 2, 4, 2));

            JPanel leftControls = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
            leftControls.setOpaque(false);

            JLabel lblEng = new JLabel("Engine:");
            lblEng.setFont(FONT_UI_BOLD);
            lblEng.setForeground(new Color(51, 65, 85));
            leftControls.add(lblEng);

            cmbEngine = new JComboBox<>(Engine.values());
            cmbEngine.setFont(FONT_UI);
            cmbEngine.setPreferredSize(new Dimension(340, 30));
            cmbEngine.setRenderer(new DefaultListCellRenderer() {
                @Override
                public Component getListCellRendererComponent(JList<?> list, Object value, int index, boolean isSelected, boolean cellHasFocus) {
                    super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
                    if (value instanceof Engine eng) {
                        setText("  " + eng.displayName);
                    }
                    return this;
                }
            });
            cmbEngine.addActionListener(e -> currentEngine = (Engine) cmbEngine.getSelectedItem());
            leftControls.add(cmbEngine);

            btnDecompile = createStyledButton("Re-Decompile", new Color(0, 120, 212), new Color(15, 23, 42));
            btnDecompile.setEnabled(false);
            btnDecompile.setToolTipText("Re-run decompilation using the selected engine");
            btnDecompile.addActionListener(e -> {
                if (currentJarPath != null) loadAndDecompileJar(currentJarPath);
            });
            leftControls.add(btnDecompile);

            btnDeobfuscate = createStyledButton("Deobfuscate ▼", new Color(124, 58, 237), new Color(15, 23, 42));
            btnDeobfuscate.setEnabled(false);
            btnDeobfuscate.setToolTipText("Deobfuscate active code file or run deep anti-obfuscation decompiler");

            JPopupMenu deobfMenu = new JPopupMenu();
            JMenuItem itemCurrentFile = new JMenuItem("Deobfuscate Active File (Clean variables, fold constants, decode unicode)    Ctrl+Alt+D");
            itemCurrentFile.setFont(FONT_UI);
            itemCurrentFile.addActionListener(ev -> codeViewerPanel.deobfuscateActiveFile());

            JMenuItem itemDeepProject = new JMenuItem("Deep Deobfuscate Project (Re-run Engine with Anti-Obfuscation flags)");
            itemDeepProject.setFont(FONT_UI);
            itemDeepProject.addActionListener(ev -> {
                if (currentJarPath != null) {
                    int confirm = JOptionPane.showConfirmDialog(
                            this,
                            "Deep deobfuscation will re-run " + currentEngine.displayName + "\nwith aggressive anti-obfuscation, identifier renaming, and flow cleaning flags.\n\nProceed?",
                            "Deep Deobfuscate Project",
                            JOptionPane.YES_NO_OPTION,
                            JOptionPane.QUESTION_MESSAGE
                    );
                    if (confirm == JOptionPane.YES_OPTION) {
                        loadAndDecompileJar(currentJarPath, true);
                    }
                }
            });

            deobfMenu.add(itemCurrentFile);
            deobfMenu.addSeparator();
            deobfMenu.add(itemDeepProject);

            btnDeobfuscate.addActionListener(ev -> deobfMenu.show(btnDeobfuscate, 0, btnDeobfuscate.getHeight()));
            leftControls.add(btnDeobfuscate);

            controlsBar.add(leftControls, BorderLayout.WEST);

            JPanel rightControls = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
            rightControls.setOpaque(false);

            // Prominent "Save As JAR..." button
            btnSaveAsJar = createStyledButton("Save As JAR...", new Color(16, 124, 65), new Color(15, 23, 42));
            btnSaveAsJar.setEnabled(false);
            btnSaveAsJar.setToolTipText("Recompile modified sources and package into a new .jar file (Ctrl+Shift+S)");
            btnSaveAsJar.addActionListener(e -> saveAsJar());
            rightControls.add(btnSaveAsJar);

            JButton btnShowLog = createStyledButton("Console Log", new Color(203, 213, 225), new Color(15, 23, 42));
            btnShowLog.addActionListener(e -> {
                logDialog.setLocationRelativeTo(this);
                logDialog.setVisible(true);
            });
            rightControls.add(btnShowLog);

            btnOpenExplorer = createStyledButton("Open in Explorer", new Color(203, 213, 225), new Color(15, 23, 42));
            btnOpenExplorer.setEnabled(false);
            btnOpenExplorer.addActionListener(e -> {
                if (currentOutputDir != null) openExplorer(currentOutputDir);
            });
            rightControls.add(btnOpenExplorer);

            JButton btnViewSource = createStyledButton("App Source", new Color(203, 213, 225), new Color(15, 23, 42));
            btnViewSource.setToolTipText("View the C# .exe launcher & Java source code, or export to build yourself");
            btnViewSource.addActionListener(e -> showApplicationSourceDialog());
            rightControls.add(btnViewSource);

            controlsBar.add(rightControls, BorderLayout.EAST);
            topContainer.add(controlsBar, BorderLayout.SOUTH);

            root.add(topContainer, BorderLayout.NORTH);

            // 2. Center Section: Modern Split Pane (File Explorer + Code Editor)
            fileTreePanel = new FileTreePanel(this::onFileSelectedInTree);
            codeViewerPanel = new CodeViewerPanel(this, this);

            JSplitPane splitPane = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, fileTreePanel, codeViewerPanel);
            splitPane.setDividerLocation(340);
            splitPane.setResizeWeight(0.28);
            splitPane.setContinuousLayout(true);
            splitPane.setBorder(BorderFactory.createMatteBorder(1, 0, 1, 0, new Color(226, 232, 240)));
            splitPane.setBackground(new Color(243, 244, 246));

            root.add(splitPane, BorderLayout.CENTER);

            // 3. Bottom Status Bar
            JPanel statusBar = new JPanel(new BorderLayout(10, 0));
            statusBar.setBackground(Color.WHITE);
            statusBar.setBorder(BorderFactory.createCompoundBorder(
                    BorderFactory.createMatteBorder(1, 0, 0, 0, new Color(226, 232, 240)),
                    new EmptyBorder(5, 16, 5, 16)
            ));

            lblStatus = new JLabel("Ready. Click the drop zone above or drag and drop a .jar file to inspect & edit.");
            lblStatus.setFont(FONT_UI);
            lblStatus.setForeground(new Color(100, 116, 139));

            progressBar = new JProgressBar(0, 100);
            progressBar.setPreferredSize(new Dimension(190, 16));
            progressBar.setStringPainted(true);
            progressBar.setFont(new Font("Segoe UI", Font.PLAIN, 10));

            statusBar.add(lblStatus, BorderLayout.CENTER);
            statusBar.add(progressBar, BorderLayout.EAST);

            root.add(statusBar, BorderLayout.SOUTH);

            // Global Shortcut Ctrl+Shift+S for Save As JAR
            root.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW).put(
                    KeyStroke.getKeyStroke(KeyEvent.VK_S, InputEvent.CTRL_DOWN_MASK | InputEvent.SHIFT_DOWN_MASK), "saveAsJar");
            root.getActionMap().put("saveAsJar", new AbstractAction() {
                @Override
                public void actionPerformed(ActionEvent e) {
                    if (btnSaveAsJar.isEnabled()) saveAsJar();
                }
            });

            // Global Shortcut Ctrl+Alt+D for Deobfuscate File
            root.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW).put(
                    KeyStroke.getKeyStroke(KeyEvent.VK_D, InputEvent.CTRL_DOWN_MASK | InputEvent.ALT_DOWN_MASK), "deobfuscateCurrentFile");
            root.getActionMap().put("deobfuscateCurrentFile", new AbstractAction() {
                @Override
                public void actionPerformed(ActionEvent e) {
                    if (codeViewerPanel != null) codeViewerPanel.deobfuscateActiveFile();
                }
            });

            setContentPane(root);
        }

        private void onJarSelected(Path jarPath) {
            loadAndDecompileJar(jarPath);
        }

        private void onFileSelectedInTree(Path filePath) {
            codeViewerPanel.loadFile(filePath);
        }

        public void setStatus(String text) {
            lblStatus.setText(text);
        }

        public void loadAndDecompileJar(Path jarPath) {
            loadAndDecompileJar(jarPath, false);
        }

        public void loadAndDecompileJar(Path jarPath, boolean deobfuscate) {
            if (decompileWorker != null && !decompileWorker.isDone()) {
                decompileWorker.cancel(true);
            }

            this.currentJarPath = jarPath;
            String fileName = jarPath.getFileName().toString();
            String baseName = fileName.contains(".") ? fileName.substring(0, fileName.lastIndexOf('.')) : fileName;
            Path parent = jarPath.getParent();
            if (parent == null) parent = Paths.get(".");
            this.currentOutputDir = parent.resolve("decompiled_" + baseName);

            dropZone.setLoadedFile(jarPath);
            btnDecompile.setEnabled(false);
            btnDeobfuscate.setEnabled(false);
            btnSaveAsJar.setEnabled(false);
            btnOpenExplorer.setEnabled(false);
            progressBar.setIndeterminate(true);
            lblStatus.setText((deobfuscate ? "Decompiling & deobfuscating " : "Decompiling ") + fileName + " with " + currentEngine.displayName + "...");
            txtLogArea.setText("");

            codeViewerPanel.showPlaceholder("Decompiling archive" + (deobfuscate ? " (with Anti-Obfuscation)" : "") + ": " + fileName + "...\nPlease wait.");

            DecompileOptions options = new DecompileOptions();
            options.jarPath = jarPath;
            options.outputDir = currentOutputDir;
            options.engine = currentEngine;
            options.extractResources = true;
            options.deobfuscate = deobfuscate;

            decompileWorker = new SwingWorker<>() {
                @Override
                protected DecompileResult doInBackground() {
                    return executeDecompilation(
                            options,
                            this::publish,
                            p -> setProgress((int) (p * 100))
                    );
                }

                @Override
                protected void process(List<String> chunks) {
                    for (String line : chunks) {
                        txtLogArea.append(line + "\n");
                    }
                    txtLogArea.setCaretPosition(txtLogArea.getDocument().getLength());
                }

                @Override
                protected void done() {
                    progressBar.setIndeterminate(false);
                    progressBar.setValue(100);
                    btnDecompile.setEnabled(true);
                    btnDeobfuscate.setEnabled(true);

                    try {
                        DecompileResult result = get();
                        if (result.success) {
                            btnSaveAsJar.setEnabled(true);
                            btnOpenExplorer.setEnabled(true);
                            lblStatus.setText((deobfuscate ? "Deep anti-obfuscation decompilation finished: " : "Decompiled ") +
                                    result.classesCount + " classes & " + result.resourcesCount +
                                    " resources in " + (result.elapsedMs / 1000.0) + "s  |  Editable in viewer!");

                            fileTreePanel.loadDirectory(currentOutputDir, jarPath.getFileName().toString());

                            Path firstJava = findFirstJavaFile(currentOutputDir);
                            if (firstJava != null) {
                                codeViewerPanel.loadFile(firstJava);
                                fileTreePanel.selectPath(firstJava);
                            } else {
                                codeViewerPanel.showPlaceholder("Decompilation complete. Select a file on the left to edit.");
                            }
                        } else {
                            lblStatus.setText("Decompilation failed: " + result.errorMessage);
                            codeViewerPanel.showPlaceholder("Decompilation failed:\n" + result.errorMessage);
                            JOptionPane.showMessageDialog(WorkbenchFrame.this, result.errorMessage, "Decompilation Failed", JOptionPane.ERROR_MESSAGE);
                        }
                    } catch (Exception ex) {
                        lblStatus.setText("Error: " + ex.getMessage());
                        codeViewerPanel.showPlaceholder("Error: " + ex.getMessage());
                    }
                }
            };

            decompileWorker.execute();
        }

        public void saveAsJar() {
            if (currentJarPath == null || currentOutputDir == null) {
                JOptionPane.showMessageDialog(this, "Please open and decompile a JAR archive first.", "No Active Project", JOptionPane.WARNING_MESSAGE);
                return;
            }

            // Auto-save active file if modified
            if (codeViewerPanel.isModified()) {
                codeViewerPanel.saveCurrentFile();
            }

            String origName = currentJarPath.getFileName().toString();
            String baseName = origName.contains(".") ? origName.substring(0, origName.lastIndexOf('.')) : origName;
            String defaultSaveName = baseName + "_edited.jar";

            Path destJar = showModernSaveFileDialog(
                    this,
                    defaultSaveName,
                    "Java Archives (*.jar)|*.jar|All Files (*.*)|*.*",
                    "Save Edited JAR Archive"
            );

            if (destJar == null) return;

            lblStatus.setText("Recompiling sources and packaging " + destJar.getFileName() + "...");
            progressBar.setIndeterminate(true);
            btnSaveAsJar.setEnabled(false);

            SwingWorker<Boolean, String> packWorker = new SwingWorker<>() {
                private String compileError = null;
                private int compiledCount = 0;

                @Override
                protected Boolean doInBackground() {
                    // Step 1: Recompile Java sources with javac
                    publish("Compiling Java source files with javac...");
                    RecompileService.RecompileResult cr = RecompileService.compileProject(currentOutputDir, currentJarPath);
                    compiledCount = cr.filesCompiled;

                    if (!cr.success) {
                        compileError = cr.compilerOutput;
                        return false;
                    }

                    // Step 2: Bundle all .class and resource files into destJar
                    publish("Packaging updated JAR archive to " + destJar.getFileName() + "...");
                    try {
                        RecompileService.packJar(currentOutputDir, destJar);
                        return true;
                    } catch (Exception e) {
                        compileError = "Packaging failed: " + e.getMessage();
                        return false;
                    }
                }

                @Override
                protected void process(List<String> chunks) {
                    for (String msg : chunks) {
                        lblStatus.setText(msg);
                    }
                }

                @Override
                protected void done() {
                    progressBar.setIndeterminate(false);
                    progressBar.setValue(100);
                    btnSaveAsJar.setEnabled(true);

                    try {
                        boolean ok = get();
                        if (ok) {
                            lblStatus.setText("Successfully saved edited JAR: " + destJar.getFileName());

                            Object[] options = {"Open in Explorer", "OK"};
                            int choice = JOptionPane.showOptionDialog(
                                    WorkbenchFrame.this,
                                    "Your edits have been successfully recompiled (" + compiledCount + " classes) and saved to:\n\n" +
                                            destJar.toAbsolutePath(),
                                    "JAR Saved Successfully",
                                    JOptionPane.YES_NO_OPTION,
                                    JOptionPane.INFORMATION_MESSAGE,
                                    null,
                                    options,
                                    options[0]
                            );

                            if (choice == 0) {
                                openExplorer(destJar);
                            }
                        } else {
                            lblStatus.setText("Compilation/Save error.");
                            showCompilerErrorDialog(compileError);
                        }
                    } catch (Exception ex) {
                        lblStatus.setText("Error: " + ex.getMessage());
                        JOptionPane.showMessageDialog(WorkbenchFrame.this, "Save error: " + ex.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
                    }
                }
            };

            packWorker.execute();
        }

        private void showCompilerErrorDialog(String errorDetails) {
            JDialog dialog = new JDialog(this, "Compilation Errors - Cannot Save JAR", true);
            dialog.setSize(680, 420);
            dialog.setLocationRelativeTo(this);

            JPanel content = new JPanel(new BorderLayout(8, 8));
            content.setBorder(new EmptyBorder(12, 12, 12, 12));

            JLabel lblMsg = new JLabel("javac encountered syntax errors while compiling your edited Java code:");
            lblMsg.setFont(FONT_UI_BOLD);
            lblMsg.setForeground(new Color(185, 28, 28));

            JTextArea txtErr = new JTextArea(errorDetails);
            txtErr.setFont(FONT_CODE);
            txtErr.setEditable(false);
            txtErr.setBackground(new Color(254, 242, 242));
            txtErr.setForeground(new Color(153, 27, 27));
            txtErr.setBorder(new EmptyBorder(8, 8, 8, 8));

            JPanel btnPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT));
            JButton btnClose = createStyledButton("Back to Editor", new Color(241, 245, 249), new Color(30, 41, 59));
            btnClose.addActionListener(e -> dialog.dispose());
            btnPanel.add(btnClose);

            content.add(lblMsg, BorderLayout.NORTH);
            content.add(new JScrollPane(txtErr), BorderLayout.CENTER);
            content.add(btnPanel, BorderLayout.SOUTH);

            dialog.setContentPane(content);
            dialog.setVisible(true);
        }

        private void showApplicationSourceDialog() {
            Object[] options = {
                    "View Launcher.cs (.EXE Source)",
                    "View JarDecompiler.java (Workbench Source)",
                    "Export Full Project & Build Script...",
                    "Cancel"
            };

            int choice = JOptionPane.showOptionDialog(
                    this,
                    "JAR Decompiler is 100% Free & Open Source!\n\n" +
                            "• Launcher.cs: Native Windows 11 C# launcher (Win11 dialogs, embedded payloads, CLI)\n" +
                            "• JarDecompiler.java: Complete Java workbench, code editor, and javac recompiler\n" +
                            "• build.bat & build.ps1: 1-click build scripts to compile from source\n\n" +
                            "What would you like to do?",
                    "Application Source Code & Build Options",
                    JOptionPane.DEFAULT_OPTION,
                    JOptionPane.INFORMATION_MESSAGE,
                    null,
                    options,
                    options[0]
            );

            if (choice == 0) {
                String cs = loadEmbeddedSource("Launcher.cs");
                codeViewerPanel.loadContent("Launcher.cs", cs, false);
                lblStatus.setText("Viewing Launcher.cs — Native C# Windows Executable Source");
            } else if (choice == 1) {
                String java = loadEmbeddedSource("JarDecompiler.java");
                codeViewerPanel.loadContent("JarDecompiler.java", java, false);
                lblStatus.setText("Viewing JarDecompiler.java — Java Workbench & Decompiler Source");
            } else if (choice == 2) {
                exportSourceProject();
            }
        }

        private void exportSourceProject() {
            Path savePath = showModernSaveFileDialog(this, "JarDecompiler-Source", "All Files (*.*)|*.*", "Export Application Source Code (Select Destination Folder)");
            if (savePath == null) return;

            Path targetDir = Files.isDirectory(savePath) ? savePath : savePath.getParent().resolve(savePath.getFileName().toString().replace(".jar", ""));

            try {
                Files.createDirectories(targetDir);
                Path srcDir = targetDir.resolve("src");
                Path launcherDir = targetDir.resolve("launcher");
                Path enginesDir = targetDir.resolve("engines");
                Files.createDirectories(srcDir);
                Files.createDirectories(launcherDir);
                Files.createDirectories(enginesDir);

                // Write source files
                Files.writeString(launcherDir.resolve("Launcher.cs"), loadEmbeddedSource("Launcher.cs"), StandardCharsets.UTF_8);
                Files.writeString(srcDir.resolve("JarDecompiler.java"), loadEmbeddedSource("JarDecompiler.java"), StandardCharsets.UTF_8);

                // Copy engines if available
                Path localEngines = Paths.get("engines");
                if (Files.isDirectory(localEngines)) {
                    try (var stream = Files.list(localEngines)) {
                        for (Path p : stream.toList()) {
                            Files.copy(p, enginesDir.resolve(p.getFileName()), StandardCopyOption.REPLACE_EXISTING);
                        }
                    }
                }

                // Write build scripts
                String buildBat = "@echo off\r\ncall launcher\\build_launcher.bat\r\n";
                Files.writeString(targetDir.resolve("build.bat"), buildBat, StandardCharsets.UTF_8);

                JOptionPane.showMessageDialog(
                        this,
                        "Full application source code and build scripts exported to:\n\n" +
                                targetDir.toAbsolutePath() + "\n\n" +
                                "To build it yourself, run 'build.bat' inside the exported folder!",
                        "Source Code Exported",
                        JOptionPane.INFORMATION_MESSAGE
                );
                lblStatus.setText("Source code exported to " + targetDir.getFileName());
            } catch (Exception ex) {
                JOptionPane.showMessageDialog(this, "Failed to export source: " + ex.getMessage(), "Export Error", JOptionPane.ERROR_MESSAGE);
            }
        }

        private Path findFirstJavaFile(Path dir) {
            try (var stream = Files.walk(dir)) {
                return stream.filter(p -> Files.isRegularFile(p) && p.toString().endsWith(".java"))
                        .findFirst()
                        .orElse(null);
            } catch (Exception ignored) {
                return null;
            }
        }

        public static JButton createStyledButton(String text, Color borderAccent, Color fg) {
            JButton btn = new JButton(text) {
                @Override
                protected void paintComponent(Graphics g) {
                    Graphics2D g2 = (Graphics2D) g.create();
                    g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                    g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

                    Color bg = Color.WHITE;
                    if (!isEnabled()) {
                        bg = new Color(248, 250, 252);
                    } else if (getModel().isPressed()) {
                        bg = new Color(226, 232, 240);
                    } else if (getModel().isRollover()) {
                        bg = new Color(241, 245, 249);
                    }

                    g2.setColor(bg);
                    g2.fillRoundRect(1, 1, getWidth() - 2, getHeight() - 2, 8, 8);

                    Color border = (borderAccent != null && isEnabled()) ? borderAccent : new Color(203, 213, 225);
                    g2.setColor(border);
                    g2.setStroke(new BasicStroke(1.5f));
                    g2.drawRoundRect(1, 1, getWidth() - 3, getHeight() - 3, 8, 8);

                    g2.dispose();
                    super.paintComponent(g);
                }
            };
            btn.setContentAreaFilled(false);
            btn.setOpaque(false);
            btn.setFont(FONT_UI_BOLD);
            // Black text color (#0F172A) guarantees clear readability on white background
            btn.setForeground(new Color(15, 23, 42));
            btn.setFocusPainted(false);
            btn.setBorder(new EmptyBorder(6, 14, 6, 14));
            btn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            return btn;
        }
    }

    // =========================================================================
    // Drop Zone Card (Modern Windows 11 Fluent Design)
    // =========================================================================

    public static class DropZonePanel extends JPanel {
        private final Frame parentFrame;
        private final java.util.function.Consumer<Path> onSelectFile;
        private boolean isDragOver = false;
        private final JLabel lblIcon = new JLabel("📁");
        private final JLabel lblTitle = new JLabel("Click to Choose JAR File or Drag & Drop Here");
        private final JLabel lblSubtitle = new JLabel("Supports .jar, .war, and .zip archives (Java 8 through 24)");
        private final JButton btnChange;

        private static final Color BG_CARD = Color.WHITE;
        private static final Color BG_HOVER = new Color(240, 247, 255);
        private static final Color BORDER_CARD = new Color(203, 213, 225);
        private static final Color BORDER_HOVER = new Color(0, 120, 212);

        public DropZonePanel(Frame parentFrame, java.util.function.Consumer<Path> onSelectFile) {
            this.parentFrame = parentFrame;
            this.onSelectFile = onSelectFile;
            setLayout(new BorderLayout(14, 0));
            setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            setPreferredSize(new Dimension(0, 74));
            setBackground(BG_CARD);

            lblIcon.setFont(new Font("Segoe UI Emoji", Font.PLAIN, 30));
            lblIcon.setBorder(new EmptyBorder(0, 18, 0, 4));

            JPanel textPanel = new JPanel(new GridLayout(2, 1, 0, 3));
            textPanel.setOpaque(false);

            lblTitle.setFont(new Font("Segoe UI", Font.BOLD, 14));
            lblTitle.setForeground(new Color(15, 23, 42));

            lblSubtitle.setFont(new Font("Segoe UI", Font.PLAIN, 12));
            lblSubtitle.setForeground(new Color(100, 116, 139));

            textPanel.add(lblTitle);
            textPanel.add(lblSubtitle);

            btnChange = WorkbenchFrame.createStyledButton("Change Archive...", new Color(248, 250, 252), new Color(15, 23, 42));
            btnChange.setVisible(false);
            btnChange.addActionListener(e -> chooseNativeFile());

            JPanel rightPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 18, 20));
            rightPanel.setOpaque(false);
            rightPanel.add(btnChange);

            add(lblIcon, BorderLayout.WEST);
            add(textPanel, BorderLayout.CENTER);
            add(rightPanel, BorderLayout.EAST);

            addMouseListener(new MouseAdapter() {
                @Override
                public void mouseClicked(MouseEvent e) {
                    chooseNativeFile();
                }

                @Override
                public void mouseEntered(MouseEvent e) {
                    isDragOver = true;
                    setBackground(BG_HOVER);
                    repaint();
                }

                @Override
                public void mouseExited(MouseEvent e) {
                    isDragOver = false;
                    setBackground(BG_CARD);
                    repaint();
                }
            });

            new DropTarget(this, new DropTargetAdapter() {
                @Override
                public void dragEnter(DropTargetDragEvent dtde) {
                    isDragOver = true;
                    setBackground(BG_HOVER);
                    repaint();
                }

                @Override
                public void dragExit(DropTargetEvent dte) {
                    isDragOver = false;
                    setBackground(BG_CARD);
                    repaint();
                }

                @Override
                @SuppressWarnings("unchecked")
                public void drop(DropTargetDropEvent dtde) {
                    isDragOver = false;
                    setBackground(BG_CARD);
                    repaint();
                    try {
                        dtde.acceptDrop(DnDConstants.ACTION_COPY);
                        List<File> files = (List<File>) dtde.getTransferable().getTransferData(DataFlavor.javaFileListFlavor);
                        if (!files.isEmpty()) {
                            File f = files.get(0);
                            String name = f.getName().toLowerCase();
                            if (name.endsWith(".jar") || name.endsWith(".war") || name.endsWith(".zip")) {
                                onSelectFile.accept(f.toPath());
                            } else {
                                JOptionPane.showMessageDialog(DropZonePanel.this,
                                        "Selected file is not a supported Java archive:\n" + f.getName(),
                                        "Invalid File", JOptionPane.WARNING_MESSAGE);
                            }
                        }
                    } catch (Exception ex) {
                        JOptionPane.showMessageDialog(DropZonePanel.this, "Error processing dropped file: " + ex.getMessage());
                    }
                }
            });
        }

        public void setLoadedFile(Path jarPath) {
            String fileName = jarPath.getFileName().toString();
            long size = 0;
            try { size = Files.size(jarPath); } catch (Exception ignored) {}
            String sizeStr = formatFileSize(size);

            lblIcon.setText("📦");
            lblTitle.setText(fileName + "  (" + sizeStr + ")");
            lblSubtitle.setText(jarPath.toAbsolutePath().toString());
            btnChange.setVisible(true);
            revalidate();
            repaint();
        }

        private void chooseNativeFile() {
            Path chosen = showModernOpenFileDialog(parentFrame, "Select Java Archive to Decompile & Edit", "Java Archives (*.jar;*.zip;*.war)|*.jar;*.zip;*.war|All Files (*.*)|*.*");
            if (chosen != null) {
                onSelectFile.accept(chosen);
            }
        }

        private String formatFileSize(long bytes) {
            if (bytes < 1024) return bytes + " B";
            if (bytes < 1024 * 1024) return String.format("%.1f KB", bytes / 1024.0);
            return String.format("%.2f MB", bytes / (1024.0 * 1024.0));
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

            g2.setColor(getBackground());
            g2.fillRoundRect(2, 2, getWidth() - 5, getHeight() - 5, 10, 10);

            g2.setColor(isDragOver ? BORDER_HOVER : BORDER_CARD);
            float strokeWidth = isDragOver ? 2.0f : 1.2f;
            float[] dash = {6f, 4f};
            g2.setStroke(new BasicStroke(strokeWidth, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER, 10f, dash, 0f));
            g2.drawRoundRect(2, 2, getWidth() - 5, getHeight() - 5, 10, 10);
            g2.dispose();
            super.paintChildren(g);
        }
    }

    // =========================================================================
    // Modern File & Package Explorer Tree
    // =========================================================================

    public static class FileTreePanel extends JPanel {
        private final java.util.function.Consumer<Path> onFileSelected;
        private final JTree tree;
        private DefaultTreeModel treeModel;
        private final JTextField txtFilter = new JTextField();
        private Path rootDirectory;
        private String rootDisplayName = "Archive Structure";

        public FileTreePanel(java.util.function.Consumer<Path> onFileSelected) {
            this.onFileSelected = onFileSelected;
            setLayout(new BorderLayout(0, 6));
            setBackground(Color.WHITE);
            setBorder(new EmptyBorder(8, 10, 8, 6));

            JPanel searchBar = new JPanel(new BorderLayout(6, 0));
            searchBar.setOpaque(false);
            searchBar.setBorder(BorderFactory.createCompoundBorder(
                    BorderFactory.createLineBorder(new Color(226, 232, 240), 1, true),
                    new EmptyBorder(4, 8, 4, 8)
            ));

            JLabel lblSearch = new JLabel("🔍");
            lblSearch.setFont(new Font("Segoe UI Emoji", Font.PLAIN, 12));
            txtFilter.setFont(FONT_UI);
            txtFilter.setBorder(null);
            txtFilter.putClientProperty("JTextField.placeholderText", "Search files & packages...");
            txtFilter.getDocument().addDocumentListener(new DocumentListener() {
                public void insertUpdate(DocumentEvent e) { refreshTree(); }
                public void removeUpdate(DocumentEvent e) { refreshTree(); }
                public void changedUpdate(DocumentEvent e) { refreshTree(); }
            });

            searchBar.add(lblSearch, BorderLayout.WEST);
            searchBar.add(txtFilter, BorderLayout.CENTER);

            DefaultMutableTreeNode emptyRoot = new DefaultMutableTreeNode("No JAR loaded");
            treeModel = new DefaultTreeModel(emptyRoot);
            tree = new JTree(treeModel);
            tree.setFont(FONT_UI);
            tree.setRootVisible(true);
            tree.setShowsRootHandles(true);
            tree.setRowHeight(28);
            tree.putClientProperty("JTree.lineStyle", "None");
            tree.setBackground(Color.WHITE);
            tree.setCellRenderer(new ModernTreeCellRenderer());

            BasicTreeUI ui = (BasicTreeUI) tree.getUI();
            ui.setLeftChildIndent(10);
            ui.setRightChildIndent(10);

            tree.addTreeSelectionListener(e -> {
                DefaultMutableTreeNode node = (DefaultMutableTreeNode) tree.getLastSelectedPathComponent();
                if (node != null && node.getUserObject() instanceof FileNodeItem item) {
                    if (!item.isDirectory && Files.isRegularFile(item.path)) {
                        onFileSelected.accept(item.path);
                    }
                }
            });

            JScrollPane scrollPane = new JScrollPane(tree);
            scrollPane.setBorder(BorderFactory.createLineBorder(new Color(226, 232, 240)));
            scrollPane.setBackground(Color.WHITE);
            scrollPane.getViewport().setBackground(Color.WHITE);

            add(searchBar, BorderLayout.NORTH);
            add(scrollPane, BorderLayout.CENTER);
        }

        public void loadDirectory(Path dir, String displayName) {
            this.rootDirectory = dir;
            this.rootDisplayName = displayName;
            refreshTree();
        }

        public void selectPath(Path targetPath) {
            DefaultMutableTreeNode root = (DefaultMutableTreeNode) treeModel.getRoot();
            Enumeration<?> en = root.depthFirstEnumeration();
            while (en.hasMoreElements()) {
                DefaultMutableTreeNode node = (DefaultMutableTreeNode) en.nextElement();
                if (node.getUserObject() instanceof FileNodeItem item) {
                    if (item.path.equals(targetPath)) {
                        TreePath treePath = new TreePath(node.getPath());
                        tree.setSelectionPath(treePath);
                        tree.scrollPathToVisible(treePath);
                        break;
                    }
                }
            }
        }

        private void refreshTree() {
            if (rootDirectory == null || !Files.isDirectory(rootDirectory)) return;

            String query = txtFilter.getText().trim().toLowerCase();
            DefaultMutableTreeNode root = new DefaultMutableTreeNode(new FileNodeItem(rootDisplayName, rootDirectory, true));
            buildTreeNodes(rootDirectory, root, query);

            treeModel.setRoot(root);
            treeModel.reload();

            for (int i = 0; i < Math.min(tree.getRowCount(), 30); i++) {
                tree.expandRow(i);
            }
        }

        private boolean buildTreeNodes(Path currentDir, DefaultMutableTreeNode parentNode, String filter) {
            boolean hasMatchingChildren = false;
            try (var stream = Files.list(currentDir)) {
                List<Path> sorted = stream.sorted((a, b) -> {
                    boolean da = Files.isDirectory(a);
                    boolean db = Files.isDirectory(b);
                    if (da && !db) return -1;
                    if (!da && db) return 1;
                    return a.getFileName().toString().compareToIgnoreCase(b.getFileName().toString());
                }).toList();

                for (Path p : sorted) {
                    String name = p.getFileName().toString();
                    boolean isDir = Files.isDirectory(p);

                    if (isDir) {
                        DefaultMutableTreeNode dirNode = new DefaultMutableTreeNode(new FileNodeItem(name, p, true));
                        boolean childMatched = buildTreeNodes(p, dirNode, filter);
                        boolean nameMatched = filter.isEmpty() || name.toLowerCase().contains(filter);

                        if (childMatched || nameMatched) {
                            parentNode.add(dirNode);
                            hasMatchingChildren = true;
                        }
                    } else {
                        boolean matches = filter.isEmpty() || name.toLowerCase().contains(filter);
                        if (matches) {
                            parentNode.add(new DefaultMutableTreeNode(new FileNodeItem(name, p, false)));
                            hasMatchingChildren = true;
                        }
                    }
                }
            } catch (Exception ignored) {}
            return hasMatchingChildren;
        }

        public static class FileNodeItem {
            public final String name;
            public final Path path;
            public final boolean isDirectory;

            public FileNodeItem(String name, Path path, boolean isDirectory) {
                this.name = name;
                this.path = path;
                this.isDirectory = isDirectory;
            }

            @Override
            public String toString() {
                return name;
            }
        }

        public static class ModernTreeCellRenderer extends JPanel implements TreeCellRenderer {
            private final JLabel lblIcon = new JLabel();
            private final JLabel lblName = new JLabel();
            private final JLabel lblBadge = new JLabel();
            private boolean isSelected = false;

            public ModernTreeCellRenderer() {
                setLayout(new BorderLayout(6, 0));
                setOpaque(false);
                setBorder(new EmptyBorder(2, 6, 2, 6));

                lblIcon.setFont(new Font("Segoe UI Emoji", Font.PLAIN, 13));
                lblName.setFont(FONT_UI);
                lblBadge.setFont(new Font("Segoe UI", Font.PLAIN, 10));
                lblBadge.setForeground(new Color(148, 163, 184));

                JPanel center = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 0));
                center.setOpaque(false);
                center.add(lblIcon);
                center.add(lblName);

                add(center, BorderLayout.CENTER);
                add(lblBadge, BorderLayout.EAST);
            }

            @Override
            public Component getTreeCellRendererComponent(JTree tree, Object value, boolean sel, boolean exp, boolean leaf, int row, boolean hasFocus) {
                this.isSelected = sel;

                if (value instanceof DefaultMutableTreeNode node) {
                    Object uo = node.getUserObject();
                    if (uo instanceof FileNodeItem item) {
                        lblName.setText(item.name);
                        String nameLower = item.name.toLowerCase();

                        if (item.isDirectory) {
                            lblIcon.setText(exp ? "📂" : "📁");
                            lblName.setForeground(sel ? new Color(0, 102, 204) : new Color(30, 41, 59));
                            lblBadge.setText("");
                        } else if (nameLower.endsWith(".java")) {
                            lblIcon.setText("☕");
                            lblName.setForeground(sel ? new Color(0, 102, 204) : new Color(15, 118, 110));
                            lblBadge.setText(".java");
                        } else if (nameLower.endsWith(".json") || nameLower.endsWith(".properties") || nameLower.endsWith(".xml") || nameLower.endsWith(".yml")) {
                            lblIcon.setText("⚙");
                            lblName.setForeground(sel ? new Color(0, 102, 204) : new Color(180, 83, 9));
                            lblBadge.setText("config");
                        } else if (nameLower.endsWith(".png") || nameLower.endsWith(".jpg") || nameLower.endsWith(".gif")) {
                            lblIcon.setText("🖼");
                            lblName.setForeground(sel ? new Color(0, 102, 204) : new Color(109, 40, 217));
                            lblBadge.setText("image");
                        } else if (nameLower.equals("manifest.mf")) {
                            lblIcon.setText("📋");
                            lblName.setForeground(sel ? new Color(0, 102, 204) : new Color(71, 85, 105));
                            lblBadge.setText("manifest");
                        } else {
                            lblIcon.setText("📄");
                            lblName.setForeground(sel ? new Color(0, 102, 204) : new Color(51, 65, 85));
                            lblBadge.setText("");
                        }
                    } else {
                        lblName.setText(value.toString());
                        lblIcon.setText("📦");
                        lblBadge.setText("");
                    }
                }
                return this;
            }

            @Override
            protected void paintComponent(Graphics g) {
                if (isSelected) {
                    Graphics2D g2 = (Graphics2D) g.create();
                    g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                    g2.setColor(new Color(235, 243, 253));
                    g2.fillRoundRect(2, 1, getWidth() - 4, getHeight() - 2, 8, 8);
                    g2.setColor(new Color(186, 215, 248));
                    g2.drawRoundRect(2, 1, getWidth() - 4, getHeight() - 2, 8, 8);
                    g2.dispose();
                }
                super.paintComponent(g);
            }
        }
    }

    // =========================================================================
    // Modern Code & Resource Editor
    // =========================================================================

    public static class CodeViewerPanel extends JPanel {
        private final Frame parentFrame;
        private final WorkbenchFrame workbenchFrame;
        private final JLabel lblBreadcrumb = new JLabel("No file selected");
        private final JLabel lblFileSize = new JLabel("");
        private final JLabel lblModifiedBadge = new JLabel("");
        private final JTextPane codeEditor = new JTextPane();
        private final JScrollPane scrollPane;
        private final TextLineNumber lineNumberGutter;

        private final JPanel searchBar = new JPanel(new FlowLayout(FlowLayout.RIGHT, 6, 2));
        private final JTextField txtSearch = new JTextField(15);
        private final JLabel lblSearchCount = new JLabel("");
        private final JButton btnSaveFile;
        private final JButton btnSaveAsJar;
        private final JButton btnDeobfuscate;
        private final JButton btnCopy;
        private final CardLayout centerCards = new CardLayout();
        private final JPanel centerPanel = new JPanel(centerCards);
        private final JLabel lblImagePreview = new JLabel("", SwingConstants.CENTER);

        private Path currentPath;
        private String originalContent = "";
        private boolean isModified = false;
        private boolean isUpdatingDoc = false;
        private final UndoManager undoManager = new UndoManager();

        private int currentSearchMatchIndex = -1;
        private final List<Integer> searchMatchOffsets = new ArrayList<>();

        public CodeViewerPanel(Frame parentFrame, WorkbenchFrame workbenchFrame) {
            this.parentFrame = parentFrame;
            this.workbenchFrame = workbenchFrame;
            setLayout(new BorderLayout(0, 0));
            setBackground(Color.WHITE);
            setBorder(new EmptyBorder(8, 6, 8, 10));

            // Top Header Bar
            JPanel topHeader = new JPanel(new BorderLayout(8, 0));
            topHeader.setBackground(Color.WHITE);
            topHeader.setBorder(new EmptyBorder(0, 4, 6, 4));

            JPanel breadcrumbBox = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 4));
            breadcrumbBox.setOpaque(false);
            lblBreadcrumb.setFont(FONT_UI_BOLD);
            lblBreadcrumb.setForeground(new Color(15, 23, 42));

            lblModifiedBadge.setFont(FONT_UI_BOLD);
            lblModifiedBadge.setForeground(new Color(234, 88, 12)); // Orange badge

            lblFileSize.setFont(FONT_UI);
            lblFileSize.setForeground(new Color(148, 163, 184));
            breadcrumbBox.add(lblBreadcrumb);
            breadcrumbBox.add(lblModifiedBadge);
            breadcrumbBox.add(lblFileSize);

            JPanel fileActions = new JPanel(new FlowLayout(FlowLayout.RIGHT, 6, 0));
            fileActions.setOpaque(false);

            btnSaveFile = WorkbenchFrame.createStyledButton("Save File", new Color(203, 213, 225), new Color(15, 23, 42));
            btnSaveFile.setEnabled(false);
            btnSaveFile.setToolTipText("Save current file to disk (Ctrl+S)");
            btnSaveFile.addActionListener(e -> saveCurrentFile());

            btnSaveAsJar = WorkbenchFrame.createStyledButton("Save As JAR...", new Color(16, 124, 65), new Color(15, 23, 42));
            btnSaveAsJar.setToolTipText("Recompile modified sources and package into a new .jar file (Ctrl+Shift+S)");
            btnSaveAsJar.addActionListener(e -> workbenchFrame.saveAsJar());

            btnDeobfuscate = WorkbenchFrame.createStyledButton("Deobfuscate", new Color(124, 58, 237), new Color(15, 23, 42));
            btnDeobfuscate.setEnabled(false);
            btnDeobfuscate.setToolTipText("Decode unicode escapes, fold constants, and clean synthetic variable names (Ctrl+Alt+D)");
            btnDeobfuscate.addActionListener(e -> deobfuscateActiveFile());

            btnCopy = WorkbenchFrame.createStyledButton("Copy", new Color(203, 213, 225), new Color(15, 23, 42));
            btnCopy.setToolTipText("Copy source code to clipboard");
            btnCopy.addActionListener(e -> copyCodeToClipboard());

            JButton btnFindToggle = WorkbenchFrame.createStyledButton("Find", new Color(203, 213, 225), new Color(15, 23, 42));
            btnFindToggle.setToolTipText("Find text in file (Ctrl+F)");
            btnFindToggle.addActionListener(e -> toggleSearchBar());

            fileActions.add(btnFindToggle);
            fileActions.add(btnCopy);
            fileActions.add(btnDeobfuscate);
            fileActions.add(btnSaveFile);
            fileActions.add(btnSaveAsJar);

            topHeader.add(breadcrumbBox, BorderLayout.WEST);
            topHeader.add(fileActions, BorderLayout.EAST);

            // In-Viewer Search Bar
            searchBar.setOpaque(false);
            searchBar.setBorder(BorderFactory.createCompoundBorder(
                    BorderFactory.createMatteBorder(0, 0, 1, 0, new Color(226, 232, 240)),
                    new EmptyBorder(4, 6, 6, 6)
            ));

            JLabel lblFind = new JLabel("Find:");
            lblFind.setFont(FONT_UI_BOLD);
            searchBar.add(lblFind);

            txtSearch.setFont(FONT_UI);
            txtSearch.setPreferredSize(new Dimension(170, 26));
            txtSearch.setBorder(BorderFactory.createCompoundBorder(
                    BorderFactory.createLineBorder(new Color(203, 213, 225), 1, true),
                    new EmptyBorder(2, 6, 2, 6)
            ));
            searchBar.add(txtSearch);

            JButton btnFindNext = WorkbenchFrame.createStyledButton("Next", new Color(203, 213, 225), new Color(15, 23, 42));
            btnFindNext.addActionListener(e -> findNextMatch(true));

            JButton btnFindPrev = WorkbenchFrame.createStyledButton("Previous", new Color(203, 213, 225), new Color(15, 23, 42));
            btnFindPrev.addActionListener(e -> findNextMatch(false));

            JButton btnCloseSearch = WorkbenchFrame.createStyledButton("✕", new Color(203, 213, 225), new Color(15, 23, 42));
            btnCloseSearch.addActionListener(e -> searchBar.setVisible(false));

            searchBar.add(btnFindNext);
            searchBar.add(btnFindPrev);
            searchBar.add(lblSearchCount);
            searchBar.add(btnCloseSearch);
            searchBar.setVisible(false);

            txtSearch.addActionListener(e -> findNextMatch(true));
            txtSearch.getDocument().addDocumentListener(new DocumentListener() {
                public void insertUpdate(DocumentEvent e) { runSearch(); }
                public void removeUpdate(DocumentEvent e) { runSearch(); }
                public void changedUpdate(DocumentEvent e) { runSearch(); }
            });

            JPanel northArea = new JPanel(new BorderLayout());
            northArea.setBackground(Color.WHITE);
            northArea.add(topHeader, BorderLayout.NORTH);
            northArea.add(searchBar, BorderLayout.SOUTH);
            add(northArea, BorderLayout.NORTH);

            // Editor Configuration (Editable!)
            codeEditor.setEditable(true);
            codeEditor.setFont(FONT_CODE);
            codeEditor.setBackground(Color.WHITE);
            codeEditor.setBorder(new EmptyBorder(4, 6, 4, 4));
            codeEditor.setCaretPosition(0);

            // Undo / Redo support
            codeEditor.getDocument().addUndoableEditListener(e -> {
                if (!isUpdatingDoc) undoManager.addEdit(e.getEdit());
            });

            codeEditor.getInputMap().put(KeyStroke.getKeyStroke(KeyEvent.VK_Z, InputEvent.CTRL_DOWN_MASK), "undo");
            codeEditor.getActionMap().put("undo", new AbstractAction() {
                @Override
                public void actionPerformed(ActionEvent e) {
                    if (undoManager.canUndo()) undoManager.undo();
                }
            });

            codeEditor.getInputMap().put(KeyStroke.getKeyStroke(KeyEvent.VK_Y, InputEvent.CTRL_DOWN_MASK), "redo");
            codeEditor.getActionMap().put("redo", new AbstractAction() {
                @Override
                public void actionPerformed(ActionEvent e) {
                    if (undoManager.canRedo()) undoManager.redo();
                }
            });

            // Ctrl+S shortcut for Save File
            codeEditor.getInputMap().put(KeyStroke.getKeyStroke(KeyEvent.VK_S, InputEvent.CTRL_DOWN_MASK), "saveFile");
            codeEditor.getActionMap().put("saveFile", new AbstractAction() {
                @Override
                public void actionPerformed(ActionEvent e) {
                    saveCurrentFile();
                }
            });

            // Ctrl+F shortcut for Find
            codeEditor.getInputMap().put(KeyStroke.getKeyStroke(KeyEvent.VK_F, InputEvent.CTRL_DOWN_MASK), "openFind");
            codeEditor.getActionMap().put("openFind", new AbstractAction() {
                @Override
                public void actionPerformed(ActionEvent e) {
                    toggleSearchBar();
                }
            });

            // Ctrl+Alt+D shortcut for Deobfuscate
            codeEditor.getInputMap().put(KeyStroke.getKeyStroke(KeyEvent.VK_D, InputEvent.CTRL_DOWN_MASK | InputEvent.ALT_DOWN_MASK), "deobfuscateFile");
            codeEditor.getActionMap().put("deobfuscateFile", new AbstractAction() {
                @Override
                public void actionPerformed(ActionEvent e) {
                    if (btnDeobfuscate.isEnabled()) deobfuscateActiveFile();
                }
            });

            // Track edits to flag dirty state
            codeEditor.getDocument().addDocumentListener(new DocumentListener() {
                public void insertUpdate(DocumentEvent e) { checkDirty(); }
                public void removeUpdate(DocumentEvent e) { checkDirty(); }
                public void changedUpdate(DocumentEvent e) { checkDirty(); }
            });

            scrollPane = new JScrollPane(codeEditor);
            lineNumberGutter = new TextLineNumber(codeEditor);
            scrollPane.setRowHeaderView(lineNumberGutter);
            scrollPane.setBorder(BorderFactory.createLineBorder(new Color(226, 232, 240)));
            scrollPane.getViewport().setBackground(Color.WHITE);

            JScrollPane imageScrollPane = new JScrollPane(lblImagePreview);
            imageScrollPane.setBorder(BorderFactory.createLineBorder(new Color(226, 232, 240)));
            imageScrollPane.getViewport().setBackground(new Color(248, 250, 252));

            centerPanel.add(scrollPane, "CODE");
            centerPanel.add(imageScrollPane, "IMAGE");

            add(centerPanel, BorderLayout.CENTER);

            showPlaceholder("Select a class or resource file from the explorer on the left to edit its code.");
        }

        private void checkDirty() {
            if (isUpdatingDoc || currentPath == null) return;
            String text = codeEditor.getText();
            boolean dirty = !text.equals(originalContent);
            if (dirty != isModified) {
                isModified = dirty;
                btnSaveFile.setEnabled(dirty);
                if (dirty) {
                    lblModifiedBadge.setText("● Modified");
                    btnSaveFile.setBackground(new Color(254, 243, 199)); // Soft amber highlight
                } else {
                    lblModifiedBadge.setText("");
                    btnSaveFile.setBackground(new Color(241, 245, 249));
                }
            }
        }

        public boolean isModified() {
            return isModified;
        }

        public void saveCurrentFile() {
            if (currentPath == null) return;
            try {
                String text = codeEditor.getText();
                Files.writeString(currentPath, text, StandardCharsets.UTF_8);
                originalContent = text;
                isModified = false;
                btnSaveFile.setEnabled(false);
                btnSaveFile.setBackground(new Color(241, 245, 249));
                lblModifiedBadge.setText("✓ (Saved)");
                new javax.swing.Timer(1600, evt -> {
                    if (!isModified) lblModifiedBadge.setText("");
                }).start();
            } catch (Exception ex) {
                JOptionPane.showMessageDialog(this, "Save error: " + ex.getMessage(), "Error Saving File", JOptionPane.ERROR_MESSAGE);
            }
        }

        private void toggleSearchBar() {
            searchBar.setVisible(!searchBar.isVisible());
            if (searchBar.isVisible()) {
                txtSearch.requestFocusInWindow();
                txtSearch.selectAll();
            }
            revalidate();
        }

        public void showPlaceholder(String text) {
            isUpdatingDoc = true;
            centerCards.show(centerPanel, "CODE");
            lblBreadcrumb.setText("Code Editor");
            lblFileSize.setText("");
            lblModifiedBadge.setText("");
            codeEditor.setText(text);
            codeEditor.setCaretPosition(0);
            codeEditor.setEditable(false);
            btnDeobfuscate.setEnabled(false);
            isUpdatingDoc = false;
        }

        public void loadContent(String title, String content, boolean editable) {
            if (isModified) saveCurrentFile();
            this.currentPath = null;
            lblBreadcrumb.setText("📜  " + title);
            lblFileSize.setText("(" + formatSize(content.getBytes(StandardCharsets.UTF_8).length) + ")");
            lblModifiedBadge.setText("");
            undoManager.discardAllEdits();

            isUpdatingDoc = true;
            centerCards.show(centerPanel, "CODE");
            codeEditor.setText("");
            if (title.endsWith(".java") || title.endsWith(".cs")) {
                JavaSyntaxHighlighter.highlight(codeEditor, content);
            } else {
                codeEditor.setText(content);
            }
            codeEditor.setCaretPosition(0);
            codeEditor.setEditable(editable);
            btnSaveFile.setEnabled(false);
            btnSaveAsJar.setEnabled(false);
            btnDeobfuscate.setEnabled(title.toLowerCase().endsWith(".java"));
            isUpdatingDoc = false;
            revalidate();
            repaint();
        }

        public void loadFile(Path path) {
            if (isModified) {
                // Auto-save on navigating to another file
                saveCurrentFile();
            }

            this.currentPath = path;
            String fileName = path.getFileName().toString();
            lblBreadcrumb.setText("📄  " + fileName);
            lblModifiedBadge.setText("");
            undoManager.discardAllEdits();

            try {
                long size = Files.size(path);
                lblFileSize.setText("(" + formatSize(size) + ")");

                String lower = fileName.toLowerCase();
                if (lower.endsWith(".png") || lower.endsWith(".jpg") || lower.endsWith(".jpeg") || lower.endsWith(".gif")) {
                    ImageIcon icon = new ImageIcon(path.toAbsolutePath().toString());
                    lblImagePreview.setIcon(icon);
                    lblImagePreview.setText("Resolution: " + icon.getIconWidth() + " × " + icon.getIconHeight() + " px");
                    lblImagePreview.setVerticalTextPosition(SwingConstants.BOTTOM);
                    lblImagePreview.setHorizontalTextPosition(SwingConstants.CENTER);
                    centerCards.show(centerPanel, "IMAGE");
                    codeEditor.setEditable(false);
                    btnDeobfuscate.setEnabled(false);
                } else {
                    centerCards.show(centerPanel, "CODE");
                    codeEditor.setEditable(true);

                    isUpdatingDoc = true;
                    String content = Files.readString(path, StandardCharsets.UTF_8);
                    codeEditor.setText("");

                    if (lower.endsWith(".java")) {
                        JavaSyntaxHighlighter.highlight(codeEditor, content);
                    } else {
                        codeEditor.setText(content);
                    }

                    originalContent = content;
                    isModified = false;
                    btnSaveFile.setEnabled(false);
                    btnDeobfuscate.setEnabled(lower.endsWith(".java"));
                    isUpdatingDoc = false;

                    codeEditor.setCaretPosition(0);
                }
                runSearch();
            } catch (Exception e) {
                codeEditor.setText("Failed to load file contents: " + e.getMessage());
            }
        }

        public void deobfuscateActiveFile() {
            if (currentPath == null) return;
            String currentText = codeEditor.getText();
            if (currentText.isBlank()) return;

            DeobfuscationService.DeobfuscateResult result = DeobfuscationService.deobfuscateSource(currentText);
            if (!result.modified) {
                workbenchFrame.setStatus("No obfuscated patterns detected in " + currentPath.getFileName() + ".");
                JOptionPane.showMessageDialog(this,
                        "No obfuscated patterns (non-ASCII identifiers, unicode escapes, opaque predicates, or synthetic variables)\nwere found in " + currentPath.getFileName() + ".",
                        "Deobfuscation Info", JOptionPane.INFORMATION_MESSAGE);
                return;
            }

            try {
                StyledDocument doc = codeEditor.getStyledDocument();
                doc.remove(0, doc.getLength());
                doc.insertString(0, result.transformedCode, null);
                if (currentPath.toString().toLowerCase().endsWith(".java")) {
                    JavaSyntaxHighlighter.applyStyles(codeEditor, result.transformedCode);
                }
                codeEditor.setCaretPosition(0);
            } catch (Exception ex) {
                codeEditor.setText(result.transformedCode);
            }

            checkDirty();

            String summary = String.format("✨ Deobfuscated %s: Cleaned %d non-ASCII names, decoded %d unicode escapes, beautified %d identifiers, folded %d predicates. (Press Ctrl+Z to undo)",
                    currentPath.getFileName(), result.nonAsciiCleaned, result.unicodeCount, result.identifiersRenamed, result.predicatesFolded);
            workbenchFrame.setStatus(summary);
        }

        private void runSearch() {
            searchMatchOffsets.clear();
            currentSearchMatchIndex = -1;
            String query = txtSearch.getText();

            codeEditor.getHighlighter().removeAllHighlights();

            if (query.isEmpty() || !searchBar.isVisible()) {
                lblSearchCount.setText("");
                return;
            }

            String text = codeEditor.getText().toLowerCase();
            String lowerQuery = query.toLowerCase();
            int index = 0;
            while ((index = text.indexOf(lowerQuery, index)) >= 0) {
                searchMatchOffsets.add(index);
                index += lowerQuery.length();
            }

            lblSearchCount.setText(searchMatchOffsets.size() + " matches");
            if (!searchMatchOffsets.isEmpty()) {
                findNextMatch(true);
            }
        }

        private void findNextMatch(boolean forward) {
            if (searchMatchOffsets.isEmpty()) return;

            if (forward) {
                currentSearchMatchIndex = (currentSearchMatchIndex + 1) % searchMatchOffsets.size();
            } else {
                currentSearchMatchIndex = (currentSearchMatchIndex - 1 + searchMatchOffsets.size()) % searchMatchOffsets.size();
            }

            int offset = searchMatchOffsets.get(currentSearchMatchIndex);
            int len = txtSearch.getText().length();

            codeEditor.getHighlighter().removeAllHighlights();
            try {
                Highlighter.HighlightPainter softPainter = new DefaultHighlighter.DefaultHighlightPainter(new Color(254, 240, 138));
                for (int pos : searchMatchOffsets) {
                    codeEditor.getHighlighter().addHighlight(pos, pos + len, softPainter);
                }

                Highlighter.HighlightPainter activePainter = new DefaultHighlighter.DefaultHighlightPainter(new Color(251, 146, 60));
                codeEditor.getHighlighter().addHighlight(offset, offset + len, activePainter);

                codeEditor.setCaretPosition(offset + len);
                lblSearchCount.setText((currentSearchMatchIndex + 1) + " of " + searchMatchOffsets.size());
            } catch (Exception ignored) {}
        }

        private void copyCodeToClipboard() {
            String text = codeEditor.getText();
            if (text != null && !text.isEmpty()) {
                Toolkit.getDefaultToolkit().getSystemClipboard().setContents(new StringSelection(text), null);
                btnCopy.setText("Copied!");
                new javax.swing.Timer(1500, e -> btnCopy.setText("Copy")).start();
            }
        }

        private String formatSize(long bytes) {
            if (bytes < 1024) return bytes + " B";
            if (bytes < 1024 * 1024) return String.format("%.1f KB", bytes / 1024.0);
            return String.format("%.2f MB", bytes / (1024.0 * 1024.0));
        }
    }

    // =========================================================================
    // Line Number Gutter Component
    // =========================================================================

    public static class TextLineNumber extends JComponent implements DocumentListener {
        private final JTextComponent component;
        private int lastDigits = 0;

        public TextLineNumber(JTextComponent component) {
            this.component = component;
            setFont(new Font("Consolas", Font.PLAIN, 12));
            component.getDocument().addDocumentListener(this);
            setBorder(BorderFactory.createCompoundBorder(
                    BorderFactory.createMatteBorder(0, 0, 0, 1, new Color(226, 232, 240)),
                    new EmptyBorder(4, 8, 4, 8)
            ));
            setBackground(new Color(248, 250, 252));
            setOpaque(true);
        }

        private void setPreferredWidth() {
            Element root = component.getDocument().getDefaultRootElement();
            int lines = root.getElementCount();
            int digits = Math.max(String.valueOf(lines).length(), 3);

            if (lastDigits != digits) {
                lastDigits = digits;
                FontMetrics fontMetrics = getFontMetrics(getFont());
                int width = fontMetrics.charWidth('0') * digits + 18;
                Dimension d = getPreferredSize();
                d.setSize(width, 1000000);
                setPreferredSize(d);
                setSize(d);
            }
        }

        @Override
        public void paintComponent(Graphics g) {
            super.paintComponent(g);
            FontMetrics fm = component.getFontMetrics(component.getFont());
            Insets insets = getInsets();
            int availableWidth = getWidth() - insets.right;

            Rectangle clip = g.getClipBounds();
            int rowStartOffset = component.viewToModel2D(new Point(0, clip.y));
            int endOffset = component.viewToModel2D(new Point(0, clip.y + clip.height));

            Element root = component.getDocument().getDefaultRootElement();
            int startRow = root.getElementIndex(rowStartOffset);
            int endRow = root.getElementIndex(endOffset);

            g.setColor(new Color(148, 163, 184));
            for (int i = startRow; i <= endRow; i++) {
                Element line = root.getElement(i);
                try {
                    Rectangle r = component.modelToView2D(line.getStartOffset()).getBounds();
                    String text = String.valueOf(i + 1);
                    int strWidth = fm.stringWidth(text);
                    int x = availableWidth - strWidth - 2;
                    int y = r.y + r.height - fm.getDescent();
                    g.drawString(text, x, y);
                } catch (Exception ignored) {}
            }
        }

        public void insertUpdate(DocumentEvent e) { documentChanged(); }
        public void removeUpdate(DocumentEvent e) { documentChanged(); }
        public void changedUpdate(DocumentEvent e) { documentChanged(); }

        private void documentChanged() {
            SwingUtilities.invokeLater(() -> {
                setPreferredWidth();
                repaint();
            });
        }
    }

    // =========================================================================
    // Modern Java Syntax Highlighter (GitHub / VS Code Light Palette)
    // =========================================================================

    public static class JavaSyntaxHighlighter {
        private static final Set<String> KEYWORDS = new HashSet<>(Arrays.asList(
                "abstract", "assert", "boolean", "break", "byte", "case", "catch", "char", "class",
                "const", "continue", "default", "do", "double", "else", "enum", "extends", "final",
                "finally", "float", "for", "goto", "if", "implements", "import", "instanceof", "int",
                "interface", "long", "native", "new", "non-sealed", "package", "permits", "private",
                "protected", "public", "record", "return", "sealed", "short", "static", "strictfp",
                "super", "switch", "synchronized", "this", "throw", "throws", "transient", "try",
                "void", "volatile", "while", "yield", "var", "true", "false", "null"
        ));

        private static final Pattern TOKEN_PATTERN = Pattern.compile(
                "(//[^\n]*)|(/\\*.*?\\*/)|(\"(?:\\\\.|[^\"\\\\])*\")|('(?:\\\\.|[^'\\\\])*')|(@[A-Za-z0-9_]+)|(\\b[A-Za-z_][A-Za-z0-9_]*\\b)|(\\b\\d+(\\.\\d+)?[fFdDlL]?\\b)",
                Pattern.DOTALL
        );

        public static void highlight(JTextPane textPane, String text) {
            StyledDocument doc = textPane.getStyledDocument();

            Style defaultStyle = textPane.addStyle("default", null);
            StyleConstants.setForeground(defaultStyle, new Color(36, 41, 47));
            StyleConstants.setFontFamily(defaultStyle, "Consolas");
            StyleConstants.setFontSize(defaultStyle, 13);

            try {
                doc.remove(0, doc.getLength());
                doc.insertString(0, text, defaultStyle);
                applyStyles(textPane, text);
            } catch (BadLocationException ignored) {}
        }

        public static void applyStyles(JTextPane textPane, String text) {
            StyledDocument doc = textPane.getStyledDocument();

            Style defaultStyle = textPane.getStyle("default");
            if (defaultStyle == null) {
                defaultStyle = textPane.addStyle("default", null);
                StyleConstants.setForeground(defaultStyle, new Color(36, 41, 47));
                StyleConstants.setFontFamily(defaultStyle, "Consolas");
                StyleConstants.setFontSize(defaultStyle, 13);
            }

            Style keywordStyle = textPane.getStyle("keyword");
            if (keywordStyle == null) keywordStyle = textPane.addStyle("keyword", defaultStyle);
            StyleConstants.setForeground(keywordStyle, new Color(207, 34, 46));
            StyleConstants.setBold(keywordStyle, true);

            Style stringStyle = textPane.getStyle("string");
            if (stringStyle == null) stringStyle = textPane.addStyle("string", defaultStyle);
            StyleConstants.setForeground(stringStyle, new Color(10, 48, 105));

            Style commentStyle = textPane.getStyle("comment");
            if (commentStyle == null) commentStyle = textPane.addStyle("comment", defaultStyle);
            StyleConstants.setForeground(commentStyle, new Color(110, 119, 129));
            StyleConstants.setItalic(commentStyle, true);

            Style annotationStyle = textPane.getStyle("annotation");
            if (annotationStyle == null) annotationStyle = textPane.addStyle("annotation", defaultStyle);
            StyleConstants.setForeground(annotationStyle, new Color(130, 80, 223));

            Style numberStyle = textPane.getStyle("number");
            if (numberStyle == null) numberStyle = textPane.addStyle("number", defaultStyle);
            StyleConstants.setForeground(numberStyle, new Color(5, 80, 174));

            Matcher matcher = TOKEN_PATTERN.matcher(text);
            while (matcher.find()) {
                int start = matcher.start();
                int len = matcher.end() - start;

                if (matcher.group(1) != null || matcher.group(2) != null) {
                    doc.setCharacterAttributes(start, len, commentStyle, true);
                } else if (matcher.group(3) != null || matcher.group(4) != null) {
                    doc.setCharacterAttributes(start, len, stringStyle, true);
                } else if (matcher.group(5) != null) {
                    doc.setCharacterAttributes(start, len, annotationStyle, true);
                } else if (matcher.group(6) != null) {
                    String word = matcher.group(6);
                    if (KEYWORDS.contains(word)) {
                        doc.setCharacterAttributes(start, len, keywordStyle, true);
                    }
                } else if (matcher.group(7) != null) {
                    doc.setCharacterAttributes(start, len, numberStyle, true);
                }
            }
        }
    }
}
