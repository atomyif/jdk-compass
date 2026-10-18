package jdkcompass;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

/** Behavior tests with isolated fixtures; no dependency downloads or external commands. */
public final class MainTest {
    private static int passed;
    private static final boolean WINDOWS = System.getProperty("os.name").startsWith("Windows");
    private MainTest() { }
    @FunctionalInterface private interface Scenario { void run(Path root) throws Exception; }
    private record Result(int exit, String out, String err) { }

    public static void main(String[] args) throws Exception {
        test("matching environment", root -> {
            Result r = run(root, "21", Map.of(), "--expect", "21");
            check(r.exit == 0 && !r.out.contains("WARN") && !r.out.contains("ERROR"), r.out);
        });
        test("runtime mismatch", root -> {
            Result r = run(root, "21", Map.of(), "--expect", "17");
            check(r.exit == 1 && r.out.contains("RUNTIME_VERSION_MISMATCH"), r.out);
        });
        test("missing project", root -> {
            Result r = run(root, "21", Map.of(), "--project", root.resolve("missing").toString());
            check(r.exit == 1 && r.out.contains("PROJECT_NOT_FOUND"), r.out);
        });
        test("java-version comments and legacy Java 8", root -> {
            Files.writeString(root.resolve(".java-version"), "# legacy project\n1.8.0_452 # pinned\n");
            Result r = run(root, "1.8.0_452", Map.of());
            check(r.out.contains("Required Java major: 8") && !r.out.contains("VERSION_FILE_INVALID"), r.out);
        });
        test("SDKMAN vendor version", root -> {
            Files.writeString(root.resolve(".sdkmanrc"), "maven=3.9.9\njava=21.0.5-tem\n");
            check(run(root, "21", Map.of()).exit == 0, "SDKMAN requirement should match");
        });
        test("asdf vendor version", root -> {
            Files.writeString(root.resolve(".tool-versions"), "nodejs 22\njava temurin-21.0.5+11\n");
            check(run(root, "21", Map.of()).exit == 0, "asdf requirement should match");
        });
        test("conflicting requirements", root -> {
            Files.writeString(root.resolve(".java-version"), "21\n");
            Files.writeString(root.resolve(".sdkmanrc"), "java=17.0.10-tem\n");
            Result r = run(root, "21", Map.of());
            check(r.exit == 1 && r.out.contains("REQUIREMENT_CONFLICT"), r.out);
        });
        test("explicit requirement overrides files", root -> {
            Files.writeString(root.resolve(".java-version"), "latest\n");
            check(run(root, "21", Map.of(), "--expect", "21").exit == 0, "explicit override should work");
        });
        test("parent discovery is opt-in and supports all version files", root -> {
            Path child = Files.createDirectories(root.resolve("module/src"));
            for (var entry : Map.of(".java-version", "21\n", ".sdkmanrc", "java=21.0.5-tem\n",
                    ".tool-versions", "java temurin-21.0.5\n").entrySet()) {
                Path version = root.resolve(entry.getKey());
                Files.writeString(version, entry.getValue());
                check(run(root, "21", Map.of(), "--project", child.toString()).out.contains("REQUIREMENT_UNKNOWN"),
                        "default must not search parents");
                Result r = run(root, "21", Map.of(), "--project", child.toString(), "--search-parents", "--strict");
                check(r.exit == 0 && r.out.contains("../../" + entry.getKey()), r.out);
                check(Files.readString(version).equals(entry.getValue()), "parent file modified");
                Files.delete(version);
            }
        });
        test("nearest configuration directory wins without merging ancestors", root -> {
            Files.writeString(root.resolve(".java-version"), "17\n");
            Path module = Files.createDirectories(root.resolve("module"));
            Files.writeString(module.resolve(".sdkmanrc"), "java=21\n");
            Path child = Files.createDirectories(module.resolve("src"));
            Result r = run(root, "21", Map.of(), "--project", child.toString(), "--search-parents", "--strict");
            check(r.exit == 0 && r.out.contains("../.sdkmanrc") && !r.out.contains(".java-version"), r.out);
            Files.writeString(child.resolve(".java-version"), "21\n");
            Result local = run(root, "21", Map.of(), "--project", child.toString(), "--search-parents", "--strict");
            check(local.exit == 0 && local.out.contains("source: [.java-version]") && !local.out.contains(".sdkmanrc"), local.out);
        });
        test("parent conflicts and invalid nearest files are not hidden", root -> {
            Files.writeString(root.resolve(".java-version"), "21\n");
            Files.writeString(root.resolve(".sdkmanrc"), "java=17\n");
            Path child = Files.createDirectories(root.resolve("src"));
            Result conflict = run(root, "21", Map.of(), "--project", child.toString(), "--search-parents");
            check(conflict.exit == 1 && conflict.out.contains("REQUIREMENT_CONFLICT")
                    && conflict.out.contains("../.sdkmanrc"), conflict.out);
            Files.writeString(child.resolve(".java-version"), "latest\n");
            Result invalid = run(root, "21", Map.of(), "--project", child.toString(), "--search-parents");
            check(invalid.exit == 1 && invalid.out.contains("VERSION_FILE_INVALID")
                    && !invalid.out.contains("../.sdkmanrc"), invalid.out);
            Result override = run(root, "21", Map.of(), "--project", child.toString(),
                    "--search-parents", "--expect", "21", "--strict");
            check(override.exit == 0 && override.out.contains("source: [--expect]"), override.out);
        });
        test("Git directory and worktree marker stop parent traversal", root -> {
            Files.writeString(root.resolve(".java-version"), "17\n");
            for (boolean worktree : List.of(false, true)) {
                Path repo = Files.createDirectories(root.resolve(worktree ? "worktree" : "repository"));
                if (worktree) Files.writeString(repo.resolve(".git"), "gitdir: unused-fixture\n");
                else Files.createDirectory(repo.resolve(".git"));
                Path child = Files.createDirectories(repo.resolve("src"));
                Result absent = run(root, "21", Map.of(), "--project", child.toString(), "--search-parents", "--strict");
                check(absent.exit == 1 && absent.out.contains("REQUIREMENT_UNKNOWN")
                        && !absent.out.contains("RUNTIME_VERSION_MISMATCH"), absent.out);
                Files.writeString(repo.resolve(".java-version"), "21\n");
                Result present = run(root, "21", Map.of(), "--project", child.toString(), "--search-parents", "--strict");
                check(present.exit == 0 && present.out.contains("../.java-version"), present.out);
            }
        });
        test("nearest supported filename without Java stops discovery", root -> {
            Files.writeString(root.resolve(".java-version"), "21\n");
            Path child = Files.createDirectories(root.resolve("src"));
            Files.writeString(child.resolve(".tool-versions"), "nodejs 22\n");
            Result r = run(root, "21", Map.of(), "--project", child.toString(), "--search-parents", "--strict");
            check(r.exit == 1 && r.out.contains("REQUIREMENT_UNKNOWN") && !r.out.contains("../.java-version"), r.out);
        });
        test("parent discovery keeps JSON and Markdown paths relative", root -> {
            Files.writeString(root.resolve(".java-version"), "21\n");
            Path child = Files.createDirectories(root.resolve("src"));
            for (String format : List.of("--json", "--markdown")) {
                Result r = run(root, "21", Map.of(), "--project", child.toString(), "--search-parents", format);
                check(r.exit == 0 && r.out.contains("../.java-version") && !r.out.contains(root.getFileName().toString()), r.out);
            }
        });
        test("non-regular nearest version path blocks fallback", root -> {
            Files.writeString(root.resolve(".java-version"), "21\n");
            Path child = Files.createDirectories(root.resolve("src"));
            Files.createDirectory(child.resolve(".java-version"));
            Result r = run(root, "21", Map.of(), "--project", child.toString(), "--search-parents");
            check(r.exit == 1 && r.out.contains("VERSION_FILE_INVALID") && !r.out.contains("../.java-version"), r.out);
        });
        test("invalid or ambiguous declarations", root -> {
            for (String value : List.of("", "latest", "21\n17\n", "21 17", "0", "999999999999999999")) {
                Files.writeString(root.resolve(".java-version"), value);
                Result r = run(root, "21", Map.of());
                check(r.exit == 1 && r.out.contains("VERSION_FILE_INVALID"), "accepted invalid requirement: " + value);
            }
        });
        test("multiple asdf fallback versions rejected", root -> {
            Files.writeString(root.resolve(".tool-versions"), "java temurin-21.0.5 temurin-17.0.10\n");
            check(run(root, "21", Map.of()).out.contains("VERSION_FILE_INVALID"), "ambiguous asdf versions");
        });
        test("oversized version file", root -> {
            Files.writeString(root.resolve(".java-version"), "2".repeat(65_537));
            check(run(root, "21", Map.of()).out.contains("VERSION_FILE_INVALID"), "size limit");
        });
        test("non-regular version file", root -> {
            Files.createDirectory(root.resolve(".java-version"));
            check(run(root, "21", Map.of()).exit == 1, "directory must not be accepted as a version file");
        });
        test("warnings fail only in strict mode", root -> {
            check(run(root, "21", Map.of()).exit == 0, "default tolerates unknown requirement");
            check(run(root, "21", Map.of(), "--strict").exit == 1, "strict rejects unknown requirement");
        });
        test("relative JAVA_HOME", root -> {
            Result r = run(root, "21", Map.of("JAVA_HOME", "relative/jdk"), "--expect", "21");
            check(r.exit == 1 && r.out.contains("JDK_HOME_RELATIVE"), r.out);
        });
        test("missing JAVA_HOME path", root -> {
            Result r = run(root, "21", Map.of("JAVA_HOME", root.resolve("missing").toString()), "--expect", "21");
            check(r.exit == 1 && r.out.contains("JDK_HOME_NOT_FOUND"), r.out);
        });
        test("empty JAVA_HOME is a warning", root -> {
            Result r = run(root, "21", Map.of("JAVA_HOME", ""), "--expect", "21");
            check(r.exit == 0 && r.out.contains("JAVA_HOME_UNSET"), r.out);
        });
        test("missing compiler", root -> {
            Path jdk = fakeJdk(root.resolve("jdk"), "21");
            Files.delete(jdk.resolve("bin/javac" + (WINDOWS ? ".exe" : "")));
            Result r = invoke(root, jdk, Map.of("JAVA_HOME", jdk.toString()), "--expect", "21");
            check(r.exit == 1 && r.out.contains("JDK_JAVAC_MISSING"), r.out);
        });
        test("invalid release metadata", root -> {
            Path jdk = fakeJdk(root.resolve("jdk"), "not-a-version");
            Result r = invoke(root, jdk, Map.of("JAVA_HOME", jdk.toString()), "--expect", "21");
            check(r.exit == 1 && r.out.contains("JDK_RELEASE_UNREADABLE"), r.out);
        });
        test("architecture aliases match without warnings", root -> {
            for (String[] pair : List.of(new String[]{"amd64", "x86_64"},
                    new String[]{"x64", "AMD64"}, new String[]{"aarch64", "arm64"},
                    new String[]{" ARM64 ", " AARCH64 "}, new String[]{"i386", "i686"},
                    new String[]{"x86", "i586"})) {
                Result r = architectureReport(root, pair[0], pair[1], "--strict");
                check(r.exit == 0 && r.out.contains("JDK_ARCHITECTURE")
                        && !r.out.contains("MISMATCH"), r.out);
            }
        });
        test("architecture mismatch warns and fails strict mode", root -> {
            for (String[] pair : List.of(new String[]{"arm64", "amd64"},
                    new String[]{"amd64", "aarch64"}, new String[]{"x86", "amd64"})) {
                Result ordinary = architectureReport(root, pair[0], pair[1]);
                check(ordinary.exit == 0 && ordinary.out.contains("[WARN] JDK_ARCHITECTURE_MISMATCH"), ordinary.out);
                Result strict = architectureReport(root, pair[0], pair[1], "--strict", "--json");
                check(strict.exit == 1 && strict.out.contains("\"code\":\"JDK_ARCHITECTURE_MISMATCH\",\"severity\":\"WARN\""), strict.out);
            }
        });
        test("missing or unsupported architecture does not invent a mismatch", root -> {
            for (String value : List.of("", "  ", "riscv64", "UNKNOWN_SECRET_SENTINEL", "arm64/amd64", "arm\\\"64")) {
                Result selected = architectureReport(root, value, "amd64", "--strict", "--json");
                check(selected.exit == 0 && !selected.out.contains("JDK_ARCHITECTURE")
                        && !selected.out.contains("SECRET_SENTINEL"), selected.out);
                Result runtime = architectureReport(root, "amd64", value, "--strict");
                check(runtime.exit == 0 && !runtime.out.contains("JDK_ARCHITECTURE")
                        && !runtime.out.contains("SECRET_SENTINEL"), runtime.out);
            }
            Result absent = run(root, "21", Map.of(), "--expect", "21", "--strict");
            check(absent.exit == 0 && !absent.out.contains("JDK_ARCHITECTURE"), absent.out);
        });
        test("architecture comparison does not depend on project requirement", root -> {
            Path jdk = fakeJdk(root.resolve("jdk"), "21");
            Files.writeString(jdk.resolve("release"), "JAVA_VERSION=\"21\"\nOS_ARCH=\"arm64\"\n");
            Result r = invokeArchitecture(root, jdk, Map.of("JAVA_HOME", jdk.toString()), "amd64");
            check(r.exit == 0 && r.out.contains("REQUIREMENT_UNKNOWN")
                    && r.out.contains("JDK_ARCHITECTURE_MISMATCH"), r.out);
        });
        test("explicit JDK architecture overrides JAVA_HOME", root -> {
            Path runtime = fakeJdk(root.resolve("runtime"), "21");
            Path other = fakeJdk(root.resolve("other"), "21");
            Files.writeString(runtime.resolve("release"), "JAVA_VERSION=\"21\"\nOS_ARCH=\"amd64\"\n");
            Files.writeString(other.resolve("release"), "JAVA_VERSION=\"21\"\nOS_ARCH=\"arm64\"\n");
            Result r = invokeArchitecture(root, runtime, Map.of("JAVA_HOME", other.toString()), "x86_64",
                    "--expect", "21", "--java-home", runtime.toString(), "--strict");
            check(r.exit == 0 && r.out.contains("--java-home declares x86_64")
                    && !r.out.contains("JDK_ARCHITECTURE_MISMATCH"), r.out);
        });
        test("Markdown includes architecture warning without raw metadata or paths", root -> {
            Result r = architectureReport(root, "arm64", "amd64", "--markdown");
            check(r.exit == 0 && r.out.contains("JDK\\_ARCHITECTURE\\_MISMATCH")
                    && r.out.contains("does not detect physical hardware or emulation")
                    && !r.out.contains(root.getFileName().toString()), r.out);
        });
        test("selected home differs from runtime", root -> {
            Path runtime = fakeJdk(root.resolve("runtime"), "21");
            Path other = fakeJdk(root.resolve("other"), "21");
            Result r = invoke(root, runtime, Map.of("JAVA_HOME", other.toString()), "--expect", "21");
            check(r.exit == 0 && r.out.contains("JDK_HOME_DIFFERS_FROM_RUNTIME"), r.out);
        });
        test("explicit home overrides environment", root -> {
            Path jdk = fakeJdk(root.resolve("jdk"), "21");
            Result r = invoke(root, jdk, Map.of("JAVA_HOME", "invalid"), "--expect", "21", "--java-home", jdk.toString());
            check(r.exit == 0 && !r.out.contains("JDK_HOME_RELATIVE"), r.out);
        });
        test("environment secrets are not reported", root -> {
            Result r = run(root, "21", Map.of("JAVA_TOOL_OPTIONS", "-Dpassword=SECRET_SENTINEL"), "--expect", "21", "--json");
            check(r.out.contains("JVM_OPTIONS_PRESENT") && !r.out.contains("SECRET_SENTINEL"), r.out);
        });
        test("JSON protocol and escaping", root -> {
            Result r = run(root, "21", Map.of(), "--expect", "21", "--json");
            check(r.out.startsWith("{\"schemaVersion\":1,") && r.out.contains("\"exitCode\":0"), r.out);
            check(Main.quote("a\"\\\n\t").equals("\"a\\\"\\\\\\u000a\\u0009\""), "JSON escaping");
            check(Main.quote("\ud800").equals("\"\\ud800\""), "unpaired surrogate escaping");
        });
        test("invalid arguments", root -> {
            for (String[] args0 : List.of(new String[]{"--wat"}, new String[]{"--expect"},
                    new String[]{"--expect", "0"}, new String[]{"--expect", "latest"},
                    new String[]{"--project", "--json"}, new String[]{"--expect", "999999999999999"})) {
                Result r = run(root, "21", Map.of(), args0);
                check(r.exit == 2 && r.out.isEmpty() && r.err.contains("Invalid arguments"), r.toString());
            }
        });
        test("Markdown report includes diagnostics and suggestions", root -> {
            Result r = run(root, "21", Map.of(), "--expect", "21", "--markdown");
            check(r.exit == 0 && r.err.isEmpty() && r.out.startsWith("## JDK Compass " + Main.VERSION), r.toString());
            check(r.out.contains("Result: **PASS**")
                    && r.out.contains("| Severity | Code | Message | Suggestion |")
                    && r.out.contains("| INFO | REQUIREMENT | Required Java major: 21;")
                    && r.out.contains("| INFO | RUNTIME | This diagnostic process runs on Java 21.")
                    && r.out.contains("This is not necessarily the JDK selected inside your IDE."), r.out);
        });
        test("Markdown preserves error and strict warning exit codes", root -> {
            Result mismatch = run(root, "21", Map.of(), "--expect", "17", "--markdown");
            check(mismatch.exit == 1 && mismatch.out.contains("Result: **FAIL**")
                    && mismatch.out.contains("| ERROR | RUNTIME\\_VERSION\\_MISMATCH |"), mismatch.out);
            Result warning = run(root, "21", Map.of(), "--markdown");
            check(warning.exit == 0 && warning.out.contains("Result: **PASS**")
                    && warning.out.contains("| WARN | REQUIREMENT\\_UNKNOWN |"), warning.out);
            Result strict = run(root, "21", Map.of(), "--strict", "--markdown");
            check(strict.exit == 1 && strict.out.contains("Result: **FAIL**"), strict.out);
        });
        test("Markdown and JSON flags conflict in either order", root -> {
            for (String[] flags : List.of(new String[]{"--json", "--markdown"},
                    new String[]{"--markdown", "--json"})) {
                Result r = run(root, "21", Map.of(), flags);
                check(r.exit == 2 && r.out.isEmpty() && r.err.contains("cannot be combined"), r.toString());
            }
        });
        test("Markdown keeps environment values and paths private", root -> {
            Result r = run(root, "21", Map.of("JAVA_TOOL_OPTIONS", "-Dpassword=SECRET_SENTINEL"),
                    "--expect", "21", "--markdown");
            check(r.out.contains("JVM\\_OPTIONS\\_PRESENT") && !r.out.contains("SECRET_SENTINEL")
                    && !r.out.contains(root.toString()) && !r.out.contains(root.getFileName().toString()), r.out);
        });
        test("Markdown escapes table separators, markup and line breaks", root -> {
            String report = Main.markdown(List.of(new Main.Finding("TEST", "INFO",
                    "one|two\\three\r\nfour\rfive\nsix", "<b>& *bold* _em_ `code` [link](url)")), 0);
            check(report.contains("one\\|two\\\\three<br>four<br>five<br>six"), report);
            check(report.contains("&lt;b&gt;&amp; \\*bold\\* \\_em\\_ \\`code\\` \\[link\\](url)"), report);
            check(report.lines().count() == 7, "cell line breaks must not create table rows: " + report);
        });
        test("inspection does not modify project", root -> {
            Path file = root.resolve(".java-version");
            Files.writeString(file, "21\n");
            byte[] original = Files.readAllBytes(file);
            var time = Files.getLastModifiedTime(file);
            run(root, "21", Map.of());
            check(java.util.Arrays.equals(original, Files.readAllBytes(file))
                    && time.equals(Files.getLastModifiedTime(file)), "project file modified");
        });
        test("Windows executable names", root -> {
            Path jdk = root.resolve("win-jdk");
            Files.createDirectories(jdk.resolve("bin"));
            Files.writeString(jdk.resolve("bin/java.exe"), "fixture");
            Files.writeString(jdk.resolve("bin/javac.exe"), "fixture");
            Files.writeString(jdk.resolve("release"), "JAVA_VERSION=\"21.0.5\"\n");
            var results = Main.inspect(new Main.Options(root, jdk, 21, false, false, false, false),
                    new Main.Environment(jdk, 21, Map.of(), true, "amd64"));
            check(results.stream().noneMatch(f -> f.severity().equals("ERROR")), results.toString());
        });
        System.out.println("PASS: " + passed + " test scenarios");
    }

    private static void test(String name, Scenario scenario) throws Exception {
        Path root = Files.createTempDirectory("jdk-compass-test-").toRealPath();
        try {
            scenario.run(root);
            passed++;
        } catch (Exception | AssertionError e) {
            throw new AssertionError("Scenario failed: " + name, e);
        } finally {
            try (var paths = Files.walk(root)) {
                for (Path path : paths.sorted(Comparator.reverseOrder()).toList()) Files.delete(path);
            }
        }
    }

    private static Path fakeJdk(Path home, String version) throws IOException {
        Files.createDirectories(home.resolve("bin"));
        for (String name : List.of("java", "javac")) {
            Path binary = home.resolve("bin").resolve(name + (WINDOWS ? ".exe" : ""));
            Files.writeString(binary, "fixture, never executed");
            if (!WINDOWS && !binary.toFile().setExecutable(true)) throw new IOException("cannot set fixture executable");
        }
        Files.writeString(home.resolve("release"), "JAVA_VERSION=\"" + version + "\"\n");
        return home;
    }

    private static Result run(Path root, String homeVersion, Map<String, String> overrides, String... args) throws IOException {
        Path jdk = fakeJdk(root.resolve("jdk"), homeVersion);
        var variables = new java.util.HashMap<>(Map.of("JAVA_HOME", jdk.toString()));
        variables.putAll(overrides);
        return invoke(root, jdk, variables, args);
    }

    private static Result invoke(Path root, Path runtimeHome, Map<String, String> variables, String... args) {
        return invokeArchitecture(root, runtimeHome, variables, "amd64", args);
    }

    private static Result architectureReport(Path root, String selected, String runtime, String... args) throws IOException {
        Path jdk = fakeJdk(root.resolve("jdk"), "21");
        Files.writeString(jdk.resolve("release"), "JAVA_VERSION=\"21\"\nOS_ARCH=\"" + selected + "\"\n");
        String[] combined = new String[args.length + 2];
        combined[0] = "--expect"; combined[1] = "21";
        System.arraycopy(args, 0, combined, 2, args.length);
        return invokeArchitecture(root, jdk, Map.of("JAVA_HOME", jdk.toString()), runtime, combined);
    }

    private static Result invokeArchitecture(Path root, Path runtimeHome, Map<String, String> variables,
                                             String runtimeArchitecture, String... args) {
        var stdout = new ByteArrayOutputStream();
        var stderr = new ByteArrayOutputStream();
        String[] combined = new String[args.length + 2];
        combined[0] = "--project"; combined[1] = root.toString();
        System.arraycopy(args, 0, combined, 2, args.length);
        int exit = Main.run(combined, new Main.Environment(runtimeHome, 21, variables, WINDOWS, runtimeArchitecture),
                new PrintStream(stdout, true, StandardCharsets.UTF_8), new PrintStream(stderr, true, StandardCharsets.UTF_8));
        return new Result(exit, stdout.toString(StandardCharsets.UTF_8), stderr.toString(StandardCharsets.UTF_8));
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
