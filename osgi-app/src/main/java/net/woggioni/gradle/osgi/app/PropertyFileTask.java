package net.woggioni.gradle.osgi.app;

import lombok.Getter;
import lombok.SneakyThrows;
import org.gradle.api.DefaultTask;
import org.gradle.api.model.ObjectFactory;
import org.gradle.api.provider.MapProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.provider.Provider;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.OutputFile;
import org.gradle.api.tasks.TaskAction;

import javax.inject.Inject;
import java.io.File;
import java.io.Writer;
import java.nio.file.Files;
import java.util.Properties;

public class PropertyFileTask extends DefaultTask {

    @Getter(onMethod_ = @Input)
    private final Property<String> fileName;

    @OutputFile
    Provider<File> getOutputFile() {
        return fileName.map(name -> new File(getTemporaryDir(), name));
    }

    @Getter(onMethod_ = @Input)
    final MapProperty<String, String> properties;

    @Inject
    public PropertyFileTask(ObjectFactory objects) {
        fileName = objects.property(String.class);
        properties = objects.mapProperty(String.class, String.class);
    }

    @TaskAction
    @SneakyThrows
    void run() {
        try(Writer writer = Files.newBufferedWriter(getOutputFile().get().toPath())) {
            Properties properties = new Properties();
            properties.putAll(this.properties.get());
            properties.store(writer, null);
        }
    }
}