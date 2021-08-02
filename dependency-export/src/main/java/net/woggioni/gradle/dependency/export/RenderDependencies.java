package net.woggioni.gradle.dependency.export;

import lombok.Getter;
import lombok.Setter;
import lombok.SneakyThrows;
import org.gradle.api.DefaultTask;
import org.gradle.api.GradleException;
import org.gradle.api.file.RegularFile;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.model.ObjectFactory;
import org.gradle.api.plugins.JavaPluginConvention;
import org.gradle.api.provider.Property;
import org.gradle.api.provider.Provider;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.InputFile;
import org.gradle.api.tasks.Internal;
import org.gradle.api.tasks.Optional;
import org.gradle.api.tasks.OutputFile;
import org.gradle.api.tasks.TaskAction;
import org.gradle.api.tasks.options.Option;

import javax.inject.Inject;
import java.io.File;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;

public class RenderDependencies extends DefaultTask {

    @Getter(onMethod_ = { @InputFile })
    private Provider<File> sourceFile;

    @Getter(onMethod_ = { @Input})
    private final Property<String> format;

    @Getter(onMethod_ = { @Input })
    private final Property<String> graphvizExecutable;

    @Getter
    @Internal
    private final RegularFileProperty outputFile;

    @Input
    @Optional
    public String getDestination() {
        return outputFile.map(RegularFile::getAsFile).map(File::getAbsolutePath).getOrNull();
    }

    @OutputFile
    public Provider<File> getResult() {
        return outputFile.map(RegularFile::getAsFile);
    }

    private final JavaPluginConvention javaPluginConvention;

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
        sourceFile = taskProvider.flatMap(ExportDependencies::getResult);
    }

    @Inject
    public RenderDependencies(ObjectFactory objects) {
        sourceFile = objects.property(File.class);
        javaPluginConvention = getProject().getConvention().getPlugin(JavaPluginConvention.class);
        format = objects.property(String.class).convention("xlib");
        graphvizExecutable = objects.property(String.class).convention("dot");
        Provider<File> defaultOutputFileProvider =
                getProject().provider(() -> new File(javaPluginConvention.getDocsDir(), "renderedDependencies"));
        outputFile = objects.fileProperty().convention(getProject().getLayout().file(defaultOutputFileProvider));
    }

    @TaskAction
    @SneakyThrows
    void run() {
        Path destination = outputFile
                .map(RegularFile::getAsFile)
                .map(File::toPath)
                .get();
        List<String> cmd = Arrays.asList(
                graphvizExecutable.get(),
                "-T" + format.get(),
                "-o" + destination,
                sourceFile.get().toString()
        );
        int returnCode = new ProcessBuilder(cmd).inheritIO().start().waitFor();
        if (returnCode != 0) {
            throw new GradleException("Error invoking graphviz");
        }
    }
}
