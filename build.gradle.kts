repositories {
    jcenter()
}

group = "net.corda"
version = 0.1

plugins {
    `java-gradle-plugin`
    `maven-publish`
    id("org.jetbrains.kotlin.jvm") version("1.3.61")
    id("com.gradle.plugin-publish") version("0.10.1")
}

configure<ExtraPropertiesExtension> {
    set("junit_jupiter_version", "5.5.2")
}

dependencies {

//    classpath("org.jetbrains.kotlin:kotlin-gradle-plugin:$kotlin_version")


    // Align versions of all Kotlin components
    implementation(platform("org.jetbrains.kotlin:kotlin-bom"))

    // Use the Kotlin JDK 8 standard library.
    implementation("org.jetbrains.kotlin:kotlin-stdlib-jdk8")

    // Use the Kotlin test library.
//    testImplementation("org.jetbrains.kotlin:kotlin-test")

    // Use the Kotlin JUnit integration.
//    testImplementation("org.jetbrains.kotlin:kotlin-test-junit")
    testImplementation("org.junit.jupiter:junit-jupiter-api:5.5.2")
    testImplementation(gradleTestKit())

    testRuntimeOnly("org.junit.jupiter:junit-jupiter-engine:5.5.2")
}

gradlePlugin {
    // Define the plugin
    val my_first_plugin by plugins.creating {
        id = "net.corda.my-first-plugin"
        implementationClass = "my.first.plugin.MyFirstPluginPlugin"
    }
}

// Add a source set for the functional test suite
//val functionalTestSourceSet = sourceSets.create("functionalTest") {
//}
//
//gradlePlugin.testSourceSets(functionalTestSourceSet)
//configurations.getByName("functionalTestImplementation").extendsFrom(configurations.getByName("testImplementation"))

// Add a task to run the functional tests
//val functionalTest by tasks.creating(Test::class) {
//    testClassesDirs = functionalTestSourceSet.output.classesDirs
//    classpath = functionalTestSourceSet.runtimeClasspath
//}
//
//val check by tasks.getting(Task::class) {
//    // Run the functional tests as part of `check`
//    dependsOn(functionalTest)
//}

tasks.withType<Test>().configureEach {
    useJUnitPlatform()
    systemProperty("plugin.build.dir", tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile>().first().destinationDir)
    systemProperty("java.io.tmpdir", buildDir.absolutePath)
    systemProperty("test.gradle.user.home", project.gradle.gradleUserHomeDir)
}

tasks.withType<Wrapper>().configureEach {
    gradleVersion = "6.1.1"
    distributionType = Wrapper.DistributionType.ALL
}
