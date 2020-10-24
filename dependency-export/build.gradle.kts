plugins {
    `java-gradle-plugin`
    `maven-publish`
    id("org.jetbrains.kotlin.jvm")
    id("com.gradle.plugin-publish")
}

dependencies {
    // Align versions of all Kotlin components
    implementation(platform("org.jetbrains.kotlin:kotlin-bom"))

    // Use the Kotlin JDK 8 standard library.
    implementation("org.jetbrains.kotlin:kotlin-stdlib-jdk8")
}

gradlePlugin {
    val dependencyExportPlugin by plugins.creating {
        id = "net.woggioni.plugins.dependency-export"
        implementationClass = "net.woggioni.plugins.dependency.export.DependencyExportPlugin"
    }
}
