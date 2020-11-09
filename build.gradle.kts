allprojects {
    apply<JavaLibraryPlugin>()
    repositories {
        mavenLocal()
        mavenCentral()
        jcenter()
    }
    group = "net.woggioni.plugins"
    version = 0.1

    dependencies {
        add("testImplementation", create(group="org.junit.jupiter", name="junit-jupiter-api", version=project["version.junitJupiter"]))
        add("testRuntimeOnly", create(group="org.junit.jupiter", name="junit-jupiter-engine", version=project["version.junitJupiter"]))
        add("testImplementation", gradleTestKit())
    }

    tasks.named<Test>("test") {
        useJUnitPlatform()
    }
}

tasks.withType<Wrapper>().configureEach {
    gradleVersion = "6.7"
    distributionType = Wrapper.DistributionType.ALL
}

