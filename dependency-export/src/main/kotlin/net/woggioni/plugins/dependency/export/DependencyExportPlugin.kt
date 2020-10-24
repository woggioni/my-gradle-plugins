package net.woggioni.plugins.dependency.export

import org.gradle.api.GradleException
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.artifacts.ResolvedArtifact
import org.gradle.api.artifacts.component.ComponentIdentifier
import org.gradle.api.artifacts.component.ModuleComponentIdentifier
import org.gradle.api.artifacts.component.ProjectComponentIdentifier
import org.gradle.api.artifacts.result.ResolvedComponentResult
import org.gradle.api.artifacts.result.ResolvedDependencyResult
import org.gradle.api.artifacts.result.UnresolvedDependencyResult
import java.io.BufferedWriter
import java.io.OutputStreamWriter
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import kotlin.reflect.KMutableProperty0


open class ExportDependenciesPluginExtension(project: Project) {
    var configurationName: String = "default"
    var outputFile = project.buildDir.toPath().resolve("dependencies.dot")
    var showArtifacts = false
}

open class RenderDependenciesPluginExtension(project: Project) {
    var format: String = "xlib"
    var outputFile = project.buildDir.toPath().resolve("renderedDependencies")
    var graphvizExecutable: String = "dot"
}

private class Overrider(private val properties: Map<String, Any?>, private val prefix: String) {
    inline fun <reified T> identity(arg: T): T {
        return arg
    }

    fun overrideProperty(property: KMutableProperty0<String>) {
        overrideProperty(property, ::identity)
    }

    inline fun <reified V> overrideProperty(
            property: KMutableProperty0<V>,
            valueFactory: (String) -> V) {
        val propertyKey = prefix + "." + property.name
        val propertyValue = properties[propertyKey] as String?
        if (propertyValue != null) {
            property.set(valueFactory(propertyValue))
        }
    }
}

object DependencyExporter {

    private fun quote(s : String) = "\"" + s + "\""

    fun exportDot(project: Project, ext: ExportDependenciesPluginExtension) {
        val overrider = Overrider(project.properties, "exportDependencies")
        overrider.overrideProperty(ext::configurationName)
        overrider.overrideProperty(ext::outputFile) { value -> Paths.get(value) }
        overrider.overrideProperty(ext::showArtifacts) { value -> value.toBoolean() }

        var sequence = 0
        val map = HashMap<ResolvedComponentResult, Int>()

        val requestedConfiguration = project.configurations.singleOrNull {
            it.name == ext.configurationName
        }?.takeIf { it.isCanBeResolved } ?: let {
            val resolvableConfigurations = "[" + project.configurations.asSequence()
                    .filter { it.isCanBeResolved }
                    .map { "'${it.name}'" }
                    .joinToString(",") + "]"
            throw GradleException("Configuration '${ext.configurationName}' doesn't exist or cannot be resolved, " +
                "resolvable configurations in this project are " + resolvableConfigurations)
        }

        val resolutionResult = requestedConfiguration.incoming.resolutionResult
        if (!ext.outputFile.isAbsolute) {
            ext.outputFile = project.buildDir.toPath().resolve(ext.outputFile)
        }
        Files.createDirectories(ext.outputFile.parent)
        BufferedWriter(
                OutputStreamWriter(
                        Files.newOutputStream(ext.outputFile))).use { writer ->
            writer.write("digraph G {")
            writer.newLine()
            writer.write("    #rankdir=\"LR\";")
            writer.newLine()

            val artifactMap = if(ext.showArtifacts) {
                requestedConfiguration.resolvedConfiguration.resolvedArtifacts.asSequence().map {
                    it.id.componentIdentifier to it
                }.groupBy(
                    Pair<ComponentIdentifier, ResolvedArtifact>::first,
                    Pair<ComponentIdentifier, ResolvedArtifact>::second
                )
            } else {
                null
            }

            for (component in resolutionResult.allComponents) {
                map.computeIfAbsent(component) {
                    sequence++
                }
                val artifacts = artifactMap?.let { it[component.id] }
                val (shape, color) = when (component.id) {
                    is ProjectComponentIdentifier -> (artifacts?.let { "none" } ?: "box") to "#88ff88"
                    is ModuleComponentIdentifier -> (artifacts?.let { "none" } ?: "oval") to "#ffff88"
                    else -> throw NotImplementedError("${component.id::class}")
                }


                val componentName = component.id.displayName
                val label = artifacts?.let {
                    val rows = it.asSequence().map { resolvedArtifact ->
                        val artifactDescription = sequenceOf(
                            "type" to resolvedArtifact.type,
                            "classifier" to resolvedArtifact.classifier,
                            "extension" to resolvedArtifact.extension.takeIf {
                                resolvedArtifact.extension != resolvedArtifact.type
                            }
                        ).mapNotNull { pair ->
                            when {
                                pair.second == null || pair.second.isEmpty() -> null
                                else -> "${pair.first}: ${pair.second}"
                            }
                        }.joinToString(", ")
                        "<TR><TD BGCOLOR=\"lightgrey\">$artifactDescription</TD></TR>"
                    }.joinToString()
                    """
                        <<TABLE BORDER="0" CELLBORDER="1" CELLSPACING="0" CELLPADDING="2">
                            <TR>
                                <TD>${component.id.displayName}</TD>
                            </TR>
                            $rows
                        </TABLE>>
                    """.trimIndent()
                } ?: quote(componentName)

                val attrs = sequenceOf(
                        "label" to label,
                        "shape" to quote(shape),
                        "style" to quote("filled"),
                        artifacts?.let { "margin" to quote(0.toString()) },
                        "fillcolor" to quote(color)
                ).mapNotNull { it }.toMap()
                writer.write("    node_${map[component]} [" +
                        attrs.entries
                                .asSequence()
                                .map { "${it.key}=${it.value}" }.joinToString(", ") +
                        "];")
                writer.newLine()
            }

            for (component in resolutionResult.allComponents) {
                component.dependencies.map { dependency ->
                    when (dependency) {
                        is ResolvedDependencyResult -> dependency
                        is UnresolvedDependencyResult -> {
                            throw dependency.failure
                        }
                        else -> {
                            throw NotImplementedError("${dependency::class}")
                        }
                    }
                }.map(ResolvedDependencyResult::getSelected).forEach { child ->
                    writer.write("    node_${map[component]} -> node_${map[child]};")
                    writer.newLine()
                }
            }
            writer.write("}")
            writer.newLine()
        }
    }
}

object DependencyRenderer {
    fun render(project: Project, ext: RenderDependenciesPluginExtension, sourceFile: Path) {
        val overrider = Overrider(project.properties, "renderDependencies")
        overrider.overrideProperty(ext::format)
        overrider.overrideProperty(ext::graphvizExecutable)
        overrider.overrideProperty(ext::outputFile) { value -> Paths.get(value) }

        if (!ext.outputFile.isAbsolute) {
            ext.outputFile = project.buildDir.toPath().resolve(ext.outputFile)
        }
        val cmd: List<String> = listOf(
                ext.graphvizExecutable,
                "-T${ext.format}",
                "-o${ext.outputFile}",
                sourceFile.toString()

        )
        val returnCode = ProcessBuilder(cmd).inheritIO().start().waitFor()
        if (returnCode != 0) {
            throw GradleException("Error invoking graphviz")
        }
    }
}

class DependencyExportPlugin : Plugin<Project> {
    override fun apply(project: Project) {
        val dependencyExportExtension = ExportDependenciesPluginExtension(project)
        project.extensions.add(ExportDependenciesPluginExtension::class.java, "exportDependencies", dependencyExportExtension)
        val exportDependenciesTask = project.tasks.register("exportDependencies") {
            it.doLast {
                DependencyExporter.exportDot(project, dependencyExportExtension)
            }
        }.get()

        val renderDependenciesPluginExtension = RenderDependenciesPluginExtension(project)
        project.extensions.add(RenderDependenciesPluginExtension::class.java, "renderDependencies", renderDependenciesPluginExtension)
        project.tasks.register("renderDependencies") {
            it.dependsOn(exportDependenciesTask)
            it.doLast {
                DependencyRenderer.render(project, renderDependenciesPluginExtension, dependencyExportExtension.outputFile)
            }
        }.get()
    }
}
