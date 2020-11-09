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
    plugins {
        create("DependencyExportPlugin") {
            id = "net.woggioni.plugins.dependency-export"
            implementationClass = "net.woggioni.plugins.dependency.export.DependencyExportPlugin"
        }
    }
}

tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile>().configureEach {
    kotlinOptions {
        languageVersion = "1.3"
        apiVersion = "1.3"
        jvmTarget = "1.8"
        javaParameters = true   // Useful for reflection.
    }
}