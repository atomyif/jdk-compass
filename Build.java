import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import javax.tools.ToolProvider;

/** Dependency-free build entry point. Run from the repository root with JDK 17+. */
class Build {
    public static void main(String[] args) throws Exception {
        if (args.length != 0) throw new IllegalArgumentException("Usage: java Build.java");
        if (!Files.isDirectory(Path.of("src/main/java"))) {
            throw new IllegalStateException("Run this command from the repository root.");
        }
        Path classes = Path.of("build/classes");
        Path tests = Path.of("build/test-classes");
        for (Path directory : List.of(classes, tests)) {
            if (Files.exists(directory)) {
                try (var stream = Files.walk(directory)) {
                    for (Path file : stream.sorted(java.util.Comparator.reverseOrder()).toList()) {
                        Files.delete(file);
                    }
                }
            }
            Files.createDirectories(directory);
        }
        compile("src/main/java", classes, List.of());
        compile("src/test/java", tests, List.of("-classpath", classes.toString()));
        String suffix = System.getProperty("os.name").startsWith("Windows") ? ".exe" : "";
        String javaExecutable = Path.of(System.getProperty("java.home"), "bin", "java" + suffix).toString();
        int testResult = new ProcessBuilder(javaExecutable, "-cp", classes + System.getProperty("path.separator") + tests,
                "jdkcompass.MainTest").inheritIO().start().waitFor();
        if (testResult != 0) throw new IllegalStateException("Tests failed; packaging stopped.");
        var manifest = new java.util.jar.Manifest();
        manifest.getMainAttributes().putValue("Manifest-Version", "1.0");
        manifest.getMainAttributes().putValue("Main-Class", "jdkcompass.Main");
        try (var jar = new java.util.jar.JarOutputStream(
                Files.newOutputStream(Path.of("build/jdk-compass.jar")), manifest);
                var files = Files.walk(classes)) {
            for (Path file : files.filter(Files::isRegularFile).sorted().toList()) {
                var entry = new java.util.jar.JarEntry(classes.relativize(file).toString().replace('\\', '/'));
                entry.setTime(0);
                jar.putNextEntry(entry);
                Files.copy(file, jar);
                jar.closeEntry();
            }
            var license = new java.util.jar.JarEntry("META-INF/LICENSE");
            license.setTime(0);
            jar.putNextEntry(license);
            Files.copy(Path.of("LICENSE"), jar);
            jar.closeEntry();
        }
        System.out.println("Built build/jdk-compass.jar");
    }

    private static void compile(String source, Path destination, List<String> extra) throws Exception {
        var compiler = ToolProvider.getSystemJavaCompiler();
        if (compiler == null) throw new IllegalStateException("A full JDK 17+ is required.");
        List<String> arguments = new ArrayList<>(List.of("--release", "17", "-encoding", "UTF-8",
                "-Xlint:all", "-Werror", "-d", destination.toString()));
        arguments.addAll(extra);
        try (var files = Files.walk(Path.of(source))) {
            files.filter(p -> p.toString().endsWith(".java")).sorted().forEach(p -> arguments.add(p.toString()));
        }
        if (compiler.run(null, System.out, System.err, arguments.toArray(String[]::new)) != 0) {
            throw new IllegalStateException("Compilation failed.");
        }
    }
}
