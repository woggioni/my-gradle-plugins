package net.woggioni.gradle.dependency.export;

import lombok.*;
import org.gradle.api.DefaultTask;
import org.gradle.api.GradleException;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.artifacts.ResolvedArtifact;
import org.gradle.api.artifacts.component.ComponentIdentifier;
import org.gradle.api.artifacts.component.ModuleComponentIdentifier;
import org.gradle.api.artifacts.component.ProjectComponentIdentifier;
import org.gradle.api.artifacts.result.*;
import org.gradle.api.file.FileCollection;
import org.gradle.api.model.ObjectFactory;
import org.gradle.api.plugins.JavaPluginConvention;
import org.gradle.api.provider.Property;
import org.gradle.api.provider.Provider;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.InputFiles;
import org.gradle.api.tasks.OutputFile;
import org.gradle.api.tasks.TaskAction;
import org.gradle.api.tasks.options.Option;

import javax.inject.Inject;
import java.io.File;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.stream.Collector;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class ExportDependencies extends DefaultTask {

    @Getter
    @Setter
    @Input
    private Property<String> configurationName;

    @Getter
    @Setter
    @OutputFile
    private Property<File> outputFile;

    @Getter
    @Setter
    @Input
    private Property<Boolean> showArtifacts;

    private final JavaPluginConvention javaPluginConvention;


    @InputFiles
    public Provider<FileCollection> getConfigurationFiles() {
        return configurationName.flatMap(name -> getProject().getConfigurations().named(name));
    }

    @Option(option = "configuration", description = "Set the configuration name")
    public void setConfiguration(String configurationName) {
        this.configurationName.set(configurationName);
    }

    @Option(option = "output", description = "Set the output file name")
    public void setOutput(String outputFile) {
        this.outputFile.set(getProject().file(outputFile));
    }

    @Option(option = "showArtifacts", description = "Show artifacts")
    public void setArtifacts(boolean value) {
        showArtifacts.set(value);
    }

    @Inject
    public ExportDependencies(ObjectFactory objects) {
        javaPluginConvention = getProject().getConvention().getPlugin(JavaPluginConvention.class);
        configurationName = objects.property(String.class).convention("runtimeClasspath");
        outputFile = objects.property(File.class).convention(
                getProject().provider(() -> new File(javaPluginConvention.getDocsDir(), "dependencies.dot")));
        showArtifacts = objects.property(Boolean.class).convention(false);
    }

    private static String quote(String s) {
        return "\"" + s + "\"";
    }

    @TaskAction
    @SneakyThrows
    void run() {
        Map<ResolvedComponentResult, Integer> map = new HashMap<>();

        Configuration requestedConfiguration = Optional.ofNullable(getProject().getConfigurations().named(configurationName.get()).getOrNull()).orElseThrow(() -> {
            String resolvableConfigurations = '[' + getProject().getConfigurations().stream()
                    .filter(Configuration::isCanBeResolved)
                    .map(it -> '\'' + it.getName() + '\'')
                    .collect(Collectors.joining(", ")) + ']';
            throw new GradleException(String.format("Configuration '%s' doesn't exist or cannot be resolved, " +
                    "resolvable configurations in this project are %s", configurationName.get(), resolvableConfigurations));
        });
        ResolutionResult resolutionResult = requestedConfiguration.getIncoming().getResolutionResult();
        Path destination = outputFile.map(it -> {
            if (it.isAbsolute()) {
                return it;
            } else {
                return new File(javaPluginConvention.getDocsDir(), it.toString());
            }
        }).map(File::toPath).get();
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
                    shape = artifacts.isEmpty() ? "box" : "none";
                    color = "#88ff88";
                } else if(id instanceof ModuleComponentIdentifier) {
                    shape = artifacts.isEmpty() ? "oval" : "none";
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
