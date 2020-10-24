plugins {
    `java-gradle-plugin`
    `maven-publish`
    groovy
    id("com.gradle.plugin-publish")
}

dependencies {
}

gradlePlugin {
    val dependencyExportPlugin by plugins.creating {
        id = "net.woggioni.plugins.jpms-check"
        implementationClass = "net.woggioni.plugins.jpms.check.JPMSCheckPlugin"
    }
}