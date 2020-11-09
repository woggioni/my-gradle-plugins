plugins {
    `maven-publish`
    `groovy-gradle-plugin`
    id("com.gradle.plugin-publish")
}

gradlePlugin {
    plugins {
        create("JPMSCheckPlugin") {
            id = "net.woggioni.plugins.jpms-check"
            implementationClass = "net.woggioni.plugins.jpms.check.JPMSCheckPlugin"
        }
    }
}