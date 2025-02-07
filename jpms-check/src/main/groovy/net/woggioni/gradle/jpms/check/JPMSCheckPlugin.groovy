package net.woggioni.gradle.jpms.check


import groovy.transform.CompileStatic
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.file.RegularFile
import org.gradle.api.plugins.JavaPlugin
import org.gradle.api.plugins.ReportingBasePlugin
import org.gradle.api.reporting.ReportingExtension

class JPMSCheckPlugin implements Plugin<Project> {

    @Override
    @CompileStatic
    void apply(Project project) {
        project.pluginManager.apply(ReportingBasePlugin.class)
        project.tasks.register("jpms-check", JPMSCheckTask) {task ->
            ReportingExtension reporting = project.extensions.getByType(ReportingExtension.class)
            boolean recursive = project.properties["jpms-check.recursive"]?.with(Object.&toString)?.with(Boolean.&parseBoolean) ?: false
            String cfgName = project.properties["jpms-check.configurationName"] ?: JavaPlugin.RUNTIME_CLASSPATH_CONFIGURATION_NAME
            OutputFormat defaultOutputFormat = (project.properties["jpms-check.outputFormat"]
                    ?.with(Object.&toString)
                    ?.with(OutputFormat.&valueOf)
                    ?: OutputFormat.html)
            task.getConfigurationName().convention(cfgName)
            task.getRecursive().convention(recursive)
            task.outputFormat.convention(defaultOutputFormat)
            task.getOutputFile().convention(reporting.baseDirectory.zip(task.getOutputFormat(), { dir, outputFormat ->
                RegularFile result = null
                switch(outputFormat) {
                    case OutputFormat.html:
                        result = dir.file( "jpms-report.html")
                        break
                    case OutputFormat.json:
                        result = dir.file( "jpms-report.json")
                        break
                }
                result
            }))
        }
    }
}
