plugins {
    `java-gradle-plugin`
    `maven-publish`
    id("com.gradle.plugin-publish")
}

dependencies {
}

java {
    sourceCompatibility = JavaVersion.VERSION_1_8
    targetCompatibility = JavaVersion.VERSION_1_8
}

arrayOf("apiElements", "runtimeElements").forEach { name : String ->
    val conf = project.configurations.getByName(name)
    conf.attributes {
        attribute(TargetJvmVersion.TARGET_JVM_VERSION_ATTRIBUTE, 8)
    }
}


gradlePlugin {
    val dependencyExportPlugin by plugins.creating {
        id = "net.woggioni.gradle.dependency-export"
        implementationClass = "net.woggioni.gradle.dependency.export.DependencyExportPlugin"
    }
}
