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

import net.woggioni.plugins.ExportDependenciesPluginExtension
import net.woggioni.plugins.RenderDependenciesPluginExtension

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

configure<ExportDependenciesPluginExtension> {
    configurationName = "runtime"
}

configure<RenderDependenciesPluginExtension> {
    format = "svg"
}
