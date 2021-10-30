package net.woggioni.gradle.jpms.check

import groovy.json.JsonBuilder
import groovy.transform.Canonical
import groovy.transform.CompileStatic
import groovy.xml.MarkupBuilder
import org.gradle.api.GradleException
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.artifacts.Configuration
import org.gradle.api.artifacts.result.ResolvedArtifactResult

import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.util.jar.JarFile
import java.util.stream.Collectors
import java.util.stream.Stream
import java.util.zip.ZipFile

class JPMSCheckPlugin implements Plugin<Project> {

    @Canonical
    @CompileStatic
    private class CheckResult {
        ResolvedArtifactResult dep
        String automaticModuleName
        boolean multiReleaseJar
        boolean moduleInfo

        boolean getJpmsFriendly() {
            return automaticModuleName != null || moduleInfo
        }

        @Override
        boolean equals(Object other) {
            if(other == null) {
                return false
            } else if(other.class == CheckResult.class) {
                return dep?.id?.componentIdentifier == ((CheckResult) other).dep?.id?.componentIdentifier
            } else {
                return false
            }
        }

        @Override
        int hashCode() {
            return dep.id.componentIdentifier.hashCode()
        }
    }

    @CompileStatic
    private Stream<CheckResult> computeResults(Stream<ResolvedArtifactResult> artifacts) {
        return artifacts.filter { ResolvedArtifactResult res ->
            res.file.exists() && res.file.name.endsWith(".jar")
        }.<CheckResult>map { resolvedArtifact ->
            JarFile jarFile = new JarFile(resolvedArtifact.file).with {
                if (it.isMultiRelease()) {
                    new JarFile(
                            resolvedArtifact.file,
                            false,
                            ZipFile.OPEN_READ,
                            Runtime.version()
                    )
                } else {
                    it
                }
            }
            String automaticModuleName = jarFile.manifest?.with {it.mainAttributes.getValue("Automatic-Module-Name") }
            def moduleInfoEntry = jarFile.getJarEntry("module-info.class")
            new CheckResult(
                    resolvedArtifact,
                    automaticModuleName,
                    jarFile.isMultiRelease(),
                    moduleInfoEntry != null
            )
        }
    }

    private void createHtmlReport(Project project, Stream<CheckResult> checkResults, Writer writer) {
        def builder = new MarkupBuilder(writer)
        int friendly = 0
        int total = 0
        def results = checkResults.peek { CheckResult res ->
            total += 1
            if(res.jpmsFriendly) friendly += 1
        }.collect(Collectors.toList())
        builder.html {
            head {
                meta name: "viewport", content: "width=device-width, initial-scale=1"
                InputStream resourceStream = getClass().classLoader.getResourceAsStream('net/woggioni/plugins/jpms/check/github-markdown.css')
                resourceStream.withReader { Reader reader ->
                    style reader.text
                }
                body {
                    article(class: 'markdown-body') {
                        h1 "Project ${project.group}:${project.name}:${project.version}", style: "text-align: center;"
                        div {
                            table {
                                thead {
                                    tr {
                                        th "JPMS friendly"
                                        th "Not JPMS friendly", colspan: 2
                                        th "Total", colspan: 2
                                    }
                                }
                                tbody {
                                    tr {
                                        td friendly, style: "text-align: center;"
                                        td total - friendly, style: "text-align: center;", colspan: 2
                                        td total, style: "text-align: center;", colspan: 2
                                    }
                                }
                                thead {
                                    th "Name"
                                    th "Multi-release jar"
                                    th "Automatic-Module-Name"
                                    th "Module descriptor"
                                    th "JPMS friendly"
                                }
                                tbody {
                                    results.forEach {res ->
                                        String color = res.jpmsFriendly ? "#dfd" : "fdd"
                                        tr(style: "background-color:$color;") {
                                            td res.dep.id.displayName
                                            td style: "text-align: center;", res.multiReleaseJar ? "✓" : "✕"
                                            td style: "text-align: center;", res.automaticModuleName ?: "n/a"
                                            td style: "text-align: center;", res.moduleInfo ? "✓" : "✕"
                                            td style: "text-align: center;", res.jpmsFriendly ? "✓" : "✕"
                                        }
                                        total += 1
                                        if(res.jpmsFriendly) friendly += 1
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    @CompileStatic
    private createJsonReport(Stream<CheckResult> checkResults, Writer writer) {
        def builder = new JsonBuilder()
        builder (checkResults.map {
                [
                    name: it.dep.id.componentIdentifier.displayName,
                    automaticModuleName: it.automaticModuleName,
                    isMultiReleaseJar: it.multiReleaseJar,
                    hasModuleInfo: it.moduleInfo,
                    jpmsFriendly: it.jpmsFriendly
                ]
            }.collect(Collectors.toList()))
        builder.writeTo(writer)
    }

    @Override
    @CompileStatic
    void apply(Project project) {
        project.tasks.register("jpms-check") {task ->
            boolean recursive = project.properties["jpms-check.recursive"]?.with(Boolean.&parseBoolean) ?: false
            String cfgName = project.properties["jpms-check.configurationName"] ?: "default"
            String outputFormat = project.properties["jpms-check.outputFormat"] ?: "html"
            Path outputFile = project.properties["jpms-check.outputFile"]?.with {
                Paths.get(it as String)
            } ?: with {
                switch(outputFormat) {
                    case "html":
                        Paths.get(project.buildDir.path, "jpms-report.html")
                        break
                    case "json":
                        Paths.get(project.buildDir.path, "jpms-report.json")
                        break
                    default:
                        throw new IllegalArgumentException("Unsupported output format: $outputFormat")
                }
            }
            task.doLast {
                Stream<Project> projects = Stream.of(project)
                if(recursive) {
                    projects = Stream.concat(projects, project.subprojects.stream())
                }
                Set<CheckResult> results = projects.flatMap {
                    Configuration requestedConfiguration = (project.configurations.<Configuration>find { Configuration cfg ->
                        cfg.canBeResolved && cfg.name == cfgName
                    } ?: {
                        def resolvableConfigurations = "[" + project.configurations
                                .grep { Configuration cfg -> cfg.canBeResolved }
                                .collect { "'${it.name}'" }
                                .join(",") + "]"
                        throw new GradleException("Configuration '$cfgName' doesn't exist or cannot be resolved, " +
                                "resolvable configurations in this project are " + resolvableConfigurations)
                    }) as Configuration
                    computeResults(requestedConfiguration.incoming.artifacts.artifacts.stream())
                }.collect(Collectors.toSet())
                Files.createDirectories(outputFile.parent)
                Files.newBufferedWriter(outputFile).withWriter {
                    Stream<CheckResult> resultStream = results.stream().sorted(Comparator.<CheckResult, String>comparing { CheckResult res ->
                        res.dep.id.componentIdentifier.displayName
                    })
                    switch(outputFormat) {
                        case "html":
                            createHtmlReport(project, resultStream, it)
                            break
                        case "json":
                            createJsonReport(resultStream, it)
                            break
                        default:
                            throw new IllegalArgumentException("Unsupported output format: $outputFormat")
                    }
                }
            }
        }
    }
}
