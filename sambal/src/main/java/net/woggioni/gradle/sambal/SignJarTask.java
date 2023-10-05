package net.woggioni.gradle.sambal;

import org.gradle.api.DefaultTask;
import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.file.RegularFile;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.plugins.BasePluginExtension;
import org.gradle.api.plugins.JavaPlugin;
import org.gradle.api.provider.MapProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.provider.Provider;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.InputFile;
import org.gradle.api.tasks.Optional;
import org.gradle.api.tasks.OutputFile;
import org.gradle.api.tasks.TaskAction;
import org.gradle.api.tasks.bundling.Jar;

import javax.inject.Inject;
import java.io.File;
import java.util.Map;
import java.util.TreeMap;

abstract class SignJarTask extends DefaultTask {
    @InputFile
    public abstract RegularFileProperty getInputJarFile();

    @Input
    public abstract MapProperty<String,String> getOptions();

    @Input
    public abstract DirectoryProperty getArchiveDestination();

    @Input
    public abstract Property<String> getArchiveBaseName();

    @Input
    public abstract Property<String> getArchiveVersion();

    @Input
    public abstract Property<String> getArchiveExtension();

    @Input
    @Optional
    public abstract Property<String> getArchiveClassifier();

    @OutputFile
    public Provider<RegularFile> getArchiveFile() {
        StringBuilder sb = new StringBuilder(getArchiveBaseName().get());
        sb.append('-').append(getArchiveVersion().get());
        if(getArchiveClassifier().isPresent() && !getArchiveClassifier().get().isBlank()) {
            sb.append('-').append(getArchiveClassifier().get());
        }
        sb.append('.').append(getArchiveExtension().get());
        return getProject().getLayout().file(getArchiveDestination().map(ad ->
            new File(ad.getAsFile(), sb.toString())
        ));
    }

    @Inject
    public SignJarTask() {
        BasePluginExtension bpe = getProject()
            .getExtensions()
            .getByType(BasePluginExtension.class);
        getArchiveDestination().convention(bpe.getLibsDirectory());
        getArchiveBaseName().convention(getProject().getName());
        getArchiveVersion().convention(getProject().getVersion().toString());
        getArchiveExtension().convention("jar");
        getArchiveClassifier().convention("signed");
        getArchiveClassifier().set("signed");
        getInputJarFile().convention(
                getProject().getTasks()
                .named(JavaPlugin.JAR_TASK_NAME, Jar.class)
                .flatMap(Jar::getArchiveFile));
    }

    @TaskAction
    public void run() {
        Map<String, String> signingOptions = new TreeMap<>(getOptions().get());
        signingOptions.put("jar", getInputJarFile().map(RegularFile::getAsFile).get().toString());
        signingOptions.put("signedJar", getArchiveFile().get().getAsFile().toString());
        getAnt().invokeMethod("signjar", signingOptions);
    }
}
