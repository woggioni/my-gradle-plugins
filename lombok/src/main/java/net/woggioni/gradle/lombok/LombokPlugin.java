package net.woggioni.gradle.lombok;

import org.gradle.api.JavaVersion;
import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.Task;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.artifacts.dsl.DependencyHandler;
import org.gradle.api.plugins.ExtraPropertiesExtension;
import org.gradle.api.plugins.JavaPlugin;
import org.gradle.api.plugins.JavaPluginExtension;
import org.gradle.api.provider.Provider;
import org.gradle.api.tasks.SourceSet;
import org.gradle.api.tasks.SourceSetContainer;
import org.gradle.api.tasks.TaskContainer;
import org.gradle.api.tasks.TaskProvider;
import org.gradle.api.tasks.compile.JavaCompile;
import org.gradle.api.tasks.javadoc.Javadoc;
import org.gradle.jvm.toolchain.JavaToolchainSpec;

import java.io.File;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

public class LombokPlugin implements Plugin<Project> {
    @Override
    public void apply(Project project) {
        project.getPluginManager().apply(JavaPlugin.class);
        LombokExtension ext = project.getExtensions().create("lombok", LombokExtension.class);
        ExtraPropertiesExtension epe = project.getExtensions().getExtraProperties();
        if(epe.has("version.lombok")) {
            ext.getVersion().convention(
                project.provider(() -> (String) epe.get("version.lombok"))
            );
        }
        JavaPluginExtension javaPluginExtension = project.getExtensions().findByType(JavaPluginExtension.class);
        SourceSetContainer sourceSetContainer = javaPluginExtension.getSourceSets();
        Provider<Map<String, String>> dependencyNotationProvider = ext.getVersion().map((String version) -> {
             Map<String, String> m = new HashMap<>();
             m.put("group", "org.projectlombok");
             m.put("name", "lombok");
             m.put("version", version);
             return Collections.unmodifiableMap(m);
        });
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
                Javadoc javadoc = (Javadoc) javadocTask;
                TaskProvider<Delombok> delombokTaskProvider = tasks.register(delombokTaskName, Delombok.class, (delombok -> {
                    delombok.getSourceSet().set(ss);
                    delombok.getOutputDir().set(outputDir);
                    delombok.getLombokJar().set(lombokConfiguration);
                    // Disabled for now due to https://github.com/projectlombok/lombok/issues/2829
                    //delombok.getInferModulePath().set(javaPluginExtension.getModularity().getInferModulePath());
                    delombok.getInferModulePath().set(false);
                }));
                javadoc.setSource(outputDir);
                javadoc.getInputs().files(delombokTaskProvider);
            }
        }
        JavaToolchainSpec toolchain = javaPluginExtension.getToolchain();
        if(toolchain.getLanguageVersion().isPresent()) {
            project.afterEvaluate((Project pro) -> {
                if(toolchain.getLanguageVersion().get().asInt() >= 16) {
                    pro.getTasks().withType(JavaCompile.class, t -> {
                        t.getOptions().getForkOptions().getJvmArgs().add("--illegal-access=permit");
                    });
                }
            });
        } else if(JavaVersion.current().compareTo(JavaVersion.VERSION_16) >= 0) {
            project.getTasks().withType(JavaCompile.class, t -> {
                t.getOptions().getForkOptions().getJvmArgs().add("--illegal-access=permit");
            });
        }
    }
}
