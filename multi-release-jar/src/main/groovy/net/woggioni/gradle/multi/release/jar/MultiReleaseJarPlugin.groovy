package net.woggioni.gradle.multi.release.jar

import org.gradle.api.GradleException
import org.gradle.api.JavaVersion
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.artifacts.Configuration
import org.gradle.api.attributes.java.TargetJvmVersion
import org.gradle.api.file.FileCollection
import org.gradle.api.file.SourceDirectorySet
import org.gradle.api.internal.plugins.DslObject
import org.gradle.api.plugins.JavaPlugin
import org.gradle.api.plugins.JavaPluginExtension
import org.gradle.api.tasks.SourceSet
import org.gradle.api.tasks.TaskProvider
import org.gradle.api.tasks.compile.JavaCompile
import org.gradle.jvm.tasks.Jar

class MultiReleaseJarPlugin implements Plugin<Project> {
    @Override
    void apply(Project project) {
        project.pluginManager.apply(JavaPlugin)
        JavaPluginExtension javaPluginExtension = project.extensions.findByType(JavaPluginExtension.class)
        JavaVersion binaryVersion = javaPluginExtension.targetCompatibility ?: javaPluginExtension.toolchain?.with {
            it.languageVersion.get()
        } ?: JavaVersion.current()
        if(binaryVersion > JavaVersion.VERSION_1_8) {
            Configuration compileClasspathConfiguration = project.configurations.compileClasspath
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
                    javaCompileTask.doFirst {
                        if(project.hasProperty("jpms.module.name")) {
                            javaCompileTask.options.compilerArgs << "--patch-module" <<
                                "${project.property("jpms.module.name")}=${mainSourceSet.output.asPath}"
                        } else {
                            throw new GradleException("Missing property 'jpms.module.name'")
                        }
                    }
                    javaCompileTask.source = sourceDirectorySet
                    javaCompileTask.destinationDirectory.set(sourceDirectorySet.destinationDirectory)
                    javaCompileTask.options.annotationProcessorPath = mainSourceSet.annotationProcessorPath
                    javaCompileTask.modularity.inferModulePath = javaPluginExtension.modularity.inferModulePath
                    javaCompileTask.options.sourcepath = sourcePaths.stream().reduce { fc1, fc2 -> fc1 + fc2 }.get()
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
