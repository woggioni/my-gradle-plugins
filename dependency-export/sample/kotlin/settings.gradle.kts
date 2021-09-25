pluginManagement {
  repositories {
      maven {
          url = 'https://woggioni.net/mvn/'
      }
      gradlePluginPortal()
  }
  plugins {
    id "net.woggioni.gradle.dependency-export" version "0.1"
  }
}
