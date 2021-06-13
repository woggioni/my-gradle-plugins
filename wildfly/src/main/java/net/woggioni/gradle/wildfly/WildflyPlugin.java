package net.woggioni.gradle.wildfly;

import org.gradle.api.GradleException;
import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.tasks.TaskCollection;
import org.gradle.api.tasks.TaskProvider;
import org.gradle.api.tasks.bundling.AbstractArchiveTask;

public class WildflyPlugin implements Plugin<Project> {
    @Override
    public void apply(Project project) {
        project.getTasks().register("deploy2Wildfly", Deploy2WildflyTask.class, deploy2WildflyTask -> {
            TaskCollection<AbstractArchiveTask> candidates = project.getTasks().withType(AbstractArchiveTask.class);
            TaskProvider<AbstractArchiveTask> warTask = candidates.named("war", AbstractArchiveTask.class);
            TaskProvider<AbstractArchiveTask> selected;
            if(warTask.isPresent()) {
                selected = warTask;
            } else {
                TaskProvider<AbstractArchiveTask> jarTask = candidates.named("jar", AbstractArchiveTask.class);
                if(jarTask.isPresent()) {
                    selected = jarTask;
                } else {
                    throw new GradleException("No suitable archiving task found in the current project");
                }
            }
            deploy2WildflyTask.getInputs().files(selected);
            deploy2WildflyTask.getArtifact()
                .fileProvider(selected.map(t -> t.getOutputs().getFiles().getSingleFile()));
        });
    }
}
