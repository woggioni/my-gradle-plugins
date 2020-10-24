## Overview
This plugin will add a new task to your gradle projects, the `jpms-check` task, to help you to check how easily
 whether you can start using [JPMS](http://openjdk.java.net/projects/jigsaw/) in your project and which one of your dependencies you will need to update or patch
 to make them JPMS-friendly

### The *jpms-check* task
The `jpms-check` task will scan through all of your project dependencies and create a document where, for
each of them, it will be reported whether they contain a module descriptor (aka `module-info.class`) or 
an `Automatic-Module-Name` entry in the manifest or if the jar is a 
[Multi-Release JAR file](https://openjdk.java.net/jeps/238).
Note that artifacts that do not meet any of these requirements can still be used with JPMS, 
but, using the standard module finder, the module's name will be inferred from the jar file name
(with [some tweaks](https://docs.oracle.com/javase/9/docs/api/java/lang/module/ModuleFinder.html#automatic-modules)
 to try exclude the version number and the artifact classifier) which is not a very reliable option.

## Install the plugin

Checkout this project and in the root folder run
```bash
./gradlew publishToMavenLocal
```
to install it to your local machine's Maven repository.

## Add the plugin to your project

Add this plugin to you project simply adding it to your projects `build.gradle`
```groovy
plugins {
    id "net.woggioni.plugins.jpms-check" version "0.1"
}
```

or to your `build.gradle.kts` if you prefer the Gradle Kotlin DSL

```kotlin
plugins {
    id("net.woggioni.plugins.jpms-check") version "0.1"
}
```

You can also enable it globally on your machine just create a `~/.gradle/init.gradle` file with this content

```groovy
initscript {
    repositories {
        mavenLocal()
        mavenCentral()
        jcenter()
    }
    dependencies {
        classpath "net.woggioni.plugins:jpms-check:0.1"
    }
}

allprojects {
    apply plugin: net.woggioni.plugins.jpms.check.JPMSCheckPlugin
}
```

this means that the plugin will be automatically enabled for all your Gradle projects, 
note that it doesn't alter the build process in any way except for creating the
 `jpms-check` task, so it is relatively safe to do so.

## Configure the plugin

The `jpms-check` will by default create a `jpms-report.html` file in the build directory of the Gradle project
where it is invoked. that is a fully self contained HTML document that can easily be opened wit ha web browser without 
the need of a web server. The behaviour can be customized using some Gradle properties.

### Parameters description

- `jpms-check.outputFormat` will change the format of the generated report, 
at the moment only `html` and `json` are supported (the json report contains the same data of the html report, 
but instead of an HTML table, it generates an array of JSON objects containing the same data).

- `jpms-check.outputFile` will set the path and the name of the generated output file. 
The default is a file in the build directory named `jpms-report.html` if the `html` format is chosen or
 `jpms-report.json` otherwise.

- `jpms-check.recursive` if this property is set to `true` the plugin will recursively analyze all the subprojects
of the project where it is invoked and merge the results in a single report file (the default value of this property is 
`false`)

- `jpms-check.configurationName` the name of the Gradle configuration whose dependencies will be analyzed, 
it defaults to the `default` configuration. 


#### Example usage
Call the `jpms-check` task to analyze all of the dependencies in the current project and all of its
 subproject and create a JSON report with the result in `/tmp/report.json`
```bash
gradle -Pjpms-check.recursive=true -Pjpms-check.outputFormat=json -Pjpms-check.outputFile=/tmp/report.json jpms-check
```
