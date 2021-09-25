plugins {
    id("net.woggioni.gradle.dependency-export")
}

repositories {
    mavenCentral()
}

dependencies {
    runtime("org.hibernate:hibernate-core:5.4.13.Final")
}

configure<ExportDependenciesPluginExtension> {
    configurationName = "runtimeClassapath"
}

configure<RenderDependenciesPluginExtension> {
    format = "svg"
}
