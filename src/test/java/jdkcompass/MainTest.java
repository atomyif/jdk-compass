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
            var results = Main.inspect(new Main.Options(root, jdk, 21, false, false),
                    new Main.Environment(jdk, 21, Map.of(), true));
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
        var stdout = new ByteArrayOutputStream();
        var stderr = new ByteArrayOutputStream();
        String[] combined = new String[args.length + 2];
        combined[0] = "--project"; combined[1] = root.toString();
        System.arraycopy(args, 0, combined, 2, args.length);
        int exit = Main.run(combined, new Main.Environment(runtimeHome, 21, variables, WINDOWS),
                new PrintStream(stdout, true, StandardCharsets.UTF_8), new PrintStream(stderr, true, StandardCharsets.UTF_8));
        return new Result(exit, stdout.toString(StandardCharsets.UTF_8), stderr.toString(StandardCharsets.UTF_8));
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
