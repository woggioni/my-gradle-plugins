package net.woggioni.gradle.osgi.app;

import lombok.Getter;
import lombok.SneakyThrows;
import org.gradle.api.DefaultTask;
import org.gradle.api.model.ObjectFactory;
import org.gradle.api.provider.ListProperty;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.OutputFile;
import org.gradle.api.tasks.TaskAction;

import javax.inject.Inject;
import java.io.File;
import java.io.Writer;
import java.nio.file.Files;
import java.util.Collections;
import java.util.Properties;

public class JavaAgentFileTask extends DefaultTask {

    @OutputFile
    public File getOutputFile() {
        return new File(getTemporaryDir(), "javaAgents.properties");
    }

    @Getter(onMethod_ = @Input)
    private final ListProperty<JavaAgent> javaAgents;

    @Inject
    public JavaAgentFileTask(ObjectFactory objects) {
        javaAgents = objects.listProperty(JavaAgent.class)
                .convention(getProject().provider(Collections::emptyList));
    }

    @TaskAction
    @SneakyThrows
    public void run() {
        try (Writer writer = Files.newBufferedWriter(getOutputFile().toPath())) {
            Properties props = new Properties();
            for (JavaAgent javaAgent : javaAgents.get()) {
                props.setProperty(javaAgent.getClassName(), javaAgent.getArgs());
            }
            props.store(writer, null);
        }
    }
}
