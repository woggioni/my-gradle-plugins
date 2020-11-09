plugins {
    `maven-publish`
    `groovy-gradle-plugin`
    id("com.gradle.plugin-publish")
}

gradlePlugin {
    plugins {
        create("MultiVersionJarPlugin") {
            id = "net.woggioni.plugins.multi-version-jar"
            implementationClass = "net.woggioni.plugins.multi.release.jar.MultiVersionJarPlugin"
        }
        create("MultiReleaseJarPlugin") {
            id = "net.woggioni.plugins.multi-release-jar"
            implementationClass = "net.woggioni.plugins.multi.release.jar.MultiReleaseJarPlugin"
        }
    }
}

java {
    sourceCompatibility = JavaVersion.VERSION_1_8
    targetCompatibility = JavaVersion.VERSION_1_8
}
