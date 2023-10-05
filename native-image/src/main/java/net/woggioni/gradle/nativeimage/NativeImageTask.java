package net.woggioni.gradle.nativeimage;

import org.gradle.api.Project;
import org.gradle.api.file.Directory;
import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.file.FileCollection;
import org.gradle.api.file.ProjectLayout;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.logging.Logger;
import org.gradle.api.plugins.BasePluginExtension;
import org.gradle.api.plugins.ExtensionContainer;
import org.gradle.api.plugins.JavaApplication;
import org.gradle.api.plugins.JavaPluginExtension;
import org.gradle.api.provider.Property;
import org.gradle.api.provider.Provider;
import org.gradle.api.tasks.Classpath;
import org.gradle.api.tasks.Exec;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.InputDirectory;
import org.gradle.api.tasks.Optional;
import org.gradle.api.tasks.OutputFile;
import org.gradle.internal.jvm.JavaModuleDetector;
import org.gradle.jvm.toolchain.JavaInstallationMetadata;
import org.gradle.jvm.toolchain.JavaLauncher;
import org.gradle.jvm.toolchain.JavaToolchainService;
import org.gradle.process.CommandLineArgumentProvider;

import javax.inject.Inject;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

import static java.util.Optional.ofNullable;
import static net.woggioni.gradle.nativeimage.NativeImagePlugin.NATIVE_IMAGE_TASK_GROUP;

public abstract class NativeImageTask extends Exec {

    @Classpath
    public abstract Property<FileCollection> getClasspath();

    @InputDirectory
    public abstract DirectoryProperty getGraalVmHome();

    @Input
    public abstract Property<Boolean> getUseJpms();
    @Input
    public abstract Property<Boolean> getEnableFallback();

    @Input
    public abstract Property<String> getMainClass();

    @Input
    @Optional
    public abstract Property<String> getMainModule();

    @Inject
    protected abstract JavaModuleDetector getJavaModuleDetector();

    @OutputFile
    protected abstract RegularFileProperty getOutputFile();
    private final Logger logger;
    public NativeImageTask() {
        Project project = getProject();
        logger = project.getLogger();
        setGroup(NATIVE_IMAGE_TASK_GROUP);
        setDescription("Create a native image of the application using GraalVM");
        getUseJpms().convention(false);
        getEnableFallback().convention(false);
        ExtensionContainer ext = project.getExtensions();
        JavaApplication javaApplication = ext.findByType(JavaApplication.class);
        if(!Objects.isNull(javaApplication)) {
            getMainClass().convention(javaApplication.getMainClass());
            getMainModule().convention(javaApplication.getMainModule());
        }
        getClasspath().convention(project.files());
        ProjectLayout layout = project.getLayout();
        JavaToolchainService javaToolchainService = ext.findByType(JavaToolchainService.class);
        JavaPluginExtension javaPluginExtension = ext.findByType(JavaPluginExtension.class);
        Provider<Directory> graalHomeDirectoryProvider = ofNullable(javaPluginExtension.getToolchain()).map(javaToolchainSpec ->
            javaToolchainService.launcherFor(javaToolchainSpec)
        ).map(javaLauncher ->
            javaLauncher.map(JavaLauncher::getMetadata).map(JavaInstallationMetadata::getInstallationPath)
        ).orElseGet(() -> layout.dir(project.provider(() ->project.file(System.getProperty("java.home")))));
        getGraalVmHome().convention(graalHomeDirectoryProvider);

        BasePluginExtension basePluginExtension =
                ext.getByType(BasePluginExtension.class);
        getOutputFile().convention(basePluginExtension.getLibsDirectory().file(project.getName()));
        Object executableProvider = new Object() {
            @Override
            public String toString() {
                return getGraalVmHome().get() + "/bin/native-image";
            }
        };
        executable(executableProvider);

        CommandLineArgumentProvider argumentProvider = new CommandLineArgumentProvider() {
            @Override
            public Iterable<String> asArguments() {
                List<String> result = new ArrayList<>();
                if(!getEnableFallback().get()) {
                    result.add("--no-fallback");
                }
                JavaModuleDetector javaModuleDetector = getJavaModuleDetector();
                boolean useJpms = getUseJpms().get();
                FileCollection classpath = getClasspath().get();
                FileCollection cp = javaModuleDetector.inferClasspath(useJpms, classpath);
                FileCollection mp = javaModuleDetector.inferModulePath(useJpms, classpath);
                if(!cp.isEmpty()) {
                    result.add("-cp");
                    result.add(cp.getAsPath());
                }
                if(!mp.isEmpty()) {
                    result.add("-p");
                    result.add(mp.getAsPath());
                }
                result.add("-o");
                result.add(getOutputFile().get().getAsFile().toString());
                result.add(getMainClass().get());
                logger.info("Native image arguments: " + String.join(" ", result));
                return Collections.unmodifiableList(result);
            }
        };
        getArgumentProviders().add(argumentProvider);
    }
}
