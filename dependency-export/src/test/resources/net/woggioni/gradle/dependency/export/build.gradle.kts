plugins {
    id("java-library")
    id("net.woggioni.gradle.dependency-export")
}

repositories {
    jcenter()
    mavenLocal()
}

dependencies {
    runtimeOnly("org.hibernate:hibernate-core:5.4.13.Final")
}

tasks.named<net.woggioni.gradle.dependency.export.ExportDependencies>("exportDependencies") {
    configurationName.set("compileClasspath")
}
