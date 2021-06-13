package net.woggioni.gradle.dependency.export;

import org.gradle.api.Action;
import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.plugins.JavaBasePlugin;
import org.gradle.api.plugins.ObjectConfigurationAction;
import org.gradle.api.provider.Provider;

public class DependencyExportPlugin implements Plugin<Project> {
    @Override
    public void apply(Project project) {
        project.apply(new Action<ObjectConfigurationAction>() {
            @Override
            public void execute(ObjectConfigurationAction objectConfigurationAction) {
                objectConfigurationAction.plugin(JavaBasePlugin.class);
            }
        });
        Provider<ExportDependencies> exportDependenciesTask =
                project.getTasks().register("exportDependencies", ExportDependencies.class);
        Provider<RenderDependencies> renderDependenciesTask =
            project.getTasks().register("renderDependencies", RenderDependencies.class,
                    renderDependencies -> renderDependencies.setExportTask(exportDependenciesTask));

        project.getExtensions().getExtraProperties().set(ExportDependencies.class.getSimpleName(), ExportDependencies.class);
        project.getExtensions().getExtraProperties().set(RenderDependencies.class.getSimpleName(), RenderDependencies.class);
    }
}
