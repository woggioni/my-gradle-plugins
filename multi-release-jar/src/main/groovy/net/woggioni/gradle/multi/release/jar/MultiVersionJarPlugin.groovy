package net.woggioni.gradle.multi.release.jar

import org.gradle.api.JavaVersion
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.artifacts.Configuration
import org.gradle.api.attributes.java.TargetJvmVersion
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.file.SourceDirectorySet
import org.gradle.api.plugins.JavaBasePlugin
import org.gradle.api.provider.Provider
import org.gradle.api.tasks.SourceSet
import org.gradle.api.tasks.compile.JavaCompile
import org.gradle.jvm.tasks.Jar

class MultiVersionJarPlugin implements Plugin<Project> {

    @Override
    void apply(Project project) {
        project.apply(to: JavaBasePlugin)
        if(JavaVersion.current() > JavaVersion.VERSION_1_8) {
            SourceSet mainSourceSet = (project.sourceSets.main as SourceSet)
            SourceDirectorySet javaSet = mainSourceSet.java
            File java8ClassesDir = project.buildDir.toPath().resolve("classes/java8").toFile()
            File java11ClassesDir = project.buildDir.toPath().resolve("classes/java11").toFile()
            JavaCompile compileJava8Task = project.tasks.register("compileJava8", JavaCompile, {
                exclude("module-info.java")
                options.release = JavaVersion.VERSION_1_8.majorVersion.toInteger()
                classpath = mainSourceSet.compileClasspath
                source = javaSet
                destinationDirectory = java8ClassesDir
                options.annotationProcessorPath = mainSourceSet.annotationProcessorPath
                modularity.inferModulePath = false
            }).get()

            JavaCompile compileModuleInfoTask = project.tasks.register("compileModuleInfo", JavaCompile, {
                include("module-info.java")
                options.release = JavaVersion.VERSION_11.majorVersion.toInteger()
                classpath = mainSourceSet.compileClasspath
                source = (project.sourceSets.main as SourceSet).java
                destinationDirectory = java11ClassesDir
                options.annotationProcessorPath = mainSourceSet.annotationProcessorPath
                modularity.inferModulePath = true
            }).get()

            Provider<Jar> jarTaskProvider = project.tasks.named("jar", Jar)
            Provider<Jar> multiVersionJarTaskProvider = project.tasks.register("multiVersionJar", Jar) {
                Jar jarTask = jarTaskProvider.get()
                from(compileJava8Task.outputs.files)
                from(compileModuleInfoTask.outputs.files)
                archiveBaseName = jarTask.archiveBaseName
                destinationDirectory = jarTask.destinationDirectory
                archiveExtension = jarTask.archiveExtension
                manifest = jarTask.manifest
            }

            jarTaskProvider.configure {
                actions = []
                Jar multiVersionJarTask = multiVersionJarTaskProvider.get()
                from(multiVersionJarTask.outputs.files)
                (it.archiveFile as RegularFileProperty).set(multiVersionJarTask.archiveFile)
            }
            ["apiElements", "runtimeElements"].forEach {String name ->
                Configuration conf = project.configurations.getByName(name)
                conf.attributes {
                    attribute(TargetJvmVersion.TARGET_JVM_VERSION_ATTRIBUTE, 8)
                }
            }
        }
        project.tasks.named("compileJava", JavaCompile) {
            if(JavaVersion.current() > JavaVersion.VERSION_1_8) {
                modularity.inferModulePath = true
            } else {
                exclude("module-info.java")
            }
        }

    }
}