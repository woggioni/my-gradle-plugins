package net.woggioni.gradle.jlink;

import lombok.Getter;
import lombok.Setter;
import lombok.SneakyThrows;
import org.apache.tools.ant.taskdefs.condition.Os;
import org.gradle.api.DefaultTask;
import org.gradle.api.GradleException;
import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.file.FileCollection;
import org.gradle.api.model.ObjectFactory;
import org.gradle.api.plugins.*;
import org.gradle.api.provider.ListProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.provider.Provider;
import org.gradle.api.tasks.*;
import org.gradle.jvm.toolchain.JavaInstallationMetadata;
import org.gradle.jvm.toolchain.JavaLauncher;
import org.gradle.jvm.toolchain.JavaToolchainService;

import javax.annotation.Nonnull;
import javax.inject.Inject;
import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;


public class JlinkTask extends DefaultTask {

    @Getter(onMethod_ = {@Input, @Optional} )
    private final Property<String> launcher;

    @Getter(onMethod_ = {@Input, @Optional} )
    private final Property<String> mainClass;

    @Getter(onMethod_ = {@Input} )
    private final Property<String> mainModule;

    @Getter(onMethod_ = {@Input} )
    private final ListProperty<String> rootModules;

    @Getter(onMethod_ = {@OutputDirectory} )
    private final DirectoryProperty destination;

    @Setter
    @Getter(onMethod_ = {@InputFiles} )
    private FileCollection modulePath;

    private final Provider<JavaLauncher> javaLauncher;

    @Inject
    public JlinkTask(@Nonnull ObjectFactory objectFactory) {
        ExtensionContainer ext = getProject().getExtensions();
        mainClass = objectFactory.property(String.class).convention(ext.findByType(JavaApplication.class).getMainClass());
        mainModule = objectFactory.property(String.class).convention(ext.findByType(JavaApplication.class).getMainModule());
        launcher = objectFactory.property(String.class).convention(getProject().provider(() -> {
            String source;
            if(mainClass.isPresent()) {
                source = mainClass.get();
            } else {
                source = mainModule.get();
            }
            int i = source.lastIndexOf(".");
            return source.substring(i == -1 ? 0 : i);
        }));
        rootModules = objectFactory.listProperty(String.class).convention(getProject().provider(() -> Arrays.asList(mainModule.get())));
        modulePath = getProject().getConfigurations().getByName(JavaPlugin.RUNTIME_CLASSPATH_CONFIGURATION_NAME)
                .plus(getProject().getTasks().named(JavaPlugin.JAR_TASK_NAME).get().getOutputs().getFiles());

        destination = objectFactory.directoryProperty().convention(
                ext.findByType(BasePluginExtension.class)
                    .getDistsDirectory()
                    .map(it -> it.dir(getProject().getName()))
        );

        JavaToolchainService javaToolchainService = ext.findByType(JavaToolchainService.class);
        JavaPluginExtension javaPluginExtension = ext.findByType(JavaPluginExtension.class);
        javaLauncher = objectFactory.property(JavaLauncher.class)
                .convention(javaToolchainService.launcherFor(javaPluginExtension.getToolchain()));
    }

    @SneakyThrows
    @TaskAction
    public void run() {
        File javaHome;
        if(javaLauncher.isPresent()) {
            JavaInstallationMetadata javaInstallationMetadata = javaLauncher.get().getMetadata();
            if (!javaInstallationMetadata.getLanguageVersion().canCompileOrRun(9)) {
                throw new GradleException(String.format("Minimum Java version supported by '%s' is 9", JlinkTask.class.getName()));
            }
            javaHome = javaInstallationMetadata.getInstallationPath().getAsFile();
        } else {
            javaHome = new File(System.getProperty("java.home"));
        }
        String executableFileExtension;
        if(Os.isFamily(Os.FAMILY_WINDOWS)) {
            executableFileExtension = ".exe";
        } else {
            executableFileExtension = "";
        }
        String jlinkPath = new File(javaHome, "bin/jlink" + executableFileExtension).getPath();

        List<String> cmd = new ArrayList<>();
        cmd.add(jlinkPath);
        cmd.add("--module-path");
        cmd.add(modulePath.getAsPath());
        for(String moduleName : rootModules.get()) {
            cmd.add("--add-modules");
            cmd.add(moduleName);
        }
        cmd.add("--launcher");
        if(mainClass.isPresent()) {
            cmd.add(String.format("%s=%s/%s", launcher.get(), mainModule.get(), mainClass.get()));
        } else {
            cmd.add(String.format("%s=%s", launcher.get(), mainModule.get()));
        }
        cmd.add("--output");
        cmd.add(destination.get().getAsFile().getPath());
        getProject().exec(execSpec -> execSpec.commandLine(cmd));
    }
}
