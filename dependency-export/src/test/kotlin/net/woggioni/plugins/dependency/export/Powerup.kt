package net.woggioni.plugins.dependency.export

import java.io.FileNotFoundException
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption


@Throws(IOException::class)
fun Any.installResource(resourceName: String, destination: Path) {
    val outputFile = run {
        val realDestination = if (Files.isSymbolicLink(destination)) {
            destination.toRealPath()
        } else {
            destination
        }
        when {
            !Files.exists(realDestination) -> {
                Files.createDirectories(realDestination.parent)
                realDestination
            }
            Files.isDirectory(realDestination) ->
                realDestination.resolve(resourceName.substring(1 + resourceName.lastIndexOf('/')))
            Files.isRegularFile(realDestination) -> realDestination
            else -> throw IllegalStateException("Path '${realDestination}' is neither a file nor a directory")
        }
    }
    (javaClass.getResourceAsStream(resourceName)
            ?: javaClass.classLoader.getResourceAsStream(resourceName))?.use { inputStream ->
        Files.copy(inputStream, outputFile, StandardCopyOption.REPLACE_EXISTING)
    } ?: throw FileNotFoundException(resourceName)
}
