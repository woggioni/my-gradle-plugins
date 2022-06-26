package net.woggioni.gradle.multi.release.jar;

import org.gradle.api.Action;
import org.gradle.api.GradleException;
import org.gradle.api.JavaVersion;
import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.Task;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.artifacts.ConfigurationContainer;
import org.gradle.api.attributes.LibraryElements;
import org.gradle.api.file.FileCollection;
import org.gradle.api.model.ObjectFactory;
import org.gradle.api.plugins.JavaPlugin;
import org.gradle.api.plugins.JavaPluginExtension;
import org.gradle.api.provider.Property;
import org.gradle.api.provider.Provider;
import org.gradle.api.tasks.SourceSet;
import org.gradle.api.tasks.SourceSetContainer;
import org.gradle.api.tasks.TaskContainer;
import org.gradle.api.tasks.TaskProvider;
import org.gradle.api.tasks.compile.CompileOptions;
import org.gradle.api.tasks.compile.JavaCompile;
import org.gradle.jvm.tasks.Jar;
import org.gradle.jvm.toolchain.JavaLanguageVersion;
import org.gradle.jvm.toolchain.JavaToolchainSpec;

import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.gradle.api.attributes.LibraryElements.JAR;
import static org.gradle.api.attributes.LibraryElements.LIBRARY_ELEMENTS_ATTRIBUTE;

public class MultiVersionJarPlugin implements Plugin<Project> {

    private void dop(Project project) {
        JavaPluginExtension javaExtension = project.getExtensions().getByType(JavaPluginExtension.class);
        JavaVersion binaryVersion =
                Optional.ofNullable(javaExtension.getToolchain())
                        .map(JavaToolchainSpec::getLanguageVersion)
                        .filter(Property<JavaLanguageVersion>::isPresent)
                        .map(Property<JavaLanguageVersion>::get)
                        .map(jls -> JavaVersion.toVersion(jls.toString()))
                        .orElseGet(() -> Optional.ofNullable(
                                javaExtension.getTargetCompatibility()).orElseGet(JavaVersion::current)
                        );

        if (JavaVersion.VERSION_1_8.compareTo(binaryVersion) < 0) {
            ObjectFactory objects = project.getObjects();
            TaskContainer tasks = project.getTasks();
            SourceSetContainer sourceSets = javaExtension.getSourceSets();
            ConfigurationContainer configurations = project.getConfigurations();
            configurations.named(JavaPlugin.COMPILE_CLASSPATH_CONFIGURATION_NAME, conf -> {
                conf.attributes(attrs -> {
                    attrs.attribute(LIBRARY_ELEMENTS_ATTRIBUTE, objects.named(LibraryElements.class, JAR));
                });
            });
            String[] lastConfigurationName = new String[] { JavaPlugin.COMPILE_CLASSPATH_CONFIGURATION_NAME };
            SourceSet mainSourceSet = sourceSets.getByName("main");
            ArrayList<FileCollection> compileOutputs = new ArrayList<>();
            compileOutputs.add(tasks.getByName(mainSourceSet.getCompileJavaTaskName())
                    .getOutputs().getFiles());
            ArrayList<FileCollection> sourcePaths = new ArrayList<>();
            sourcePaths.add(mainSourceSet.getJava().getSourceDirectories());
            Configuration compileClasspathConfiguration =
                    configurations.getByName(JavaPlugin.COMPILE_CLASSPATH_CONFIGURATION_NAME);

            Provider<Jar> jarTaskProvider = tasks.named(JavaPlugin.JAR_TASK_NAME, Jar.class, jarTask -> {
                Map<String, String> m = Stream.of(
                        new AbstractMap.SimpleEntry<>("Multi-Release", "true")
                ).collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
                jarTask.getManifest().attributes(m);
            });

            project.getTasks().named(JavaPlugin.COMPILE_JAVA_TASK_NAME, JavaCompile.class, t -> {
                t.exclude("module-info.java");
                t.getOptions().getRelease().set(Integer.parseInt(JavaVersion.VERSION_1_8.getMajorVersion()));
            });
            Arrays.stream(JavaVersion.values())
                    .filter(v -> v.compareTo(JavaVersion.VERSION_1_8) > 0)
                    .filter(v -> v.compareTo(binaryVersion) <= 0)
                    .forEach(javaVersion -> {
                        String sourceSetName = "java" + javaVersion.getMajorVersion();
                        SourceSet sourceSet = sourceSets.create(sourceSetName);
                        TaskProvider<JavaCompile> compileTask = tasks.register(
                                JavaPlugin.COMPILE_JAVA_TASK_NAME + javaVersion.getMajorVersion(),
                                JavaCompile.class,
                                (JavaCompile javaCompileTask) -> {
                                    javaCompileTask.getOptions().getRelease().set(Integer.parseInt(javaVersion.getMajorVersion()));
                                    javaCompileTask.setClasspath(
                                            compileClasspathConfiguration.plus(compileOutputs.stream().reduce(FileCollection::plus)
                                                    .orElseGet(project::files)));
                                    compileOutputs.add(javaCompileTask.getOutputs().getFiles());
                                    javaCompileTask.source(sourceSet.getAllJava().getSourceDirectories());
                                    javaCompileTask.getDestinationDirectory().set(sourceSet.getJava().getDestinationDirectory());
                                    javaCompileTask.getOptions().setAnnotationProcessorPath(sourceSet.getAnnotationProcessorPath());
                                    javaCompileTask.getModularity().getInferModulePath().set(javaExtension.getModularity().getInferModulePath());
                                    CompileOptions options = javaCompileTask.getOptions();
                                    options.setSourcepath(sourceSet.getJava().getSourceDirectories());
//                                    sourcePaths.stream()
//                                            .reduce(FileCollection::plus)
//                                            .ifPresent(options::setSourcepath);
                                    sourcePaths.add(sourceSet.getJava().getSourceDirectories());
                                    sourceSet.compiledBy(javaCompileTask);
                                    Optional<String> patchPath = compileOutputs.stream()
                                            .reduce(FileCollection::plus).map(FileCollection::getAsPath);

                                    javaCompileTask.doFirst(new Action<Task>() {
                                        @Override
                                        public void execute(Task javaCompile) {
                                            if (project.hasProperty("jpms.module.name")) {
                                                patchPath.ifPresent(p -> {
                                                    List<String> compilerArgs = javaCompileTask.getOptions().getCompilerArgs();
                                                    compilerArgs.add("--patch-module");
                                                    compilerArgs.add(project.property("jpms.module.name").toString() + '=' + p);
                                                });
                                            } else {
                                                throw new GradleException("Missing property 'jpms.module.name'");
                                            }
                                        }
                                    });
                                });
                        tasks.named(JavaPlugin.JAR_TASK_NAME, Jar.class, jarTask -> {
                            jarTask.from(compileTask.get().getDestinationDirectory(), cp -> {
                                cp.into("META-INF/versions/" + javaVersion.getMajorVersion());
                            });
                        });

                        String extendFormConfiguration = lastConfigurationName[0];
                        String confName = sourceSetName + JavaPlugin.COMPILE_CLASSPATH_CONFIGURATION_NAME;
                        lastConfigurationName[0] = confName;
                        configurations.register(confName, conf -> {
                            conf.attributes(attrs -> {
                                attrs.attribute(LIBRARY_ELEMENTS_ATTRIBUTE, objects.named(LibraryElements.class, JAR));
                            });
                            conf.extendsFrom(configurations.getByName(extendFormConfiguration));
                        });
                    });
        }
    }

    @Override
    public void apply(Project project) {
        project.getPluginManager().withPlugin("java-library", plugin -> dop(project));
    }
}
