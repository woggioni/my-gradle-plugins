buildscript {
    repositories {
        mavenLocal()
        jcenter()
        mavenCentral()
    }
    dependencies {
        classpath("net.woggioni.plugins:dependency-export:0.1")
    }
}

import net.woggioni.plugins.DependencyExportPluginExtension

plugins {
    kotlin("jvm") version "1.3.71"
    id("net.woggioni.plugins.dependency-export") version "0.1"
}

repositories {
    jcenter()
    mavenLocal()
}

dependencies {
    runtime("org.hibernate:hibernate-core:5.4.13.Final")
}

configure<DependencyExportPluginExtension> {
    configurationName = "runtime"
}
