package net.woggioni.gradle.graalvm;

import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.artifacts.ConfigurationContainer;
import org.gradle.api.file.Directory;
import org.gradle.api.file.FileCollection;
import org.gradle.api.file.ProjectLayout;
import org.gradle.api.plugins.ExtensionContainer;
import org.gradle.api.plugins.JavaLibraryPlugin;
import org.gradle.api.plugins.JavaPlugin;
import org.gradle.api.plugins.JavaPluginExtension;
import org.gradle.api.provider.Property;
import org.gradle.api.provider.Provider;
import org.gradle.api.tasks.CacheableTask;
import org.gradle.api.tasks.TaskContainer;
import org.gradle.jvm.tasks.Jar;
import org.gradle.jvm.toolchain.JavaInstallationMetadata;
import org.gradle.jvm.toolchain.JavaLauncher;
import org.gradle.jvm.toolchain.JavaToolchainService;

import static java.util.Optional.ofNullable;

@CacheableTask
public class NativeImagePlugin implements Plugin<Project> {

    public static final String NATIVE_IMAGE_TASK_NAME = "nativeImage";
    public static final String UPX_TASK_NAME = "upx";
    public static final String CONFIGURE_NATIVE_IMAGE_TASK_NAME = "configureNativeImage";
    public static final String NATIVE_IMAGE_CONFIGURATION_FOLDER_NAME = "native-image";

    private static <T> void setIfPresent(Property<T> p1, Provider<T> provider) {
        if(provider.isPresent()) {
            p1.set(provider);
        }
    }

    @Override
    public void apply(Project project) {
        project.getPluginManager().apply(JavaLibraryPlugin.class);
        ProjectLayout layout = project.getLayout();
        ExtensionContainer extensionContainer = project.getExtensions();
        TaskContainer tasks = project.getTasks();

        Provider<Jar> jarTaskProvider = tasks.named(JavaPlugin.JAR_TASK_NAME, Jar.class, jar -> {
            jar.from(layout.getProjectDirectory().dir(NATIVE_IMAGE_CONFIGURATION_FOLDER_NAME), copySpec -> {
                copySpec.into(
                    String.format("META-INF/native-image/%s/%s/",
                        project.getName(),
                        project.getGroup()
                    )
                );
            });
        });
        NativeImageExtension nativeImageExtension = extensionContainer.create("nativeImage", NativeImageExtension.class);

        ExtensionContainer ext = project.getExtensions();
        JavaPluginExtension javaPluginExtension = ext.findByType(JavaPluginExtension.class);
        JavaToolchainService javaToolchainService = ext.findByType(JavaToolchainService.class);

        Provider<Directory> graalHomeDirectoryProvider = ofNullable(javaPluginExtension.getToolchain()).map(javaToolchainSpec ->
                javaToolchainService.launcherFor(javaToolchainSpec)
        ).map(javaLauncher ->
                javaLauncher.map(JavaLauncher::getMetadata).map(JavaInstallationMetadata::getInstallationPath)
        ).orElseGet(() -> layout.dir(project.provider(() -> project.file(System.getProperty("java.home")))));
        nativeImageExtension.getGraalVmHome().convention(graalHomeDirectoryProvider);
        nativeImageExtension.getUseMusl().convention(false);
        nativeImageExtension.getEnableFallback().convention(false);
        nativeImageExtension.getLinkAtBuildTime().convention(false);
        nativeImageExtension.getBuildStaticImage().convention(false);
        nativeImageExtension.getCompressExecutable().convention(false);
        nativeImageExtension.getUseLZMA().convention(false);
        nativeImageExtension.getCompressionLevel().convention(6);

        ConfigurationContainer configurations = project.getConfigurations();
        FileCollection classpath = project.files(jarTaskProvider,
                configurations.getByName(JavaPlugin.RUNTIME_CLASSPATH_CONFIGURATION_NAME));
        nativeImageExtension.getClasspath().convention(classpath);

        Provider<NativeImageConfigurationTask> nativeImageConfigurationTaskProvider =
            tasks.register(CONFIGURE_NATIVE_IMAGE_TASK_NAME, NativeImageConfigurationTask.class);

        Provider<NativeImageTask> nativeImageTaskProvider = tasks.register(NATIVE_IMAGE_TASK_NAME, NativeImageTask.class, nativeImageTask -> {
            nativeImageTask.getClasspath().set(nativeImageExtension.getClasspath());
            nativeImageTask.getBuildStaticImage().set(nativeImageExtension.getBuildStaticImage());
            nativeImageTask.getUseMusl().set(nativeImageExtension.getUseMusl());
            nativeImageTask.getLinkAtBuildTime().set(nativeImageExtension.getLinkAtBuildTime());
            nativeImageTask.getMainClass().set(nativeImageExtension.getMainClass());
            nativeImageTask.getMainModule().set(nativeImageExtension.getMainModule());
            nativeImageTask.getEnableFallback().set(nativeImageExtension.getEnableFallback());
        });

        Provider<UpxTask> upxTaskProvider = tasks.register(UPX_TASK_NAME, UpxTask.class, t -> {
            t.getInputFile().set(nativeImageTaskProvider.flatMap(NativeImageTask::getOutputFile));
            setIfPresent(t.getUseLZMA(), nativeImageExtension.getUseLZMA());
            setIfPresent(t.getCompressionLevel(), nativeImageExtension.getCompressionLevel());
            setIfPresent(t.getCompressionLevel(), nativeImageExtension.getCompressionLevel());
        });

        tasks.named(NATIVE_IMAGE_TASK_NAME, NativeImageTask.class, t -> {
            if(nativeImageExtension.getCompressExecutable().getOrElse(false)) {
                t.finalizedBy(upxTaskProvider);
            }
        });

    }
}
