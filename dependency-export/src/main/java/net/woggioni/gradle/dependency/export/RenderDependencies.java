package net.woggioni.gradle.dependency.export;

import lombok.Getter;
import lombok.Setter;
import lombok.SneakyThrows;
import org.gradle.api.DefaultTask;
import org.gradle.api.GradleException;
import org.gradle.api.model.ObjectFactory;
import org.gradle.api.plugins.JavaPluginConvention;
import org.gradle.api.provider.Property;
import org.gradle.api.provider.Provider;
import org.gradle.api.tasks.InputFile;
import org.gradle.api.tasks.TaskAction;
import org.gradle.api.tasks.options.Option;

import javax.inject.Inject;
import java.io.File;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;

public class RenderDependencies extends DefaultTask {

    @Getter
    @InputFile
    private Provider<File> sourceFile;

    @Getter
    @Setter
    private Property<String> format;

    @Getter
    @Setter
    private Property<String> graphvizExecutable;

    @Getter
    @Setter
    private Property<File> outputFile;

    private final JavaPluginConvention javaPluginConvention;

    @Option(option = "output", description = "Set the output file name")
    public void setOutputCli(String outputFile) {
        this.outputFile.set(getProject().file(outputFile));
    }

    @Option(option = "format", description = "Set output format (see https://graphviz.org/doc/info/output.html)")
    public void setFormatCli(String format) {
        this.format.set(format);
    }

    public void setExportTask(Provider<ExportDependencies> taskProvider) {
        sourceFile = taskProvider.flatMap(ExportDependencies::getOutputFile);
    }

    @Inject
    public RenderDependencies(ObjectFactory objects) {
        sourceFile = objects.property(File.class);
        javaPluginConvention = getProject().getConvention().getPlugin(JavaPluginConvention.class);
        format = objects.property(String.class).convention("xlib");
        graphvizExecutable = objects.property(String.class).convention("dot");
        outputFile = objects.property(File.class)
                .convention(new File(javaPluginConvention.getDocsDir(), "renderedDependencies"));
    }

    @TaskAction
    @SneakyThrows
    void run() {
        Path destination = outputFile.map(it -> {
            if (it.isAbsolute()) {
                return it;
            } else {
                return new File(javaPluginConvention.getDocsDir(), it.toString());
            }
        }).map(File::toPath).get();
        List<String> cmd = Arrays.asList(
                graphvizExecutable.get(),
                "-T" + format.get(),
                "-o" + destination.toString(),
                sourceFile.get().toString()
        );
        int returnCode = new ProcessBuilder(cmd).inheritIO().start().waitFor();
        if (returnCode != 0) {
            throw new GradleException("Error invoking graphviz");
        }
    }
}
