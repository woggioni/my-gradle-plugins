plugins {
    kotlin("jvm") version "1.3.71"
    id("net.woggioni.plugins.dependency-export")
}

repositories {
    jcenter()
    mavenLocal()
}

dependencies {
    runtime("org.hibernate:hibernate-core:5.4.13.Final")
}

