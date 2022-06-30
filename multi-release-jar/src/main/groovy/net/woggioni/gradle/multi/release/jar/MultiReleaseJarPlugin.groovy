package net.woggioni.gradle.multi.release.jar

import org.gradle.api.GradleException
import org.gradle.api.JavaVersion
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.artifacts.Configuration
import org.gradle.api.attributes.LibraryElements
import org.gradle.api.attributes.java.TargetJvmVersion
import org.gradle.api.file.FileCollection
import org.gradle.api.file.SourceDirectorySet
import org.gradle.api.internal.plugins.DslObject
import org.gradle.api.model.ObjectFactory
import org.gradle.api.plugins.JavaPlugin
import org.gradle.api.plugins.JavaPluginExtension
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.Provider
import org.gradle.api.tasks.*
import org.gradle.api.tasks.compile.JavaCompile
import org.gradle.jvm.tasks.Jar
import org.gradle.process.CommandLineArgumentProvider

import java.lang.module.ModuleDescriptor
import java.util.jar.JarFile
import java.util.stream.Collectors
import java.util.zip.ZipFile

import static org.gradle.api.attributes.LibraryElements.JAR
import static org.gradle.api.attributes.LibraryElements.LIBRARY_ELEMENTS_ATTRIBUTE

class MultiReleaseJarPlugin implements Plugin<Project> {

    private static void jpmsModuleName(File file) {
        JarFile jarFile = new JarFile(file).with {
            if (it.isMultiRelease()) {
                new JarFile(
                        file,
                        false,
                        ZipFile.OPEN_READ,
                        Runtime.version()
                )
            } else {
                it
            }
        }
        String automaticModuleName = jarFile.manifest?.with {it.mainAttributes.getValue("Automatic-Module-Name") }
        def moduleInfoEntry = jarFile.getJarEntry("module-info.class")
        moduleInfoEntry
                ?.with(jarFile.&getInputStream)
                ?.withCloseable(ModuleDescriptor.&read) ?: automaticModuleName
        ModuleDescriptor.read()
    }

//    @Canonical
    static class CompilerArgumentProvider implements CommandLineArgumentProvider {

        private final Project project

        private final ObjectFactory objects

        @Input
        final Provider<String> jpmsModuleName

        private final Map<String, ListProperty<String>> patchModules

        @InputFiles
        @CompileClasspath
        final FileCollection sourceSetOutput

//        @InputFiles
//        FileCollection getPatchModules() {
//            return project.files(patchModules.entrySet().stream().flatMap {
//                it.getValue().get().stream()
//            }.toArray(String::new))
//        }

        CompilerArgumentProvider(
            Project project,
            ObjectFactory objects,
            Provider<String> jpmsModuleName,
            Map<String, ListProperty<String>> patchModules,
            FileCollection sourceSetOutput) {
            this.project = project
            this.objects = objects
            this.jpmsModuleName = jpmsModuleName
            this.patchModules = patchModules
            this.sourceSetOutput = sourceSetOutput
        }

        @Override
        Iterable<String> asArguments() {
            Map<String, ListProperty<String>> patchModules = new HashMap<>(patchModules)

            String name = jpmsModuleName.get()
            if(name) {
                patchModules.computeIfAbsent(name) {
                    objects.listProperty(String.class).convention(new ArrayList<String>())
                }.addAll(sourceSetOutput.collect { it.toString() })
            } else {
                throw new GradleException("Missing property 'jpms.module.name'")
            }
            String sep = System.getProperty('path.separator')
            List<String> result = new ArrayList<>()
            for(Map.Entry<String, ListProperty<String>> entry : patchModules.entrySet()) {
                String arg = entry.getValue().get().stream().collect(Collectors.joining(sep))
                result += '--patch-module'
                result += "${entry.getKey()}=${arg}"
            }
            result
        }
    }

    @Override
    void apply(Project project) {
        project.pluginManager.apply(JavaPlugin)
        MultiReleaseJarPluginExtension mrjpe = new MultiReleaseJarPluginExtension(project.objects)
        project.extensions.add('multiReleaseJar', mrjpe)
        JavaPluginExtension javaPluginExtension = project.extensions.findByType(JavaPluginExtension.class)
        JavaVersion binaryVersion = javaPluginExtension.targetCompatibility ?: javaPluginExtension.toolchain?.with {
            it.languageVersion.get()
        } ?: JavaVersion.current()
        if(binaryVersion > JavaVersion.VERSION_1_8) {
            Configuration compileClasspathConfiguration = project.configurations.compileClasspath
            project.configurations.named(JavaPlugin.COMPILE_CLASSPATH_CONFIGURATION_NAME) {
                attributes {
                    attribute(LIBRARY_ELEMENTS_ATTRIBUTE, project.objects.named(LibraryElements.class, JAR))
                }
            }

            SourceSet mainSourceSet = (project.sourceSets.main as SourceSet)
            JavaCompile compileJavaTask = project.tasks.named(JavaPlugin.COMPILE_JAVA_TASK_NAME, JavaCompile).get()
            compileJavaTask.configure {
                options.release.set(JavaVersion.VERSION_1_8.majorVersion.toInteger())
            }
            Jar jarTask = project.tasks.named(JavaPlugin.JAR_TASK_NAME, Jar).get()
            jarTask.configure {
                manifest.attributes('Multi-Release': 'true')
            }
            ArrayList<FileCollection> compileOutputs = new ArrayList<>()
            compileOutputs << compileJavaTask.outputs.files
            ArrayList<FileCollection> sourcePaths = new ArrayList<>()
            sourcePaths << mainSourceSet.java.sourceDirectories
            Arrays.stream(JavaVersion.values()).filter {
                it > JavaVersion.VERSION_1_8 && it <= binaryVersion
            }.forEach {javaVersion ->
                SourceDirectorySet sourceDirectorySet =
                        project.objects.sourceDirectorySet("java${javaVersion.majorVersion}", javaVersion.toString())
                sourceDirectorySet.with {
                    srcDir(new File(project.projectDir, "src/${mainSourceSet.name}/${sourceDirectorySet.name}"))
                    destinationDirectory.set(new File(project.buildDir, "classes/${sourceDirectorySet.name}"))
                    sourcePaths << sourceDirectories
                }
                new DslObject(mainSourceSet).getConvention().getPlugins().put(sourceDirectorySet.name, sourceDirectorySet)
                mainSourceSet.getExtensions().add(SourceDirectorySet.class, sourceDirectorySet.name, sourceDirectorySet)
                TaskProvider<JavaCompile> compileTask = project.tasks.register(JavaPlugin.COMPILE_JAVA_TASK_NAME + javaVersion.majorVersion, JavaCompile, { javaCompileTask ->
                    javaCompileTask.options.release.set(javaVersion.majorVersion.toInteger())
                    javaCompileTask.classpath = compileClasspathConfiguration + compileOutputs.stream().reduce { fc1, fc2 -> fc1 + fc2 }.get()
                    javaCompileTask.options.compilerArgumentProviders.add(
                        new CompilerArgumentProvider(
                            project,
                            project.objects,
                            project.provider {
                                project.hasProperty("jpms.module.name") ?
                                    project.property("jpms.module.name") : null
                            },
                            mrjpe.patchModules,
                            mainSourceSet.output
                        )
                    )
                    javaCompileTask.source = sourceDirectorySet
                    javaCompileTask.destinationDirectory.set(sourceDirectorySet.destinationDirectory)
                    javaCompileTask.options.annotationProcessorPath = mainSourceSet.annotationProcessorPath
                    javaCompileTask.modularity.inferModulePath = javaPluginExtension.modularity.inferModulePath
                    javaCompileTask.options.sourcepath = sourcePaths.stream().reduce { fc1, fc2 -> fc1 + fc2 }.get()
                    javaCompileTask.javaCompiler = compileJavaTask.javaCompiler
                })
                compileOutputs << compileTask.get().outputs.files
                sourceDirectorySet.compiledBy(compileTask, { it.getDestinationDirectory()})
                jarTask.configure {
                    from(compileTask.get().destinationDirectory) {
                        into("META-INF/versions/${javaVersion.majorVersion}")
                    }
                }

            }
            SourceSet testSourceSet = (project.sourceSets.test as SourceSet)
            testSourceSet.compileClasspath += compileOutputs.stream().reduce { fc1, fc2 -> fc1 + fc2 }.get()
            testSourceSet.runtimeClasspath += compileOutputs.stream().reduce { fc1, fc2 -> fc1 + fc2 }.get()

            ["apiElements", "runtimeElements"].forEach { String name ->
                Configuration conf = project.configurations.getByName(name)
                conf.attributes {
                    attribute(TargetJvmVersion.TARGET_JVM_VERSION_ATTRIBUTE, compileJavaTask.options.release.get())
                }
            }
        }
    }
}
