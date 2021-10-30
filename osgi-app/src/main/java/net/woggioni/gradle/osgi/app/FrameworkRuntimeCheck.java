package net.woggioni.gradle.osgi.app;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import org.gradle.api.DefaultTask;
import org.gradle.api.GradleException;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.provider.Provider;
import org.gradle.api.tasks.InputFiles;
import org.gradle.api.tasks.TaskAction;

import javax.inject.Inject;
import java.io.File;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.Set;

@RequiredArgsConstructor(onConstructor_ = @Inject)
public class FrameworkRuntimeCheck extends DefaultTask {

    @Getter(onMethod_ = @InputFiles)
    private final Provider<Configuration> confProvider;

    @TaskAction
    @SneakyThrows
    void run() {
        Configuration conf = confProvider.get();
        Set<File> files = conf.getFiles();
        URL[] urls = new URL[files.size()];
        int i = 0;
        for(File file : files) {
            urls[i++] = file.toURI().toURL();
        }
        try {
            new URLClassLoader(urls).loadClass("org.osgi.framework.launch.Framework");
        } catch (ClassNotFoundException cnfe) {
            throw new GradleException(
                    String.format("No OSGi framework runtime found in '%s' configuration", conf.getName()));
        }
    }
}
