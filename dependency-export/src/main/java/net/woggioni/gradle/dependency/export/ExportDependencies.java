package net.woggioni.gradle.dependency.export;

import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import org.gradle.api.DefaultTask;
import org.gradle.api.GradleException;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.artifacts.ResolvedArtifact;
import org.gradle.api.artifacts.component.ComponentIdentifier;
import org.gradle.api.artifacts.component.ModuleComponentIdentifier;
import org.gradle.api.artifacts.component.ProjectComponentIdentifier;
import org.gradle.api.artifacts.result.DependencyResult;
import org.gradle.api.artifacts.result.ResolutionResult;
import org.gradle.api.artifacts.result.ResolvedComponentResult;
import org.gradle.api.artifacts.result.ResolvedDependencyResult;
import org.gradle.api.artifacts.result.UnresolvedDependencyResult;
import org.gradle.api.file.FileCollection;
import org.gradle.api.file.RegularFile;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.model.ObjectFactory;
import org.gradle.api.plugins.JavaPlugin;
import org.gradle.api.plugins.JavaPluginConvention;
import org.gradle.api.provider.Property;
import org.gradle.api.provider.Provider;
import org.gradle.api.tasks.Classpath;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.InputFiles;
import org.gradle.api.tasks.Internal;
import org.gradle.api.tasks.OutputFile;
import org.gradle.api.tasks.TaskAction;
import org.gradle.api.tasks.options.Option;

import javax.inject.Inject;
import java.io.File;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collector;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static net.woggioni.gradle.dependency.export.DependencyExportPlugin.DEPENDENCY_EXPORT_GROUP;

public class ExportDependencies extends DefaultTask {

    @Getter(onMethod_ = { @Input })
    private final Property<String> configurationName;

    @Getter
    @Internal
    private final RegularFileProperty outputFile;

    @Input
    public String getDestination() {
        return outputFile.map(RegularFile::getAsFile).map(File::getAbsolutePath).get();
    }

    @OutputFile
    public Provider<File> getResult() {
        return outputFile.map(RegularFile::getAsFile);
    }

    @Getter(onMethod_ = { @Input })
    private final Property<Boolean> showArtifacts;

    private final JavaPluginConvention javaPluginConvention;

    @InputFiles
    @Classpath
    public Provider<FileCollection> getConfigurationFiles() {
        return configurationName.map(this::fetchConfiguration);
    }

    @Option(option = "configuration", description = "Set the configuration name")
    public void setConfiguration(String configurationName) {
        this.configurationName.set(configurationName);
    }

    @Option(option = "output", description = "Set the output file name")
    public void setOutput(String outputFile) {
        Provider<File> fileProvider = getProject().provider(() -> new File(outputFile));
        this.outputFile.set(getProject().getLayout().file(fileProvider));
    }

    @Option(option = "showArtifacts", description = "Show artifacts")
    public void setArtifacts(boolean value) {
        showArtifacts.set(value);
    }

    @Inject
    public ExportDependencies(ObjectFactory objects) {
        setGroup(DEPENDENCY_EXPORT_GROUP);
        javaPluginConvention = getProject().getConvention().getPlugin(JavaPluginConvention.class);
        configurationName = objects.property(String.class).convention(JavaPlugin.RUNTIME_CLASSPATH_CONFIGURATION_NAME);
        Provider<File> defaultOutputFileProvider =
                getProject().provider(() -> new File(javaPluginConvention.getDocsDir(), "dependencies.dot"));
        outputFile = objects.fileProperty().convention(getProject().getLayout().file(defaultOutputFileProvider));
        showArtifacts = objects.property(Boolean.class).convention(false);
    }

    private static String quote(String s) {
        return "\"" + s + "\"";
    }
    
    private Configuration fetchConfiguration(String configurationName) {
        return Optional.ofNullable(getProject().getConfigurations().findByName(configurationName)).orElseThrow(() -> {
            String resolvableConfigurations = '[' + getProject().getConfigurations().stream()
                    .filter(Configuration::isCanBeResolved)
                    .map(it -> '\'' + it.getName() + '\'')
                    .collect(Collectors.joining(", ")) + ']';
            throw new GradleException(String.format("Configuration '%s' doesn't exist or cannot be resolved, " +
                    "resolvable configurations in this project are %s", configurationName, resolvableConfigurations));
        });
    }

    @TaskAction
    @SneakyThrows
    public void run() { 
        Configuration requestedConfiguration = fetchConfiguration(configurationName.get());
        ResolutionResult resolutionResult = requestedConfiguration.getIncoming().getResolutionResult();
        Path destination = outputFile.map(RegularFile::getAsFile).map(File::toPath).get();
        doStuff(requestedConfiguration, resolutionResult, destination);
    }

    @SneakyThrows
    private void doStuff(Configuration requestedConfiguration, ResolutionResult resolutionResult, Path destination) {
        Map<ResolvedComponentResult, Integer> map = new HashMap<>();
        Files.createDirectories(destination.getParent());
        try(Writer writer = Files.newBufferedWriter(destination)) {
            writer.write("digraph G {");
            writer.write('\n');
            writer.write("    #rankdir=\"LR\";");
            writer.write('\n');
            Optional<Map<ComponentIdentifier, List<ResolvedArtifact>>> artifactMap = Optional.empty();
            if(showArtifacts.get()) {
                artifactMap = Optional.of(requestedConfiguration.getResolvedConfiguration().getResolvedArtifacts().stream().map(it ->
                        new AbstractMap.SimpleEntry<>(it.getId().getComponentIdentifier(), it)
                ).collect(Collectors.groupingBy(Map.Entry::getKey,
                        Collector.of(ArrayList::new,
                                (list, entry) -> list.add(entry.getValue()),
                                (l1, l2) -> { l1.addAll(l2); return l1; }))));
            }
            final int[] sequence = new int[1];
            for (ResolvedComponentResult component : resolutionResult.getAllComponents()) {
                map.computeIfAbsent(component, it -> sequence[0]++);
                ComponentIdentifier id = component.getId();
                Optional<List<ResolvedArtifact>> artifacts = artifactMap
                        .flatMap(it -> Optional.ofNullable(it.get(id)));
                String componentName = id.getDisplayName();
                String label = artifacts.map(it -> {
                    String rows = it.stream().map(resolvedArtifact -> {
                        String artifactDescription = Stream.of(
                                new AbstractMap.SimpleEntry<>("type", resolvedArtifact.getType()),
                                new AbstractMap.SimpleEntry<>("classifier", resolvedArtifact.getClassifier()),
                                new AbstractMap.SimpleEntry<>("extension",
                                        !Objects.equals(resolvedArtifact.getExtension(), resolvedArtifact.getType()) ?
                                                resolvedArtifact.getExtension() : null)
                        ).map(entry -> {
                            if (entry.getValue() == null || entry.getValue().isEmpty()) return null;
                            else return entry.getKey() + ": " + entry.getValue();
                        }).collect(Collectors.joining(", "));
                        return "<TR><TD BGCOLOR=\"lightgrey\">" + artifactDescription + "</TD></TR>";
                    }).collect(Collectors.joining());
                    return "<<TABLE BORDER=\"0\" CELLBORDER=\"1\" CELLSPACING=\"0\" CELLPADDING=\"2\">" +
                            "    <TR>" +
                            "        <TD>" + id.getDisplayName() + "</TD>" +
                            "    </TR>" +
                            "    " + rows +
                            "</TABLE>>";
                }).orElse(quote(componentName));

                String shape;
                String color;
                if(id instanceof ProjectComponentIdentifier) {
                    shape = !artifacts.isPresent() ? "box" : "none";
                    color = "#88ff88";
                } else if(id instanceof ModuleComponentIdentifier) {
                    shape = !artifacts.isPresent() ? "oval" : "none";
                    color = "#ffff88";
                } else {
                    throw new IllegalArgumentException(id.getClass().getName());
                }
                Map<String, String> attrs = Stream.of(
                        new AbstractMap.SimpleEntry<>("label", label),
                        new AbstractMap.SimpleEntry<>("shape", quote(shape)),
                        new AbstractMap.SimpleEntry<>("style", quote("filled")),
                        artifacts.map(it -> new AbstractMap.SimpleEntry<>("margin", quote("0"))).orElse(null),
                        new AbstractMap.SimpleEntry<>("fillcolor",  quote(color))
                ).filter(Objects::nonNull).collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
                writer.write("    node_" + map.get(component) + " [" +
                        attrs.entrySet().stream()
                                .map(it -> it.getKey() + '=' + it.getValue())
                                .collect(Collectors.joining(", ")) +
                        "];");
                writer.write('\n');
            }

            @EqualsAndHashCode
            @RequiredArgsConstructor
            class Link {
                final ComponentIdentifier id1;
                final ComponentIdentifier id2;
            }
            Set<Link> linkCache = new HashSet<>();
            for (ResolvedComponentResult component : resolutionResult.getAllComponents()) {
                for(DependencyResult dependency : component.getDependencies()) {
                    if(dependency instanceof ResolvedDependencyResult) {
                        ResolvedComponentResult child =
                                ((ResolvedDependencyResult) dependency).getSelected();
                        if(linkCache.add(new Link(component.getId(), child.getId()))) {
                            writer.write("    node_" + map.get(component) + " -> node_" + map.get(child) + ";");
                            writer.write('\n');
                        }
                    } else if(dependency instanceof UnresolvedDependencyResult) {
                        throw ((UnresolvedDependencyResult) dependency).getFailure();
                    } else {
                        throw new IllegalArgumentException(dependency.getClass().getName());
                    }
                }
            }
            writer.write('}');
            writer.write('\n');
        }
    }
}
