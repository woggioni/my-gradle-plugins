package net.woggioni.gradle.finalguard;

import lombok.SneakyThrows;
import org.gradle.api.Action;
import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.artifacts.DependencySet;
import org.gradle.api.model.ObjectFactory;
import org.gradle.api.plugins.ExtensionContainer;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.TaskContainer;
import org.gradle.api.tasks.compile.CompileOptions;
import org.gradle.api.tasks.compile.JavaCompile;

import javax.tools.Diagnostic;
import java.net.URL;
import java.util.jar.Attributes;
import java.util.jar.Manifest;

public class FinalGuardPlugin implements Plugin<Project> {
    private static final String FINALGUARD_PLUGIN_CONFIGURATION = "finalguard_plugin";

    @Override
    public void apply(final Project project) {
        final ExtensionContainer extensionContainer = project.getExtensions();
        final TaskContainer tasks = project.getTasks();
        final ObjectFactory objects = project.getObjects();

        Configuration javacPluginConfiguration = project.getConfigurations().create(FINALGUARD_PLUGIN_CONFIGURATION);
        javacPluginConfiguration.withDependencies(new Action<DependencySet>() {
            @Override
            @SneakyThrows
            public void execute(DependencySet dependencies) {
                final Class<?> cls = getClass();
                final String resourceName = cls.getName().replace('.', '/') + ".class";
                final URL classUrl = cls.getClassLoader().getResource(resourceName);
                if (classUrl.getProtocol().startsWith("jar")) {
                    final String path = classUrl.toString();
                    String manifestPath = path.substring(0, path.lastIndexOf("!") + 1) +
                            "/META-INF/MANIFEST.MF";
                    final Manifest manifest = new Manifest(new URL(manifestPath).openStream());
                    final Attributes attr = manifest.getMainAttributes();
                    final String version = attr.getValue(Attributes.Name.SPECIFICATION_VERSION);
                    dependencies.add(project.getDependencies().create("net.woggioni.finalguard:finalguard-javac-plugin:" + version));
                }
            }
        });

        final FinalGuardExtension finalGuardExtension = objects.newInstance(FinalGuardExtension.class);
        extensionContainer.add("finalGuard", finalGuardExtension);
        tasks.withType(JavaCompile.class, javaCompileTask -> {
            javaCompileTask.doFirst(t -> {
                final CompileOptions options = javaCompileTask.getOptions();
                options.setAnnotationProcessorPath(options.getAnnotationProcessorPath().plus(javacPluginConfiguration));
                {
                    final Property<Diagnostic.Kind> defaultLevel = finalGuardExtension.getDefaultLevel();
                    if (defaultLevel.isPresent()) {
                        final Diagnostic.Kind diagnosticKind = defaultLevel.get();
                        options.getForkOptions().getJvmArgs().add("-Dnet.woggioni.finalguard.diagnostic.level=" + diagnosticKind);
                    }
                }
                {
                    final Property<Diagnostic.Kind> localVariableLevel = finalGuardExtension.getLocalVariableLevel();
                    if (localVariableLevel.isPresent()) {
                        final Diagnostic.Kind diagnosticKind = localVariableLevel.get();
                        options.getForkOptions().getJvmArgs().add("-Dnet.woggioni.finalguard.diagnostic.local.variable.level=" + diagnosticKind);
                    }
                }
                {
                    final Property<Diagnostic.Kind> methodParameterLevel = finalGuardExtension.getMethodParameterLevel();
                    if (methodParameterLevel.isPresent()) {
                        final Diagnostic.Kind diagnosticKind = methodParameterLevel.get();
                        options.getForkOptions().getJvmArgs().add("-Dnet.woggioni.finalguard.diagnostic.method.param.level=" + diagnosticKind);
                    }
                }
                {
                    final Property<Diagnostic.Kind> forLoopParameterLevel = finalGuardExtension.getForLoopParameterLevel();
                    if (forLoopParameterLevel.isPresent()) {
                        final Diagnostic.Kind diagnosticKind = forLoopParameterLevel.get();
                        options.getForkOptions().getJvmArgs().add("-Dnet.woggioni.finalguard.diagnostic.for.param.level=" + diagnosticKind);
                    }
                }
                {
                    final Property<Diagnostic.Kind> tryParameterLevel = finalGuardExtension.getTryWithResourceLevel();
                    if (tryParameterLevel.isPresent()) {
                        final Diagnostic.Kind diagnosticKind = tryParameterLevel.get();
                        options.getForkOptions().getJvmArgs().add("-Dnet.woggioni.finalguard.diagnostic.try.param.level=" + diagnosticKind);
                    }
                }
                {
                    final Property<Diagnostic.Kind> catchParameterLevel = finalGuardExtension.getCatchParameterLevel();
                    if (catchParameterLevel.isPresent()) {
                        final Diagnostic.Kind diagnosticKind = catchParameterLevel.get();
                        options.getForkOptions().getJvmArgs().add("-Dnet.woggioni.finalguard.diagnostic.catch.param.level=" + diagnosticKind);
                    }
                }
                {
                    final Property<Diagnostic.Kind> lambdaParameterLevel = finalGuardExtension.getLambdaParameterLevel();
                    if (lambdaParameterLevel.isPresent()) {
                        final Diagnostic.Kind diagnosticKind = lambdaParameterLevel.get();
                        options.getForkOptions().getJvmArgs().add("-Dnet.woggioni.finalguard.diagnostic.lambda.param.level=" + diagnosticKind);
                    }
                }
                options.getCompilerArgs().add("-Xplugin:net.woggioni.finalguard.FinalGuardPlugin");
            });
        });
    }
}
