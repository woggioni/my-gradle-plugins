package net.woggioni.gradle.graalvm;

import lombok.SneakyThrows;
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
import org.gradle.api.plugins.JavaPluginExtension;
import org.gradle.api.provider.ListProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.provider.Provider;
import org.gradle.api.tasks.Classpath;
import org.gradle.api.tasks.Exec;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.InputDirectory;
import org.gradle.api.tasks.Optional;
import org.gradle.api.tasks.OutputDirectory;
import org.gradle.internal.jvm.JavaModuleDetector;
import org.gradle.jvm.toolchain.JavaInstallationMetadata;
import org.gradle.jvm.toolchain.JavaLauncher;
import org.gradle.jvm.toolchain.JavaToolchainService;
import org.gradle.process.CommandLineArgumentProvider;

import javax.inject.Inject;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;

import static java.util.Optional.ofNullable;
import static net.woggioni.gradle.graalvm.Constants.GRAALVM_TASK_GROUP;

public abstract class JlinkTask extends Exec {

    @Classpath
    public abstract Property<FileCollection> getClasspath();

    @InputDirectory
    public abstract DirectoryProperty getGraalVmHome();

    @Input
    @Optional
    public abstract Property<String> getMainClass();

    @Input
    @Optional
    public abstract Property<String> getMainModule();

    @Input
    public abstract ListProperty<String> getAdditionalModules();

    @Input
    public abstract Property<Boolean> getBindServices();

    @Input
    @Optional
    public abstract Property<Integer> getCompressionLevel();

    @Inject
    protected abstract JavaModuleDetector getJavaModuleDetector();

    @OutputDirectory
    public abstract DirectoryProperty getOutputDir();

    private static final Logger log = Logging.getLogger(JlinkTask.class);

    public JlinkTask() {
        Project project = getProject();
        setGroup(GRAALVM_TASK_GROUP);
        setDescription(
            "Generates a custom Java runtime image that contains only the platform modules" +
                " that are required for a given application");
        ExtensionContainer ext = project.getExtensions();
        JavaApplication javaApplication = ext.findByType(JavaApplication.class);
        if(!Objects.isNull(javaApplication)) {
            getMainClass().convention(javaApplication.getMainClass());
            getMainModule().convention(javaApplication.getMainModule());
        }
        getClasspath().convention(project.files());
        ProjectLayout layout = project.getLayout();
        JavaToolchainService javaToolchainService = ext.findByType(JavaToolchainService.class);
        JavaPluginExtension javaPluginExtension = ext.findByType(JavaPluginExtension.class);
        Provider<Directory> graalHomeDirectoryProvider = ofNullable(javaPluginExtension.getToolchain()).map(javaToolchainSpec ->
            javaToolchainService.launcherFor(javaToolchainSpec)
        ).map(javaLauncher ->
            javaLauncher.map(JavaLauncher::getMetadata).map(JavaInstallationMetadata::getInstallationPath)
        ).orElseGet(() -> layout.dir(project.provider(() ->project.file(System.getProperty("java.home")))));
        getGraalVmHome().convention(graalHomeDirectoryProvider);
        getAdditionalModules().convention(new ArrayList<>());
        getBindServices().convention(false);

        BasePluginExtension basePluginExtension =
                ext.getByType(BasePluginExtension.class);
        getOutputDir().convention(
            basePluginExtension.getLibsDirectory()
                .dir(project.getName() +
                    ofNullable(project.getVersion()).map(it -> "-" + it).orElse(""))
        );
        Object executableProvider = new Object() {
            @Override
            public String toString() {
                return getGraalVmHome().get() + "/bin/jlink";
            }
        };
        executable(executableProvider);
        CommandLineArgumentProvider argumentProvider = new CommandLineArgumentProvider() {
            @Override
            @SneakyThrows
            public Iterable<String> asArguments() {
                List<String> result = new ArrayList<>();
                final Property<Integer> compressionLevelProperty= getCompressionLevel();
                if(compressionLevelProperty.isPresent()) {
                    result.add(String.format("--compress=zip-%d", compressionLevelProperty.get()));
                }
                if(getBindServices().get()) {
                    result.add("--bind-services");
                }
                JavaModuleDetector javaModuleDetector = getJavaModuleDetector();
                FileCollection classpath = getClasspath().get();
                FileCollection mp = javaModuleDetector.inferModulePath(true, classpath);
                if(!mp.isEmpty()) {
                    result.add("-p");
                    result.add(mp.getAsPath());
                }

                if(getMainModule().isPresent()) {
                    result.add("--launcher");
                    String launcherArg = project.getName() + '=' +
                            getMainModule().get() +
                            ofNullable(getMainClass().getOrElse(null)).map(it -> '/' + it).orElse("");
                    result.add(launcherArg);
                }
                result.add("--output");
                result.add(getOutputDir().get().getAsFile().toString());
                List<String> additionalModules = getAdditionalModules().get();
                if(getMainModule().isPresent() || !additionalModules.isEmpty()) {
                    result.add("--add-modules");
                    final List<String> modules2BeAdded = new ArrayList<>();
                    ofNullable(getMainModule().getOrElse(null)).ifPresent(modules2BeAdded::add);
                    modules2BeAdded.addAll(additionalModules);
                    if(!modules2BeAdded.isEmpty()) {
                        result.add(String.join(",", modules2BeAdded));
                    }
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
