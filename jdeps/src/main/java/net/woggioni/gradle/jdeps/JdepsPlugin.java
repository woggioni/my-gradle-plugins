package net.woggioni.gradle.jdeps;

import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.artifacts.ConfigurationContainer;
import org.gradle.api.file.FileCollection;
import org.gradle.api.plugins.JavaLibraryPlugin;
import org.gradle.api.plugins.JavaPlugin;
import org.gradle.api.provider.Provider;
import org.gradle.api.tasks.TaskContainer;

public class JdepsPlugin implements Plugin<Project> {
    public static final String JDEPS_TASK_NAME = "jdeps";

    @Override
    public void apply(Project project) {
        project.getPluginManager().apply(JavaLibraryPlugin.class);
        TaskContainer tasks = project.getTasks();
        Provider<JdepsTask> jdepsTaskProvider = tasks.register(JDEPS_TASK_NAME, JdepsTask.class, jdepsTask -> {
            ConfigurationContainer configurations = project.getConfigurations();
            FileCollection classpath = project.files(configurations.getByName(JavaPlugin.RUNTIME_CLASSPATH_CONFIGURATION_NAME));
            jdepsTask.getClasspath().set(classpath);
            jdepsTask.getArchives().set(project.files(tasks.named(JavaPlugin.JAR_TASK_NAME)));
        });
    }
}
