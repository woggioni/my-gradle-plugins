package net.woggioni.gradle.jdeps;

import lombok.SneakyThrows;
import org.gradle.api.Action;
import org.gradle.api.Project;
import org.gradle.api.file.Directory;
import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.file.FileCollection;
import org.gradle.api.file.ProjectLayout;
import org.gradle.api.logging.Logger;
import org.gradle.api.logging.Logging;
import org.gradle.api.plugins.BasePluginExtension;
import org.gradle.api.plugins.ExtensionContainer;
import org.gradle.api.plugins.JavaApplication;
import org.gradle.api.provider.ListProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.provider.Provider;
import org.gradle.api.reporting.ReportingExtension;
import org.gradle.api.tasks.Classpath;
import org.gradle.api.tasks.Exec;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.InputDirectory;
import org.gradle.api.tasks.Optional;
import org.gradle.api.tasks.OutputDirectory;
import org.gradle.internal.jvm.JavaModuleDetector;
import org.gradle.jvm.toolchain.JavaToolchainService;
import org.gradle.jvm.toolchain.JavaToolchainSpec;
import org.gradle.jvm.toolchain.internal.DefaultToolchainSpec;
import org.gradle.process.CommandLineArgumentProvider;

import javax.inject.Inject;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;

import static java.util.Optional.ofNullable;
import static net.woggioni.gradle.jdeps.Constants.JDEPS_TASK_GROUP;

public abstract class JdepsTask extends Exec {

    private final JavaToolchainSpec toolchain;

    public JavaToolchainSpec toolchain(Action<? super JavaToolchainSpec> action) {
        action.execute(toolchain);
        return toolchain;
    }

    @Classpath
    public abstract Property<FileCollection> getClasspath();

    @Classpath
    public abstract Property<FileCollection> getArchives();

    @InputDirectory
    public abstract DirectoryProperty getJavaHome();

    @Input
    @Optional
    public abstract Property<String> getMainClass();

    @Input
    @Optional
    public abstract Property<String> getMainModule();

    @Input
    public abstract ListProperty<String> getAdditionalModules();

    @Inject
    protected abstract JavaModuleDetector getJavaModuleDetector();

    @OutputDirectory
    public abstract DirectoryProperty getOutputDir();

    @Optional
    @OutputDirectory
    public abstract DirectoryProperty getDotOutput();

    @Input
    @Optional
    public abstract Property<Integer> getJavaRelease();

    @Input
    public abstract Property<Boolean> getRecursive();

    private static final Logger log = Logging.getLogger(JdepsTask.class);

    public JdepsTask() {
        Project project = getProject();
        setGroup(JDEPS_TASK_GROUP);
        setDescription(
                "Generates a custom Java runtime image that contains only the platform modules" +
                        " that are required for a given application");
        ExtensionContainer ext = project.getExtensions();
        JavaApplication javaApplication = ext.findByType(JavaApplication.class);
        if (!Objects.isNull(javaApplication)) {
            getMainClass().convention(javaApplication.getMainClass());
            getMainModule().convention(javaApplication.getMainModule());
        }
        getClasspath().convention(project.files());
        getRecursive().convention(true);
        ProjectLayout layout = project.getLayout();
        toolchain = getObjectFactory().newInstance(DefaultToolchainSpec.class);
        JavaToolchainService javaToolchainService = ext.findByType(JavaToolchainService.class);
        Provider<Directory> graalHomeDirectoryProvider = javaToolchainService.launcherFor(it -> {
            it.getLanguageVersion().set(toolchain.getLanguageVersion());
            it.getVendor().set(toolchain.getVendor());
            it.getImplementation().set(toolchain.getImplementation());
        }).map(javaLauncher ->
                javaLauncher.getMetadata().getInstallationPath()
        ).orElse(layout.dir(project.provider(() -> project.file(System.getProperty("java.home")))));
        getJavaHome().convention(graalHomeDirectoryProvider);

        getJavaHome().convention(graalHomeDirectoryProvider);
        getAdditionalModules().convention(new ArrayList<>());

        ReportingExtension reporting = ext.getByType(ReportingExtension.class);

        getOutputDir().convention(
                reporting.getBaseDirectory()
                        .dir(project.getName() +
                                ofNullable(project.getVersion()).map(it -> "-" + it).orElse(""))
        );
        getDotOutput().convention(getOutputDir().dir("graphviz"));
        Object executableProvider = new Object() {
            @Override
            public String toString() {
                return getJavaHome().get() + "/bin/jdeps";
            }
        };
        executable(executableProvider);
        CommandLineArgumentProvider argumentProvider = new CommandLineArgumentProvider() {
            @Override
            @SneakyThrows
            public Iterable<String> asArguments() {
                List<String> result = new ArrayList<>();
                JavaModuleDetector javaModuleDetector = getJavaModuleDetector();
                FileCollection classpath = getClasspath().get();
                FileCollection mp = javaModuleDetector.inferModulePath(true, classpath);
                if (!mp.isEmpty()) {
                    result.add("--module-path");
                    result.add(mp.getAsPath());
                }

                FileCollection cp = classpath.minus(mp);
                if(!cp.isEmpty()) {
                    result.add("-cp");
                    result.add(cp.getAsPath());
                }

                List<String> additionalModules = getAdditionalModules().get();
                if (!additionalModules.isEmpty()) {
                    result.add("--add-modules");
                    final List<String> modules2BeAdded = new ArrayList<>();
                    modules2BeAdded.addAll(additionalModules);
                    if (!modules2BeAdded.isEmpty()) {
                        result.add(String.join(",", modules2BeAdded));
                    }
                }
                if (getDotOutput().isPresent()) {
                    result.add("-dotoutput");
                    result.add(getDotOutput().get().getAsFile().toString());
                }

                if(getRecursive().get()) {
                    result.add("--recursive");
                } else {
                    result.add("--no-recursive");
                }

                if (getMainModule().isPresent()) {
                    result.add("-m");
                    result.add(getMainModule().get());
                }
                if (getJavaRelease().isPresent()) {
                    result.add("--multi-release");
                    result.add(getJavaRelease().get().toString());
                }

                for (File archive : getArchives().get()) {
                    result.add(archive.toString());
                }
                return Collections.unmodifiableList(result);
            }
        };
        getArgumentProviders().add(argumentProvider);

    }

    @Override
    @SneakyThrows
    protected void exec() {
        Files.walk(getOutputDir().get().getAsFile().toPath())
                .sorted(Comparator.reverseOrder())
                .forEach(new Consumer<Path>() {
                    @Override
                    @SneakyThrows
                    public void accept(Path path) {
                        Files.delete(path);
                    }
                });
        super.exec();
    }
}
