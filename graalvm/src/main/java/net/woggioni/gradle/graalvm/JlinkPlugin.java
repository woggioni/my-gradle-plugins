package net.woggioni.gradle.graalvm;

import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.artifacts.ConfigurationContainer;
import org.gradle.api.file.FileCollection;
import org.gradle.api.plugins.BasePluginExtension;
import org.gradle.api.plugins.ExtensionContainer;
import org.gradle.api.plugins.JavaApplication;
import org.gradle.api.plugins.JavaLibraryPlugin;
import org.gradle.api.plugins.JavaPlugin;
import org.gradle.api.provider.Provider;
import org.gradle.api.tasks.TaskContainer;
import org.gradle.api.tasks.bundling.Tar;
import org.gradle.api.tasks.bundling.Zip;

import java.util.Optional;

public class JlinkPlugin implements Plugin<Project> {

    public static final String JLINK_TASK_NAME = "jlink";
    public static final String JLINK_DIST_ZIP_TASK_NAME = "jlinkDistZip";
    public static final String JLINK_DIST_TAR_TASK_NAME = "jlinkDistTar";

    @Override
    public void apply(Project project) {
        project.getPluginManager().apply(JavaLibraryPlugin.class);
        ExtensionContainer extensionContainer = project.getExtensions();
        BasePluginExtension basePluginExtension = extensionContainer.getByType(BasePluginExtension.class);

        TaskContainer tasks = project.getTasks();
        Provider<JlinkTask> jlinTaskProvider = tasks.register(JLINK_TASK_NAME, JlinkTask.class, jlinkTask -> {
            ConfigurationContainer configurations = project.getConfigurations();
            FileCollection classpath = project.files(tasks.named(JavaPlugin.JAR_TASK_NAME),
                    configurations.getByName(JavaPlugin.RUNTIME_CLASSPATH_CONFIGURATION_NAME));
            jlinkTask.getClasspath().set(classpath);
        });

        Provider<Zip> jlinkZipTaskProvider = tasks.register(JLINK_DIST_ZIP_TASK_NAME, Zip.class, zip -> {
            zip.getArchiveBaseName().set(project.getName());
            if(project.getVersion() != null) {
                zip.getArchiveVersion().set(project.getVersion().toString());
            }
            zip.getDestinationDirectory().set(basePluginExtension.getDistsDirectory());
            zip.from(jlinTaskProvider);
        });

        Provider<Tar> jlinkTarTaskProvider = tasks.register(JLINK_DIST_TAR_TASK_NAME, Tar.class, zip -> {
            zip.getArchiveBaseName().set(project.getName());
            if(project.getVersion() != null) {
                zip.getArchiveVersion().set(project.getVersion().toString());
            }
            zip.getDestinationDirectory().set(basePluginExtension.getDistsDirectory());
            zip.from(jlinTaskProvider);
        });


        tasks.named(JLINK_TASK_NAME, JlinkTask.class, jlinkTask -> {
            jlinkTask.finalizedBy(jlinkZipTaskProvider);
            jlinkTask.finalizedBy(jlinkTarTaskProvider);
        });
    }
}
