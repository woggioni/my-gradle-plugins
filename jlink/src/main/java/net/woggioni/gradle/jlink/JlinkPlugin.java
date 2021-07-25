package net.woggioni.gradle.jlink;

import org.gradle.api.Plugin;
import org.gradle.api.Project;

public class JlinkPlugin implements Plugin<Project> {
    @Override
    public void apply(Project project) {
        project.getTasks().register("jlink", JlinkTask.class);
    }
}
