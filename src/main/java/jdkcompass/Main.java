package jdkcompass;

import java.io.IOException;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.regex.Pattern;

/** Read-only checks for differences between a project's Java requirement and its environment. */
public final class Main {
    public static final String VERSION = "0.4.0";
    private static final int MAX_FILE_BYTES = 65_536;
    private static final List<String> VERSION_FILES = List.of(".java-version", ".sdkmanrc", ".tool-versions");
    private static final Pattern VERSION_TOKEN = Pattern.compile(
            "(?:[A-Za-z][A-Za-z0-9]*-)?(1\\.[0-9]+|[0-9]+)(?:[._+\\-][A-Za-z0-9._+\\-]+)?");

    private Main() { }

    public record Finding(String code, String severity, String message, String suggestion) { }
    public record Environment(Path runtimeHome, int runtimeMajor, Map<String, String> variables,
                              boolean windows, String runtimeArchitecture) { }
    public record Options(Path project, Path javaHome, Integer expected, boolean json, boolean strict,
                          boolean markdown, boolean searchParents) { }
    private record Requirement(String source, int major) { }

    public static void main(String[] args) {
        var environment = new Environment(Path.of(System.getProperty("java.home")),
                Runtime.version().feature(), System.getenv(),
                System.getProperty("os.name").startsWith("Windows"), System.getProperty("os.arch", ""));
        System.exit(run(args, environment, System.out, System.err));
    }

    static int run(String[] args, Environment environment, PrintStream out, PrintStream err) {
        try {
            if (args.length == 1 && (args[0].equals("--help") || args[0].equals("-h"))) {
                out.println("""
                        JDK Compass — read-only Java environment diagnostics
                        Usage: java -jar jdk-compass.jar [options]
                          --project PATH    Project directory (default: current directory)
                          --search-parents  Find nearest version files up to the Git or filesystem root
                          --java-home PATH  Inspect this JDK instead of JAVA_HOME
                          --expect MAJOR    Explicit required Java major; overrides project files
                          --json            Emit schema-versioned JSON
                          --markdown        Emit a Markdown report (cannot combine with --json)
                          --strict          Return failure for warnings as well as errors
                          --version         Print version
                          --help            Print this help
                        Auto-detection: .java-version, .sdkmanrc, .tool-versions (selected directory by default).
                        Exit codes: 0 = no errors; 1 = findings failed policy; 2 = invalid arguments.
                        The tool does not run project scripts or change any configuration.
                        """);
                return 0;
            }
            if (args.length == 1 && args[0].equals("--version")) {
                out.println(VERSION);
                return 0;
            }
            Options options = parseOptions(args);
            List<Finding> findings = inspect(options, environment);
            int exit = findings.stream().anyMatch(f -> f.severity().equals("ERROR")
                    || options.strict() && f.severity().equals("WARN")) ? 1 : 0;
            if (options.json()) {
                out.println(json(findings, exit));
            } else if (options.markdown()) {
                out.print(markdown(findings, exit));
            } else {
                out.println("JDK Compass " + VERSION);
                for (Finding finding : findings) {
                    out.printf("[%s] %s: %s%n", finding.severity(), finding.code(), finding.message());
                    if (!finding.suggestion().isBlank()) out.println("  " + finding.suggestion());
                }
                out.println("Result: " + (exit == 0 ? "PASS" : "FAIL"));
            }
            return exit;
        } catch (IllegalArgumentException e) {
            err.println("Invalid arguments: " + e.getMessage() + ". Use --help.");
            return 2;
        }
    }

    static Options parseOptions(String[] args) {
        Path project = Path.of(".");
        Path home = null;
        Integer expected = null;
        boolean json = false;
        boolean strict = false;
        boolean markdown = false;
        boolean searchParents = false;
        for (int i = 0; i < args.length; i++) {
            switch (args[i]) {
                case "--json" -> json = true;
                case "--strict" -> strict = true;
                case "--markdown" -> markdown = true;
                case "--search-parents" -> searchParents = true;
                case "--project", "--java-home", "--expect" -> {
                    String flag = args[i];
                    if (++i == args.length || args[i].startsWith("--")) {
                        throw new IllegalArgumentException("missing value for " + flag);
                    }
                    if (flag.equals("--project")) project = Path.of(args[i]);
                    if (flag.equals("--java-home")) home = Path.of(args[i]);
                    if (flag.equals("--expect")) {
                        if (!args[i].matches("[0-9]+")) {
                            throw new IllegalArgumentException("--expect must be an integer from 1 to 999");
                        }
                        expected = Integer.valueOf(args[i]);
                        if (expected < 1 || expected > 999) {
                            throw new IllegalArgumentException("--expect must be from 1 to 999");
                        }
                    }
                }
                default -> throw new IllegalArgumentException("unknown option " + args[i]);
            }
        }
        if (json && markdown) throw new IllegalArgumentException("--json and --markdown cannot be combined");
        return new Options(project.toAbsolutePath().normalize(), home, expected, json, strict, markdown, searchParents);
    }

    static List<Finding> inspect(Options options, Environment environment) {
        List<Finding> results = new ArrayList<>();
        if (!Files.isDirectory(options.project())) {
            results.add(new Finding("PROJECT_NOT_FOUND", "ERROR", "Project directory is unavailable.",
                    "Pass an existing directory with --project."));
            return results;
        }
        List<Requirement> requirements = new ArrayList<>();
        if (options.expected() != null) {
            requirements.add(new Requirement("--expect", options.expected()));
        } else {
            detectRequirements(options.project(), options.searchParents(), requirements, results);
        }
        Integer expected = null;
        if (requirements.isEmpty()) {
            results.add(new Finding("REQUIREMENT_UNKNOWN", "WARN", "No supported Java requirement was found.",
                    "Add a supported version file or pass --expect MAJOR; pom.xml and Gradle are not parsed yet."));
        } else if (requirements.stream().map(Requirement::major).distinct().count() > 1) {
            results.add(new Finding("REQUIREMENT_CONFLICT", "ERROR", "Project version files disagree: "
                    + requirements, "Align version files, or use --expect for a deliberate override."));
        } else {
            expected = requirements.get(0).major();
            results.add(new Finding("REQUIREMENT", "INFO", "Required Java major: " + expected
                    + "; source: " + requirements.stream().map(Requirement::source).toList(), ""));
        }
        results.add(new Finding("RUNTIME", "INFO", "This diagnostic process runs on Java "
                + environment.runtimeMajor() + ".", "This is not necessarily the JDK selected inside your IDE."));
        if (expected != null && environment.runtimeMajor() != expected) {
            results.add(new Finding("RUNTIME_VERSION_MISMATCH", "ERROR", "Running Java "
                    + environment.runtimeMajor() + " differs from required Java " + expected + ".",
                    "Use the intended JDK to launch the tool and align your build tool's JDK setting."));
        }
        Path selectedHome = options.javaHome();
        String homeLabel = "--java-home";
        if (selectedHome == null) {
            homeLabel = "JAVA_HOME";
            String raw = environment.variables().get("JAVA_HOME");
            if (raw == null || raw.isBlank()) {
                results.add(new Finding("JAVA_HOME_UNSET", "WARN", "JAVA_HOME is not set.",
                        "Some build tools use JAVA_HOME. Set it if your workflow requires it."));
            } else {
                try { selectedHome = Path.of(raw); }
                catch (InvalidPathException e) {
                    results.add(new Finding("JAVA_HOME_INVALID", "ERROR", "JAVA_HOME contains an invalid path.",
                            "Point it at a JDK home directory."));
                }
            }
        }
        if (selectedHome != null) inspectHome(selectedHome, homeLabel, expected, environment, results);
        for (String name : List.of("JAVA_TOOL_OPTIONS", "JDK_JAVA_OPTIONS", "_JAVA_OPTIONS")) {
            String value = environment.variables().get(name);
            if (value != null && !value.isBlank()) {
                results.add(new Finding("JVM_OPTIONS_PRESENT", "WARN", name + " is set; its value is not reported.",
                        "It can alter JVM startup. Review it locally if results differ across machines."));
            }
        }
        return results;
    }

    private static void inspectHome(Path home, String label, Integer expected,
                                    Environment environment, List<Finding> results) {
        if (!home.isAbsolute()) {
            results.add(new Finding("JDK_HOME_RELATIVE", "ERROR", label + " is a relative path.",
                    "Use an absolute JDK home path."));
            return;
        }
        String suffix = environment.windows() ? ".exe" : "";
        if (!Files.isDirectory(home)) {
            results.add(new Finding("JDK_HOME_NOT_FOUND", "ERROR", label + " does not identify an accessible directory.",
                    "On macOS, a JDK bundle's home is usually Contents/Home."));
            return;
        }
        for (String command : List.of("java", "javac")) {
            Path executable = home.resolve("bin").resolve(command + suffix);
            if (!Files.isRegularFile(executable)
                    || (!environment.windows() && !Files.isExecutable(executable))) {
                results.add(new Finding("JDK_" + command.toUpperCase(java.util.Locale.ROOT) + "_MISSING", "ERROR",
                        label + " has no usable bin/" + command + suffix + ".", "Select a full JDK, not a JRE or bundle root."));
            }
        }
        try {
            Properties release = new Properties();
            release.load(new java.io.StringReader(readSmall(home.resolve("release"))));
            String raw = release.getProperty("JAVA_VERSION", "").replace("\"", "");
            int major = parseMajor(raw);
            results.add(new Finding("JDK_HOME_VERSION", "INFO", label + " declares Java " + major + ".", ""));
            if (expected != null && major != expected) {
                results.add(new Finding("JDK_HOME_VERSION_MISMATCH", "ERROR", label + " declares Java "
                        + major + " but the project requires " + expected + ".", "Choose the project's required JDK."));
            }
            inspectArchitecture(release.getProperty("OS_ARCH", ""), label, environment, results);
            if (!Files.isSameFile(home, environment.runtimeHome())) {
                results.add(new Finding("JDK_HOME_DIFFERS_FROM_RUNTIME", "WARN", label
                        + " differs from the JDK running this tool.", "Align the selected JDK and the Java executable used to launch this tool."));
            }
        } catch (IOException | IllegalArgumentException e) {
            results.add(new Finding("JDK_RELEASE_UNREADABLE", "ERROR", "Cannot read a valid JAVA_VERSION from the selected JDK's release file.",
                    "Check JDK installation completeness and file permissions."));
        }
    }

    private static void inspectArchitecture(String declared, String label,
                                            Environment environment, List<Finding> results) {
        String selected = normalizeArchitecture(declared);
        String runtime = normalizeArchitecture(environment.runtimeArchitecture());
        if (selected == null || runtime == null) return;
        results.add(new Finding("JDK_ARCHITECTURE", "INFO", label + " declares " + selected
                + "; this Java process reports " + runtime + ".", ""));
        if (!selected.equals(runtime)) {
            results.add(new Finding("JDK_ARCHITECTURE_MISMATCH", "WARN", label + " architecture "
                    + selected + " differs from the running Java process architecture " + runtime + ".",
                    "Use matching JDK architectures, or confirm that cross-architecture execution is intentional."
                    + " This comparison does not detect physical hardware or emulation."));
        }
    }

    private static String normalizeArchitecture(String value) {
        if (value == null) return null;
        String normalized = value.trim();
        if (normalized.length() >= 2 && normalized.startsWith("\"") && normalized.endsWith("\"")) {
            normalized = normalized.substring(1, normalized.length() - 1).trim();
        }
        return switch (normalized.toLowerCase(java.util.Locale.ROOT)) {
            case "amd64", "x86_64", "x64" -> "x86_64";
            case "aarch64", "arm64" -> "arm64";
            case "x86", "i386", "i486", "i586", "i686" -> "x86";
            default -> null;
        };
    }

    private static void detectRequirements(Path project, boolean searchParents,
                                           List<Requirement> requirements, List<Finding> results) {
        Path directory = project;
        String prefix = "";
        while (searchParents && !hasVersionFile(directory)
                && !Files.exists(directory.resolve(".git"), java.nio.file.LinkOption.NOFOLLOW_LINKS)
                && directory.getParent() != null) {
            directory = directory.getParent();
            prefix += "../";
        }
        for (String name : VERSION_FILES) {
            Path file = directory.resolve(name);
            if (!Files.exists(file, java.nio.file.LinkOption.NOFOLLOW_LINKS)) continue;
            try {
                String text = readSmall(file);
                List<String> declarations = new ArrayList<>();
                for (String raw : text.split("\\R")) {
                    String line = raw.split("#", 2)[0].trim();
                    if (line.isEmpty()) continue;
                    if (name.equals(".java-version")) declarations.add(line);
                    if (name.equals(".sdkmanrc") && line.matches("java\\s*=.*")) {
                        declarations.add(line.substring(line.indexOf('=') + 1).trim());
                    }
                    if (name.equals(".tool-versions") && line.matches("java(?:\\s.*)?")) {
                        declarations.add(line.substring(4).trim());
                    }
                }
                if (declarations.isEmpty() && !name.equals(".java-version")) continue;
                if (declarations.size() != 1) throw new IllegalArgumentException("ambiguous version declaration");
                requirements.add(new Requirement(prefix + name, parseMajor(declarations.get(0))));
            } catch (IOException | IllegalArgumentException e) {
                results.add(new Finding("VERSION_FILE_INVALID", "ERROR", "Cannot interpret " + prefix + name + ".",
                        "Use one concrete Java version; aliases and multiple fallback versions are unsupported. Files must be at most 64 KiB."));
            }
        }
    }

    private static boolean hasVersionFile(Path directory) {
        return VERSION_FILES.stream().anyMatch(name ->
                Files.exists(directory.resolve(name), java.nio.file.LinkOption.NOFOLLOW_LINKS));
    }

    static int parseMajor(String value) {
        var matcher = VERSION_TOKEN.matcher(value.trim());
        if (!matcher.matches()) throw new IllegalArgumentException("unsupported Java version");
        String number = matcher.group(1);
        int major = Integer.parseInt(number.startsWith("1.") ? number.substring(2) : number);
        if (major < 1 || major > 999) throw new IllegalArgumentException("Java major out of range");
        return major;
    }

    private static String readSmall(Path path) throws IOException {
        if (!Files.isRegularFile(path)) throw new IOException("not a regular file");
        try (var input = Files.newInputStream(path)) {
            byte[] bytes = input.readNBytes(MAX_FILE_BYTES + 1);
            if (bytes.length > MAX_FILE_BYTES) throw new IOException("file too large");
            return new String(bytes, StandardCharsets.UTF_8);
        }
    }

    static String markdown(List<Finding> findings, int exit) {
        StringBuilder result = new StringBuilder("## JDK Compass ").append(VERSION)
                .append("\n\nResult: **").append(exit == 0 ? "PASS" : "FAIL").append("**\n\n")
                .append("| Severity | Code | Message | Suggestion |\n")
                .append("| --- | --- | --- | --- |\n");
        for (Finding finding : findings) {
            result.append("| ").append(markdownCell(finding.severity()))
                    .append(" | ").append(markdownCell(finding.code()))
                    .append(" | ").append(markdownCell(finding.message()))
                    .append(" | ").append(markdownCell(finding.suggestion())).append(" |\n");
        }
        return result.toString();
    }

    private static String markdownCell(String text) {
        StringBuilder result = new StringBuilder();
        String normalized = text.replace("\r\n", "\n").replace('\r', '\n');
        for (int i = 0; i < normalized.length(); i++) {
            char c = normalized.charAt(i);
            switch (c) {
                case '&' -> result.append("&amp;");
                case '<' -> result.append("&lt;");
                case '>' -> result.append("&gt;");
                case '\n' -> result.append("<br>");
                default -> {
                    if ("\\|`*_[]".indexOf(c) >= 0) result.append('\\');
                    result.append(c);
                }
            }
        }
        return result.toString();
    }

    static String json(List<Finding> findings, int exit) {
        StringBuilder result = new StringBuilder("{\"schemaVersion\":1,\"toolVersion\":")
                .append(quote(VERSION)).append(",\"exitCode\":").append(exit).append(",\"findings\":[");
        for (int i = 0; i < findings.size(); i++) {
            if (i > 0) result.append(',');
            Finding f = findings.get(i);
            result.append("{\"code\":").append(quote(f.code())).append(",\"severity\":").append(quote(f.severity()))
                    .append(",\"message\":").append(quote(f.message())).append(",\"suggestion\":")
                    .append(quote(f.suggestion())).append('}');
        }
        return result.append("]}").toString();
    }

    static String quote(String text) {
        StringBuilder result = new StringBuilder("\"");
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c == '"' || c == '\\') result.append('\\').append(c);
            else if (c < 32 || Character.isSurrogate(c)) result.append(String.format("\\u%04x", (int) c));
            else result.append(c);
        }
        return result.append('"').toString();
    }
}
