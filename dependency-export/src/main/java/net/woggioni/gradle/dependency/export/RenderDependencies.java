package net.woggioni.gradle.dependency.export;

import lombok.Getter;
import lombok.SneakyThrows;
import org.gradle.api.DefaultTask;
import org.gradle.api.GradleException;
import org.gradle.api.file.RegularFile;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.model.ObjectFactory;
import org.gradle.api.plugins.JavaPluginExtension;
import org.gradle.api.provider.Property;
import org.gradle.api.provider.Provider;
import org.gradle.api.tasks.CacheableTask;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.InputFile;
import org.gradle.api.tasks.Internal;
import org.gradle.api.tasks.Optional;
import org.gradle.api.tasks.OutputFile;
import org.gradle.api.tasks.PathSensitive;
import org.gradle.api.tasks.PathSensitivity;
import org.gradle.api.tasks.TaskAction;
import org.gradle.api.tasks.options.Option;

import javax.inject.Inject;
import java.io.File;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;

import static net.woggioni.gradle.dependency.export.DependencyExportPlugin.DEPENDENCY_EXPORT_GROUP;

@CacheableTask
public class RenderDependencies extends DefaultTask {

    @Getter(onMethod_ = {@InputFile, @PathSensitive(PathSensitivity.NONE)})
    private Provider<File> sourceFile;

    @Getter(onMethod_ = {@Input})
    private final Property<String> format;

    @Getter(onMethod_ = {@Input})
    private final Property<String> graphvizExecutable;

    @Getter
    @Internal
    private final RegularFileProperty outputFile;

    @Input
    @Optional
    public String getDestination() {
        return outputFile.map(RegularFile::getAsFile).map(File::getAbsolutePath).getOrNull();
    }

    @Optional
    @OutputFile
    public Provider<File> getResult() {
        return outputFile.map(RegularFile::getAsFile);
    }

    @Option(option = "output", description = "Set the output file name")
    public void setOutputCli(String outputFile) {
        Provider<File> fileProvider = getProject().provider(() -> new File(outputFile));
        this.outputFile.set(getProject().getLayout().file(fileProvider));
    }

    @Option(option = "format", description = "Set output format (see https://graphviz.org/doc/info/output.html)")
    public void setFormatCli(String format) {
        this.format.set(format);
    }

    public void setExportTask(Provider<ExportDependencies> taskProvider) {
        dependsOn(taskProvider);
        sourceFile = taskProvider.flatMap(ExportDependencies::getResult);
    }

    @Inject
    public RenderDependencies(ObjectFactory objects) {
        setGroup(DEPENDENCY_EXPORT_GROUP);
        sourceFile = objects.property(File.class);
        format = objects.property(String.class).convention("xlib");
        graphvizExecutable = objects.property(String.class).convention("dot");
        final JavaPluginExtension javaPluginExtension = getProject().getExtensions().findByType(JavaPluginExtension.class);
        final Provider<RegularFile> defaultOutputFileProvider = javaPluginExtension.getDocsDir().file("renderedDependencies");
        outputFile = objects.fileProperty().convention(defaultOutputFileProvider
                .zip(format, (file, type) -> Objects.equals("xlib", type) ? null : file));
        getOutputs().upToDateWhen(t -> outputFile.isPresent());
    }

    @TaskAction
    @SneakyThrows
    void run() {
        java.util.Optional<Path> destination = java.util.Optional.of(
                    outputFile
                            .map(RegularFile::getAsFile)
                            .map(File::toPath)
                )
                .filter(Provider::isPresent)
                .map(Provider::get);

        List<String> cmd = new ArrayList<>(Arrays.asList(
                graphvizExecutable.get(),
                "-T" + format.get()
        ));

        if (destination.isPresent()) {
            cmd.add("-o");
            cmd.add(destination.get().toString());
        }
        cmd.add(sourceFile.get().toString());

        int returnCode = new ProcessBuilder(cmd).inheritIO().start().waitFor();
        if (returnCode != 0) {
            throw new GradleException("Error invoking graphviz");
        }
    }
}
