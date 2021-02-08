buildscript {
    repositories {
        mavenLocal()
        jcenter()
        mavenCentral()
    }
    dependencies {
        classpath("net.woggioni.gradle:dependency-export:0.1")
    }
}

plugins {
    id("net.woggioni.gradle.dependency-export") version "0.1"
}

repositories {
    jcenter()
    mavenLocal()
}

dependencies {
    runtime("org.hibernate:hibernate-core:5.4.13.Final")
}

configure<ExportDependenciesPluginExtension> {
    configurationName = "runtime"
}

configure<RenderDependenciesPluginExtension> {
    format = "svg"
}
