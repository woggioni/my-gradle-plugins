package net.woggioni.gradle.osgi.app;

import lombok.Getter;
import lombok.Setter;
import lombok.SneakyThrows;
import org.gradle.api.DefaultTask;
import org.gradle.api.model.ObjectFactory;
import org.gradle.api.provider.MapProperty;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.OutputFile;
import org.gradle.api.tasks.TaskAction;

import javax.inject.Inject;
import java.io.File;
import java.io.Writer;
import java.nio.file.Files;
import java.util.Properties;

public class FrameworkPropertyFileTask extends DefaultTask {

    @OutputFile
    File getOutputFile() {
        return new File(getTemporaryDir(), "framework.properties");
    }

    @Getter(onMethod_ = @Input)
    final MapProperty<String, String> frameworkProperties;

    @Inject
    public FrameworkPropertyFileTask(ObjectFactory objects) {
        frameworkProperties = objects.mapProperty(String.class, String.class);
    }

    @TaskAction
    @SneakyThrows
    void run() {
        try(Writer writer = Files.newBufferedWriter(getOutputFile().toPath())) {
            Properties properties = new Properties();
            properties.putAll(frameworkProperties.get());
            properties.store(writer, null);
        }
    }
}