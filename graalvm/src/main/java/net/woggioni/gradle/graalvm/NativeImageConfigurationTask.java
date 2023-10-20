package net.woggioni.gradle.graalvm;

import org.gradle.api.artifacts.Configuration;
import org.gradle.api.artifacts.ConfigurationContainer;
import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.file.ProjectLayout;
import org.gradle.api.plugins.ExtensionContainer;
import org.gradle.api.plugins.JavaApplication;
import org.gradle.api.plugins.JavaPlugin;
import org.gradle.api.plugins.JavaPluginExtension;
import org.gradle.api.provider.Property;
import org.gradle.api.provider.Provider;
import org.gradle.api.tasks.JavaExec;
import org.gradle.api.tasks.OutputDirectory;
import org.gradle.api.tasks.TaskContainer;
import org.gradle.api.tasks.bundling.Jar;
import org.gradle.jvm.toolchain.JavaLauncher;
import org.gradle.jvm.toolchain.JavaToolchainService;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

import static net.woggioni.gradle.graalvm.Constants.GRAALVM_TASK_GROUP;
import static net.woggioni.gradle.graalvm.NativeImagePlugin.NATIVE_IMAGE_CONFIGURATION_FOLDER_NAME;

public abstract class NativeImageConfigurationTask extends JavaExec {

    @OutputDirectory
    public abstract DirectoryProperty getConfigurationDir();

    public NativeImageConfigurationTask() {
        setGroup(GRAALVM_TASK_GROUP);
        setDescription("Run the application with the native-image-agent " +
                "to create a configuration for native image creation");
        ProjectLayout layout = getProject().getLayout();
        TaskContainer taskContainer = getProject().getTasks();
        JavaApplication javaApplication = getProject().getExtensions().findByType(JavaApplication.class);
        JavaPluginExtension javaExtension = getProject().getExtensions().getByType(JavaPluginExtension.class);
        ExtensionContainer ext = getProject().getExtensions();
        Property<JavaLauncher> javaLauncherProperty = getJavaLauncher();
        Optional.ofNullable(ext.findByType(JavaToolchainService.class))
                .flatMap(ts -> Optional.ofNullable(javaExtension.getToolchain()).map(ts::launcherFor))
                .ifPresent(javaLauncherProperty::set);
        if(!Objects.isNull(javaApplication)) {
            getMainClass().convention(javaApplication.getMainClass());
            getMainModule().convention(javaApplication.getMainModule());
        }
        getConfigurationDir().convention(layout.getProjectDirectory()
                .dir(NATIVE_IMAGE_CONFIGURATION_FOLDER_NAME));
        ConfigurationContainer cc = getProject().getConfigurations();
        Configuration runtimeClasspath = cc.getByName(JavaPlugin.RUNTIME_CLASSPATH_CONFIGURATION_NAME);
        setClasspath(runtimeClasspath);
        Provider<Jar> jarTaskProvider = taskContainer.named(JavaPlugin.JAR_TASK_NAME, Jar.class);
        setClasspath(
            getProject().files(
                jarTaskProvider,
                runtimeClasspath
            )
        );
        List<String> jvmArgs = new ArrayList<>();
        jvmArgs.add("-agentlib:native-image-agent=config-output-dir=" + getConfigurationDir().get());
        for(String jvmArg : Optional.ofNullable(javaApplication.getApplicationDefaultJvmArgs()).orElse(Collections.emptyList())) {
            jvmArgs.add(jvmArg);
        }
        jvmArgs(jvmArgs);
    }
}