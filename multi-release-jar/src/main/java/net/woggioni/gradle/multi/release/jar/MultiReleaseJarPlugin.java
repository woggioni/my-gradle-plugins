package net.woggioni.gradle.multi.release.jar;

import lombok.Getter;
import org.gradle.api.GradleException;
import org.gradle.api.JavaVersion;
import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.attributes.LibraryElements;
import org.gradle.api.attributes.java.TargetJvmVersion;
import org.gradle.api.file.FileCollection;
import org.gradle.api.file.SourceDirectorySet;
import org.gradle.api.internal.plugins.DslObject;
import org.gradle.api.plugins.JavaPlugin;
import org.gradle.api.plugins.JavaPluginExtension;
import org.gradle.api.provider.MapProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.provider.Provider;
import org.gradle.api.tasks.CompileClasspath;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.InputFiles;
import org.gradle.api.tasks.SourceSet;
import org.gradle.api.tasks.SourceSetContainer;
import org.gradle.api.tasks.TaskProvider;
import org.gradle.api.tasks.compile.AbstractCompile;
import org.gradle.api.tasks.compile.JavaCompile;
import org.gradle.jvm.tasks.Jar;
import org.gradle.jvm.toolchain.JavaLanguageVersion;
import org.gradle.jvm.toolchain.JavaToolchainSpec;
import org.gradle.process.CommandLineArgumentProvider;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

import static org.gradle.api.attributes.LibraryElements.JAR;
import static org.gradle.api.attributes.LibraryElements.LIBRARY_ELEMENTS_ATTRIBUTE;

public class MultiReleaseJarPlugin implements Plugin<Project> {

//    @SneakyThrows
//    private static void jpmsModuleName(File file) {
//        JarFile jarFile = new JarFile(file);
//        if (jarFile.isMultiRelease()) {
//            jarFile = new JarFile(
//                file,
//                false,
//                ZipFile.OPEN_READ,
//                Runtime.version()
//            );
//        }
//        String automaticModuleName = Optional.ofNullable(jarFile.getManifest())
//            .map(Manifest::getMainAttributes)
//            .map(mainAttr -> mainAttr.getValue("Automatic-Module-Name"))
//            .orElse(null);
//        Optional<JarEntry> moduleInfoEntry = Optional.ofNullable(jarFile.getJarEntry("module-info.class"));
//        moduleInfoEntry
//            .map(jarFile::getInputStream)
//            .map(ModuleDescriptor::read)
//            .orElse(automaticModuleName);
//    }

    static class CompilerArgumentProvider implements CommandLineArgumentProvider {

        @Getter(onMethod_ = {@Input})
        private final Provider<String> jpmsModuleName;

        @Getter(onMethod_ = {@Input})
        private final MapProperty<String, List<String>> patchModules;

        @Getter(onMethod_ = {@InputFiles, @CompileClasspath})
        private final FileCollection sourceSetOutput;   

        public CompilerArgumentProvider(
            Provider<String> jpmsModuleName,
            MapProperty<String, List<String>> patchModules,
            FileCollection sourceSetOutput) {
            this.jpmsModuleName = jpmsModuleName;
            this.patchModules = patchModules;
            this.sourceSetOutput = sourceSetOutput;
        }

        @Override
        public Iterable<String> asArguments() {
            Map<String, List<String>> patchModules = new TreeMap<>(this.patchModules.get());

            if (jpmsModuleName.isPresent()) {
                String name = jpmsModuleName.get();
                patchModules.computeIfAbsent(name, k -> new ArrayList<>())
                    .addAll(
                        StreamSupport.stream(sourceSetOutput.spliterator(), false)
                            .map(File::toString)
                            .collect(Collectors.toList())
                    );
            } else {
                throw new GradleException("Missing property 'jpms.module.name'");
            }
            String sep = System.getProperty("path.separator");
            List<String> result = new ArrayList<>();
            for (Map.Entry<String, List<String>> entry : patchModules.entrySet()) {
                String arg = String.join(sep, entry.getValue());
                result.add("--patch-module");
                result.add(entry.getKey() + "=" + arg);
            }
            return result;
        }
    }

    @Override
    public void apply(Project project) {
        project.getPluginManager().apply(JavaPlugin.class);
        MultiReleaseJarPluginExtension mrjpe = project.getObjects().newInstance(MultiReleaseJarPluginExtension.class);
        project.getExtensions().add("multiReleaseJar", mrjpe);
        JavaPluginExtension javaPluginExtension = project.getExtensions().findByType(JavaPluginExtension.class);
        SourceSetContainer ssc = javaPluginExtension.getSourceSets();
        JavaVersion binaryVersion = Optional.ofNullable(javaPluginExtension.getTargetCompatibility())
            .or(() -> Optional.ofNullable(javaPluginExtension.getToolchain())
                .map(JavaToolchainSpec::getLanguageVersion)
                .filter(Property::isPresent)
                .map(Property::get)
                .map(JavaLanguageVersion::asInt)
                .map(JavaVersion::toVersion)
            ).orElseGet(JavaVersion::current);
        if (Comparator.<JavaVersion>naturalOrder().compare(binaryVersion, JavaVersion.VERSION_1_8) > 0) {
            Configuration compileClasspathConfiguration = project.getConfigurations()
                .getByName(JavaPlugin.COMPILE_CLASSPATH_CONFIGURATION_NAME);
            project.getConfigurations().named(JavaPlugin.COMPILE_CLASSPATH_CONFIGURATION_NAME, cfg -> {
                cfg.attributes(attr -> {
                    attr.attribute(LIBRARY_ELEMENTS_ATTRIBUTE, project.getObjects().named(LibraryElements.class, JAR));
                });
            });

            SourceSet mainSourceSet = ssc.getByName(SourceSet.MAIN_SOURCE_SET_NAME);

            JavaCompile compileJavaTask = project.getTasks()
                .named(JavaPlugin.COMPILE_JAVA_TASK_NAME, JavaCompile.class, (JavaCompile t) -> {
                    t.getOptions().getRelease().set(Integer.parseInt(JavaVersion.VERSION_1_8.getMajorVersion()));
                }).get();
            Jar jarTask = project.getTasks().named(JavaPlugin.JAR_TASK_NAME, Jar.class, (Jar t) -> {
                Map<String, String> attrs = new HashMap<>();
                attrs.put("Multi-Release", "true");
                t.getManifest().attributes(attrs);
            }).get();
            SourceDirectorySet java = mainSourceSet.getJava();
            ArrayList<FileCollection> compileOutputs = new ArrayList<>();
            compileOutputs.add(compileJavaTask.getOutputs().getFiles());
            ArrayList<FileCollection> sourcePaths = new ArrayList<>();
            sourcePaths.add(java.getSourceDirectories());
            Comparator<JavaVersion> cmp = Comparator.naturalOrder();
            Arrays.stream(JavaVersion.values())
                .filter((JavaVersion jv) -> cmp.compare(jv, JavaVersion.VERSION_1_8) > 0 && cmp.compare(jv, binaryVersion) <= 0)
                .forEach(javaVersion -> {
                    SourceDirectorySet sourceDirectorySet = project.getObjects()
                        .sourceDirectorySet("java" + javaVersion.getMajorVersion(), javaVersion.toString());
                    sourceDirectorySet.srcDir(new File(project.getProjectDir(),
                        "src/" + mainSourceSet.getName() + "/" + sourceDirectorySet.getName()));
                    sourceDirectorySet.getDestinationDirectory().set(
                        new File(project.getBuildDir(),
                            "classes/" + mainSourceSet.getName() + "/" + sourceDirectorySet.getName())
                    );
                    sourcePaths.add(sourceDirectorySet.getSourceDirectories());
                    new DslObject(mainSourceSet).getConvention().getPlugins().put(sourceDirectorySet.getName(), sourceDirectorySet);
                    mainSourceSet.getExtensions().add(SourceDirectorySet.class, sourceDirectorySet.getName(), sourceDirectorySet);
                    TaskProvider<JavaCompile> compileTask =
                        project.getTasks().register(JavaPlugin.COMPILE_JAVA_TASK_NAME + javaVersion.getMajorVersion(), JavaCompile.class,
                            (JavaCompile javaCompileTask) -> {
                                javaCompileTask.getOptions().getRelease().set(Integer.parseInt(javaVersion.getMajorVersion()));
                                javaCompileTask.setClasspath(compileClasspathConfiguration.plus(
                                    compileOutputs.stream().reduce(project.getObjects().fileCollection(), FileCollection::plus)));
                                javaCompileTask.getOptions().getCompilerArgumentProviders().add(
                                    new CompilerArgumentProvider(
                                        project.provider(() -> Optional.of("jpms.module.name")
                                            .filter(project::hasProperty)
                                            .map(project::property)
                                            .map(Object::toString)
                                            .orElse(null)),
                                        mrjpe.getPatchModules(),
                                        mainSourceSet.getOutput()
                                    )
                                );
                                javaCompileTask.source(sourceDirectorySet);
                                javaCompileTask.getDestinationDirectory().set(sourceDirectorySet.getDestinationDirectory());
                                javaCompileTask.getOptions().setAnnotationProcessorPath(mainSourceSet.getAnnotationProcessorPath());
                                javaCompileTask.getModularity().getInferModulePath().set(javaPluginExtension.getModularity().getInferModulePath());
                                javaCompileTask.getOptions().setSourcepath(sourcePaths.stream().reduce(project.files(), FileCollection::plus));
                                javaCompileTask.getJavaCompiler().set(compileJavaTask.getJavaCompiler());
                            });
                    compileOutputs.add(compileTask.get().getOutputs().getFiles());
                    sourceDirectorySet.compiledBy(compileTask, AbstractCompile::getDestinationDirectory);
                    jarTask.from(compileTask.get().getDestinationDirectory(), copySpec -> {
                        copySpec.into("META-INF/versions/" + javaVersion.getMajorVersion());
                    });
                });

            SourceSet testSourceSet = ssc.getByName(SourceSet.TEST_SOURCE_SET_NAME);
            testSourceSet.setCompileClasspath(
                testSourceSet.getCompileClasspath().plus(compileOutputs.stream().reduce(project.files(), FileCollection::plus))
            );
            testSourceSet.setRuntimeClasspath(
                testSourceSet.getRuntimeClasspath().plus(compileOutputs.stream().reduce(project.files(), FileCollection::plus))
            );
            Arrays.asList("apiElements", "runtimeElements").forEach((String name) -> {
                Configuration conf = project.getConfigurations().getByName(name);
                conf.attributes(attrs -> {
                    attrs.attribute(TargetJvmVersion.TARGET_JVM_VERSION_ATTRIBUTE,
                        compileJavaTask.getOptions().getRelease().get());
                });
            });
        }
    }
}
