repositories {
    jcenter()
    mavenLocal()
}

group = "net.woggioni.plugins"
version = 0.1

plugins {
    `java-gradle-plugin`
    `maven-publish`
    id("org.jetbrains.kotlin.jvm") version("1.3.71")
    id("com.gradle.plugin-publish") version("0.10.1")
}

configure<ExtraPropertiesExtension> {
    set("junit_jupiter_version", "5.5.2")
}

dependencies {


    // Align versions of all Kotlin components
    implementation(platform("org.jetbrains.kotlin:kotlin-bom"))

    // Use the Kotlin JDK 8 standard library.
    implementation("org.jetbrains.kotlin:kotlin-stdlib-jdk8")

    testImplementation("org.junit.jupiter:junit-jupiter-api:5.5.2")
    testImplementation(gradleTestKit())

    testRuntimeOnly("org.junit.jupiter:junit-jupiter-engine:5.5.2")
}

gradlePlugin {
    val dependencyExportPlugin by plugins.creating {
        id = "net.woggioni.plugins.dependency-export"
        implementationClass = "net.woggioni.plugins.DependencyExportPlugin"
    }
}

tasks.withType<Test>().configureEach {
    useJUnitPlatform()
    systemProperty("plugin.build.dir", tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile>().first().destinationDir)
    systemProperty("java.io.tmpdir", buildDir.absolutePath)
    systemProperty("test.gradle.user.home", project.gradle.gradleUserHomeDir)
}

tasks.withType<Wrapper>().configureEach {
    gradleVersion = "6.3"
    distributionType = Wrapper.DistributionType.ALL
}

tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile>().configureEach {
    kotlinOptions.jvmTarget = "1.8"
}