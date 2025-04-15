## Overview
This project contains 2 Gradle plugin:
- NativeImage allows you to create a native executable file from a Gradle project using GraalVM's `native-image` tool
- Jlink allows you to create a native distribution of a Gradle project using GraalVM `jlink` tool

### Native Image plugin

Declare the plugin in your build's `settings.gradle` like this
```groovy

pluginManagement {
    repositories {
        maven {
            url = 'https://woggioni.net/mvn/'
        }
    }

    plugins {
        id "net.woggioni.gradle.graalvm.native-image" version "2023.10.23"
    }
}
```

Then add it to a project's `build.gradle`

```groovy
plugins {
    id 'net.woggioni.gradle.graalvm.native-image'
}

configureNativeImage {
    args = [ 'your', 'command', 'line', 'arguments']
}

application {
    mainClass = 'your.main.Class'
}
```
Mind that if your project also uses the built-in Gradle `application` plugin, that must be applied before the `net.woggioni.gradle.graalvm.native-image`.

The plugin adds 2 tasks to your project:

- `configureNativeImage` of type `net.woggioni.gradle.graalvm.NativeImageConfigurationTask` which launches 
   your application with the [native-image-agent](https://www.graalvm.org/latest/reference-manual/native-image/metadata/AutomaticMetadataCollection/)
   to generate the native image configuration files in `${project.projectDir}/native-image`
- `nativeImage` of type `net.woggioni.gradle.graalvm.NativeImageTask` that creates the native executable the project's 
   libraries folder (`${project.buildDir}/libs`)

#### Configuration for `NativeImageConfigurationTask`

###### mergeConfiguration

This boolean property decides whether to create a new set of configuration files or simply append to the existing ones

#### Configuration for `NativeImageTask`

###### mainClass

The name of the main class the native executable will launch, defaults to the value set in

```groovy
application {
    mainClass = 'my.main.class'
}
```

###### mainModule

The name of the main JPMS module the native executable will launch, defaults to the value set in

```groovy
application {
    mainModule = 'my.main.module'
}
```

Only applicable when `useJpms` is true

###### useJpms

Whether or not enable JPMS in the generated executable 
(dependencies that support JPMS will be forwarded to `native-image` using the `-p` instead of the `-cp` option)

###### useMusl

This boolean property allows to link the generated executable to musl libc, 
note that in order to do that a valid (linked to musl-libc) compiler toolchain must be available
on the `PATH`, for example if building x86_64 executables on Linux, GraalVM will look for 
`x86_64-linux-musl-gcc`

###### buildStaticImage

This boolean property allows to create a statically linked executable for maximum portability
(a static executable only depends on the kernel and can be moved freely to
another different machine with the same operating system and CPU architecture).
Beware that this requires the libc to support static linking 
(most notably, the glibc does not support static linking, the only way to build static executables 
on linux is to link to a different libc implementation like musl libc).

###### enableFallback

Whether or not to allow the creation of the [fallback image](https://www.graalvm.org/22.0/reference-manual/native-image/Limitations/)
when native-image configuration is missing.

###### graalVmHome

This points to the installation folder of GraalVM, defaults to the installation directory
of the selected Gradle toolchain (if any) or the installation directory
of the JVM Gradle is running on otherwise.

#### Customize native-image command line
A [native-image.properties](https://www.graalvm.org/22.0/reference-manual/native-image/BuildConfiguration/) 
file can be added to `${project.projectDir}/native-image` to add custom parameters:

```bash
-H:Optimize=3 --initialize-at-run-time=your.main.class --gc=G1
```

#### Limitations

GraalVM with the [native-image](https://www.graalvm.org/22.0/reference-manual/native-image/) plugin is required in order
for these plugins to work, this can be achieved either running Gradle under GraalVM directly or using Gradle toolchains
support to request GraalVM at the project level

```groovy
java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(17)
        vendor = JvmVendorSpec.GRAAL_VM
    }
}
```