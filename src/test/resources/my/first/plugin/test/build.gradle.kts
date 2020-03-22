//buildscript {
//    repositories {
//        mavenLocal()
//        jcenter()
//        mavenCentral()
//    }
//    dependencies {
//        classpath("net.corda:my-first-plugin:0.1")
//    }
//}

plugins {
    id("net.corda.my-first-plugin")
}
//apply(plugin = "net.corda.my-first-plugin")
import my.first.plugin.MyFirstPluginPluginExtension

configure<my.first.plugin.MyFirstPluginPluginExtension> {
    message = "Hi"
    greeter = "Gradle"
}
