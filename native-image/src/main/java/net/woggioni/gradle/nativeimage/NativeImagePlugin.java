package net.woggioni.gradle.nativeimage;

import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.artifacts.ConfigurationContainer;
import org.gradle.api.file.FileCollection;
import org.gradle.api.file.ProjectLayout;
import org.gradle.api.plugins.ExtensionContainer;
import org.gradle.api.plugins.JavaApplication;
import org.gradle.api.plugins.JavaLibraryPlugin;
import org.gradle.api.plugins.JavaPlugin;
import org.gradle.api.provider.Provider;
import org.gradle.api.tasks.TaskContainer;
import org.gradle.jvm.tasks.Jar;

import java.util.Optional;

public class NativeImagePlugin implements Plugin<Project> {

    public static final String NATIVE_IMAGE_TASK_NAME = "nativeImage";
    public static final String CONFIGURE_NATIVE_IMAGE_TASK_NAME = "configureNativeImage";
    public static final String NATIVE_IMAGE_CONFIGURATION_FOLDER_NAME = "native-image";

    public static final String NATIVE_IMAGE_TASK_GROUP = "native image";

    @Override
    public void apply(Project project) {
        project.getPluginManager().apply(JavaLibraryPlugin.class);
        ProjectLayout layout = project.getLayout();
        ExtensionContainer extensionContainer = project.getExtensions();
        JavaApplication javaApplicationExtension =
            Optional.ofNullable(extensionContainer.findByType(JavaApplication.class))
                .orElseGet(() -> extensionContainer.create("application", JavaApplication.class));

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
        tasks.register(CONFIGURE_NATIVE_IMAGE_TASK_NAME, NativeImageConfigurationTask.class);
        tasks.register(NATIVE_IMAGE_TASK_NAME, NativeImageTask.class, nativeImageTask -> {
            ConfigurationContainer configurations = project.getConfigurations();
            FileCollection classpath = project.files(jarTaskProvider,
                    configurations.getByName(JavaPlugin.RUNTIME_CLASSPATH_CONFIGURATION_NAME));
            nativeImageTask.getClasspath().set(classpath);
        });
    }
}
