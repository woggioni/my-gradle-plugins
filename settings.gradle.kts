pluginManagement {
    plugins {
        id("org.jetbrains.kotlin.jvm") version "1.3.72" apply false
        id("com.gradle.plugin-publish") version "0.10.1" apply false
    }
}

rootProject.name = "my-gradle-plugins"
include("dependency-export")
include("jpms-check")
include("multi-release-jar")
