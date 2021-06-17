package net.woggioni.gradle.lombok;

import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.Task;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.artifacts.dsl.DependencyHandler;
import org.gradle.api.model.ObjectFactory;
import org.gradle.api.plugins.JavaPlugin;
import org.gradle.api.plugins.JavaPluginConvention;
import org.gradle.api.provider.Provider;
import org.gradle.api.tasks.SourceSet;
import org.gradle.api.tasks.SourceSetContainer;
import org.gradle.api.tasks.TaskContainer;
import org.gradle.api.tasks.javadoc.Javadoc;

import java.io.File;
import java.util.Map;

public class LombokPlugin implements Plugin<Project> {
    @Override
    public void apply(Project project) {
        project.getPluginManager().apply(JavaPlugin.class);
        ObjectFactory objectFactory = project.getObjects();
        LombokExtension ext = project.getExtensions()
                .create("lombok", LombokExtension.class,
                        objectFactory.property(String.class).convention("1.18.20")
                );
        JavaPluginConvention javaPluginConvention = project.getConvention().getPlugin(JavaPluginConvention.class);
        SourceSetContainer sourceSetContainer = javaPluginConvention.getSourceSets();
        project.afterEvaluate(p -> {
            Provider<Map<String, String>> dependencyNotationProvider = project.provider(() ->
                    Map.of("group", "org.projectlombok",
                            "name", "lombok",
                            "version", ext.getVersion().get())
            );
            Configuration lombokConfiguration = project.getConfigurations().create("lombok");
            project.getDependencies().addProvider(
                    lombokConfiguration.getName(),
                    dependencyNotationProvider,
                    externalModuleDependency -> {
                    }
            );
            for (SourceSet ss : sourceSetContainer) {
                DependencyHandler dependencyHandler = project.getDependencies();
                dependencyHandler.addProvider(
                        ss.getCompileOnlyConfigurationName(),
                        dependencyNotationProvider,
                        externalModuleDependency -> {
                        });
                dependencyHandler.addProvider(
                        ss.getAnnotationProcessorConfigurationName(),
                        dependencyNotationProvider,
                        externalModuleDependency -> {
                        });
                TaskContainer tasks = project.getTasks();
                String javadocTaskName = ss.getJavadocTaskName();
                Task javadocTask = tasks.findByName(javadocTaskName);
                if(javadocTask != null) {
                    String delombokTaskName = "delombok" + ss.getName().substring(0, 1).toUpperCase() + ss.getName().substring(1);
                    File outputDir = new File(new File(project.getBuildDir(), "delombok"), ss.getName());
                    Provider<Delombok> delombokTask = tasks.register(delombokTaskName,
                            Delombok.class,
                            lombokConfiguration.getSingleFile(),
                            outputDir,
                            ss.getJava().getSrcDirs(),
                            ss.getCompileClasspath().getAsPath()
                    );
                    Javadoc javadoc = (Javadoc) javadocTask;
                    javadoc.setSource(outputDir);
                    javadoc.getInputs().files(delombokTask);
                }
            }
        });
    }
}
