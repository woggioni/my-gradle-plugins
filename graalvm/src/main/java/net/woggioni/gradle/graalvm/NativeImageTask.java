package net.woggioni.gradle.graalvm;

import org.gradle.api.Project;
import org.gradle.api.file.Directory;
import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.file.FileCollection;
import org.gradle.api.file.ProjectLayout;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.logging.Logger;
import org.gradle.api.logging.Logging;
import org.gradle.api.plugins.BasePluginExtension;
import org.gradle.api.plugins.ExtensionContainer;
import org.gradle.api.plugins.JavaApplication;
import org.gradle.api.plugins.JavaPluginExtension;
import org.gradle.api.provider.Property;
import org.gradle.api.provider.Provider;
import org.gradle.api.tasks.CacheableTask;
import org.gradle.api.tasks.Classpath;
import org.gradle.api.tasks.Exec;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.InputDirectory;
import org.gradle.api.tasks.InputFile;
import org.gradle.api.tasks.Optional;
import org.gradle.api.tasks.OutputFile;
import org.gradle.api.tasks.PathSensitive;
import org.gradle.api.tasks.PathSensitivity;
import org.gradle.internal.jvm.JavaModuleDetector;
import org.gradle.jvm.toolchain.JavaInstallationMetadata;
import org.gradle.jvm.toolchain.JavaLauncher;
import org.gradle.jvm.toolchain.JavaToolchainService;
import org.gradle.process.CommandLineArgumentProvider;

import javax.inject.Inject;
import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

import static java.util.Optional.ofNullable;
import static net.woggioni.gradle.graalvm.Constants.GRAALVM_TASK_GROUP;

@CacheableTask
public abstract class NativeImageTask extends Exec {

    public static final String NATIVE_COMPILER_PATH_ENV_VARIABLE = "GRAAL_NATIVE_COMPILER_PATH";
    public static final String NATIVE_COMPILER_PATH_PROPERTY_KEY = "graal.native.compiler.path";

    @Classpath
    public abstract Property<FileCollection> getClasspath();

    @InputDirectory
    @PathSensitive(PathSensitivity.RELATIVE)
    public abstract DirectoryProperty getGraalVmHome();

    @InputFile
    @PathSensitive(PathSensitivity.ABSOLUTE)
    public abstract RegularFileProperty getNativeCompilerPath();

    @Input
    public abstract Property<Boolean> getUseMusl();
    @Input
    public abstract Property<Boolean> getBuildStaticImage();
    @Input
    public abstract Property<Boolean> getEnableFallback();
    @Input
    public abstract Property<Boolean> getLinkAtBuildTime();

    @Input
    public abstract Property<String> getMainClass();

    @Input
    @Optional
    public abstract Property<String> getMainModule();

    @Inject
    protected abstract JavaModuleDetector getJavaModuleDetector();

    @OutputFile
    protected abstract RegularFileProperty getOutputFile();

    private static final Logger log = Logging.getLogger(NativeImageTask.class);

    public NativeImageTask() {
        Project project = getProject();
        setGroup(GRAALVM_TASK_GROUP);
        setDescription("Create a native image of the application using GraalVM");
        getUseMusl().convention(false);
        getBuildStaticImage().convention(false);
        getEnableFallback().convention(false);
        getLinkAtBuildTime().convention(false);
        Provider<File> nativeComnpilerProvider = project.provider(() -> {
            String envVar;
            File compilerPath = null;
            if(project.hasProperty(NATIVE_COMPILER_PATH_PROPERTY_KEY)) {
                compilerPath = new File(project.property(NATIVE_COMPILER_PATH_PROPERTY_KEY).toString());
            } else if((envVar = System.getenv(NATIVE_COMPILER_PATH_ENV_VARIABLE)) != null) {
                compilerPath = new File(envVar);
            }
            return compilerPath;
        });
        getNativeCompilerPath().convention(project.getLayout().file(nativeComnpilerProvider));
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
        ).orElseGet(() -> layout.dir(project.provider(() -> project.file(System.getProperty("java.home")))));
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
                if(getBuildStaticImage().get()) {
                    result.add("--static");
                }
                if(getUseMusl().get()) {
                    result.add("--libc=musl");
                }
                if(getLinkAtBuildTime().get()) {
                    result.add("--link-at-build-time");
                }
                if(getNativeCompilerPath().isPresent()) {
                    result.add("--native-compiler-path=" + getNativeCompilerPath().getAsFile().get());
                }
                JavaModuleDetector javaModuleDetector = getJavaModuleDetector();
                boolean useJpms = getMainModule().isPresent();
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
                if(getMainModule().isPresent()) {
                    result.add("--module");
                    String mainModule = getMainModule().get();
                    result.add(getMainClass()
                            .map(mainClass -> String.format("%s/%s", mainModule, mainClass))
                            .getOrElse(mainModule));
                } else {
                    result.add(getMainClass().get());
                }
                return Collections.unmodifiableList(result);
            }
        };
        getArgumentProviders().add(argumentProvider);
    }
}
