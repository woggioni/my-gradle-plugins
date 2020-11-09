package net.woggioni.plugins.multi.release.jar

import org.gradle.api.JavaVersion
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.artifacts.Configuration
import org.gradle.api.attributes.java.TargetJvmVersion
import org.gradle.api.file.FileCollection
import org.gradle.api.file.SourceDirectorySet
import org.gradle.api.plugins.Convention
import org.gradle.api.plugins.JavaBasePlugin
import org.gradle.api.plugins.JavaPluginConvention
import org.gradle.api.tasks.SourceSet
import org.gradle.api.tasks.TaskProvider
import org.gradle.api.tasks.compile.JavaCompile
import org.gradle.internal.impldep.org.apache.commons.compress.compressors.z.ZCompressorInputStream
import org.gradle.jvm.tasks.Jar

class MultiReleaseJarPlugin implements Plugin<Project> {
    @Override
    void apply(Project project) {
        project.pluginManager.apply(JavaBasePlugin)
        if(JavaVersion.current() > JavaVersion.VERSION_1_8) {
            SourceSet mainSourceSet = (project.sourceSets.main as SourceSet)
            JavaCompile compileJavaTask = project.tasks.named("compileJava", JavaCompile).get()
            compileJavaTask.configure {
                options.release.set(JavaVersion.VERSION_1_8.majorVersion.toInteger())
            }
            Jar jarTask = project.tasks.named("jar", Jar).get()
            jarTask.configure {
                manifest.attributes('Multi-Release': 'true')
            }
            ArrayList<FileCollection> compileOutputs = new ArrayList<>()
            compileOutputs << compileJavaTask.outputs.files
            ArrayList<FileCollection> sourcePaths = new ArrayList<>()
            sourcePaths << mainSourceSet.java.sourceDirectories
            Arrays.stream(JavaVersion.values()).filter {
                it > JavaVersion.VERSION_1_8 && it <= JavaVersion.current()
            }.forEach {javaVersion ->
                SourceDirectorySet sourceDirectorySet =
                    project.objects.sourceDirectorySet("java${javaVersion.majorVersion}", javaVersion.toString())
                sourceDirectorySet.with {
                    srcDir(new File(project.projectDir, "src/main/${sourceDirectorySet.name}"))
                    destinationDirectory.set(new File(project.buildDir, "classes/${sourceDirectorySet.name}"))
                    sourcePaths << sourceDirectories
                }
                TaskProvider<JavaCompile> compileTask = project.tasks.register("compileJava${javaVersion.majorVersion}", JavaCompile, {
                    options.release.set(javaVersion.majorVersion.toInteger())
                    classpath = compileOutputs.stream().reduce { fc1, fc2 -> fc1 + fc2 }.get()
                    it.doFirst {
                        options.compilerArgs << "--module-path" << mainSourceSet.compileClasspath.asPath
                    }
                    source = sourceDirectorySet
                    destinationDirectory.set(sourceDirectorySet.destinationDirectory)
                    options.annotationProcessorPath = mainSourceSet.annotationProcessorPath
                    modularity.inferModulePath = false
                    options.sourcepath = sourcePaths.stream().reduce { fc1, fc2 -> fc1 + fc2 }.get()
                })
                compileOutputs << compileJavaTask.outputs.files
                sourceDirectorySet.compiledBy(compileTask, { it.getDestinationDirectory()})
                jarTask.configure { Jar it ->
                    from(compileTask.get().outputs.files) {
                        into("META-INF/versions/${javaVersion.majorVersion}")
                    }
                }
            }
            ["apiElements", "runtimeElements"].forEach {String name ->
                Configuration conf = project.configurations.getByName(name)
                conf.attributes {
                    attribute(TargetJvmVersion.TARGET_JVM_VERSION_ATTRIBUTE, compileJavaTask.options.release.get())
                }
            }
        }
    }
}
