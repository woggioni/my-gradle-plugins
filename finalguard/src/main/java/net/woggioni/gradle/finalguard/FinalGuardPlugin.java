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
    private static final String JAVAC_PLUGIN_NAME = "net.woggioni.finalguard.FinalGuardPlugin";

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
                final StringBuilder xpluginArg = new StringBuilder("-Xplugin:").append(JAVAC_PLUGIN_NAME);
                appendOption(xpluginArg, "default.level", finalGuardExtension.getDefaultLevel());
                appendOption(xpluginArg, "local.variable.level", finalGuardExtension.getLocalVariableLevel());
                appendOption(xpluginArg, "method.param.level", finalGuardExtension.getMethodParameterLevel());
                appendOption(xpluginArg, "abstract.method.param.level", finalGuardExtension.getAbstractMethodParameterLevel());
                appendOption(xpluginArg, "for.param.level", finalGuardExtension.getForLoopParameterLevel());
                appendOption(xpluginArg, "try.param.level", finalGuardExtension.getTryWithResourceLevel());
                appendOption(xpluginArg, "catch.param.level", finalGuardExtension.getCatchParameterLevel());
                appendOption(xpluginArg, "lambda.param.level", finalGuardExtension.getLambdaParameterLevel());
                options.getCompilerArgs().add(xpluginArg.toString());
            });
        });
    }

    private static void appendOption(StringBuilder sb, String key, Property<Diagnostic.Kind> property) {
        if (property.isPresent()) {
            sb.append(' ').append(key).append('=').append(property.get());
        }
    }
}
