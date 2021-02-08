package net.woggioni.gradle.dependency.export;

import lombok.SneakyThrows;
import org.gradle.testkit.runner.GradleRunner;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.FileNotFoundException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Objects;
import java.util.Optional;
import java.util.function.BiFunction;
import java.util.stream.Stream;

public class DependencyExportPluginTest {

    private static InputStream resourceFromClass(Class<?> cls, String resourceName) {
        return cls.getResourceAsStream(resourceName);
    }

    private static InputStream resourceFromClassLoader(Class<?> cls, String resourceName) {
        return cls.getClassLoader().getResourceAsStream(resourceName);
    }

    @SneakyThrows
    private static void installResource(Class<?> cls, String resourceName, Path destination) {
        Path outputFile;
        Path realDestination;
        if (Files.isSymbolicLink(destination)) {
            realDestination = destination.toRealPath();
        } else {
            realDestination = destination;
        }
        if(!Files.exists(realDestination)) {
            Files.createDirectories(realDestination.getParent());
            outputFile = realDestination;
        } else if(Files.isDirectory(realDestination)) {
            outputFile = realDestination.resolve(resourceName.substring(1 + resourceName.lastIndexOf('/')));
        } else if(Files.isRegularFile(realDestination)) {
            outputFile = realDestination;
        } else throw new IllegalStateException("Path '${realDestination}' is neither a file nor a directory");
        Optional<InputStream> inputStreamOptional = Stream.<BiFunction<Class<?>, String, InputStream>>of(
                DependencyExportPluginTest::resourceFromClass,
                DependencyExportPluginTest::resourceFromClassLoader
        ).map(f -> f.apply(cls, resourceName)).filter(Objects::nonNull).findFirst();
        try(InputStream inputStream = inputStreamOptional.orElseThrow(() -> new FileNotFoundException(resourceName))){
            Files.copy(inputStream, outputFile, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    @TempDir
    public Path testGradleHomeDir;

    @TempDir
    public Path testProjectDir;

    public Path buildFile;

    @BeforeEach
    public void setup() {
        buildFile = testProjectDir.resolve("build.gradle.kts");
    }

    public GradleRunner getStandardGradleRunnerFor(String taskName) {
        return GradleRunner.create()
                .withDebug(true)
                .withProjectDir(testProjectDir.toFile())
                .withArguments(taskName, "-s", "--info", "-g", testGradleHomeDir.toString())
                .withPluginClasspath();
    }

    @Test
    public void testKotlin() {
        installResource(getClass(), "build.gradle.kts", testProjectDir);
        installResource(getClass(),"settings.gradle.kts", testProjectDir);
        installResource(getClass(),"gradle.properties", testProjectDir);
        GradleRunner runner = getStandardGradleRunnerFor("exportDependencies");
        runner.build();
    }

    @Test
    public void testGroovy() {
        installResource(getClass(),"build.gradle", testProjectDir);
        installResource(getClass(),"settings.gradle.kts", testProjectDir);
        installResource(getClass(),"gradle.properties", testProjectDir);
        GradleRunner runner = getStandardGradleRunnerFor("exportDependencies");
        runner.build();
    }
}
