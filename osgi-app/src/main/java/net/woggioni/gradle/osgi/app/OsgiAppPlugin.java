package net.woggioni.gradle.osgi.app;

import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import org.gradle.api.Action;
import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.artifacts.ConfigurationContainer;
import org.gradle.api.artifacts.Dependency;
import org.gradle.api.artifacts.ExternalDependency;
import org.gradle.api.artifacts.ModuleVersionIdentifier;
import org.gradle.api.artifacts.dsl.DependencyHandler;
import org.gradle.api.attributes.LibraryElements;
import org.gradle.api.file.DuplicatesStrategy;
import org.gradle.api.file.FileCollection;
import org.gradle.api.file.FileCopyDetails;
import org.gradle.api.plugins.BasePluginExtension;
import org.gradle.api.plugins.ExtraPropertiesExtension;
import org.gradle.api.plugins.JavaPlugin;
import org.gradle.api.provider.Provider;
import org.gradle.api.specs.Spec;
import org.gradle.api.tasks.JavaExec;
import org.gradle.api.tasks.bundling.Jar;

import java.io.File;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.TreeMap;
import java.util.function.Supplier;
import java.util.jar.JarFile;
import java.util.stream.Collectors;

public class OsgiAppPlugin implements Plugin<Project> {

    private static void applyDependencySubstitution(Configuration conf) {
        conf.getResolutionStrategy().dependencySubstitution(dependencySubstitutions -> {
            //Replace Kotlin stdlib
            dependencySubstitutions.substitute(dependencySubstitutions.module("org.jetbrains.kotlin:kotlin-stdlib-common"))
                    .using(dependencySubstitutions.module("org.jetbrains.kotlin:kotlin-osgi-bundle:$kotlinVersion"));
            dependencySubstitutions.substitute(dependencySubstitutions.module("org.jetbrains.kotlin:kotlin-stdlib"))
                    .using(dependencySubstitutions.module("org.jetbrains.kotlin:kotlin-osgi-bundle:$kotlinVersion"));
            dependencySubstitutions.substitute(dependencySubstitutions.module("org.jetbrains.kotlin:kotlin-reflect"))
                    .using(dependencySubstitutions.module("org.jetbrains.kotlin:kotlin-osgi-bundle:$kotlinVersion"));
        });
    }

    private static Map<String, String> createDependencyNotation(String group, String name, String version) {
        Map<String, String> m = new TreeMap<>();
        m.put("group", group);
        m.put("name", name);
        m.put("version", version);
        return Collections.unmodifiableMap(m);
    }

    private static Dependency createProjectDependency(DependencyHandler handler, String path) {
        return createProjectDependency(handler, path, null);
    }

    private static Dependency createProjectDependency(DependencyHandler handler, String path, String configuration) {
        Map<String, String> m = new TreeMap<>();
        m.put("path", path);
        if(configuration != null) {
            m.put("configuration", configuration);
        }
        return handler.project(m);
    }

    @Getter
    @EqualsAndHashCode
    @RequiredArgsConstructor
    private static final class NameAndGroup {
        private final String group;
        private final String name;
    }

    private static void alignVersionsTo(ConfigurationContainer cc, String sourceConfigurationName, Configuration target) {
        target.withDependencies(dependencies -> {
            Map<NameAndGroup, String> modules = new HashMap<>();
            cc.getByName(sourceConfigurationName).getIncoming().getResolutionResult().getAllComponents().forEach(resolvedComponentResult -> {
                ModuleVersionIdentifier moduleVersionIdentifier = resolvedComponentResult.getModuleVersion();
                modules.put(new NameAndGroup(moduleVersionIdentifier.getGroup(), moduleVersionIdentifier.getName()), moduleVersionIdentifier.getVersion());
            });
            dependencies.configureEach(dependency -> {
                if(dependency instanceof ExternalDependency) {
                    NameAndGroup needle = new NameAndGroup(dependency.getGroup(), dependency.getName());
                    String constrainedVersion = modules.get(needle);
                    if(constrainedVersion != null) {
                        ((ExternalDependency) dependency).version(versionConstraint -> {
                            versionConstraint.require(constrainedVersion);
                        });
                    }
                }
            });
        });
    }


    @Override
    @SneakyThrows
    public void apply(Project project) {
        project.getPlugins().apply("java-library");
        project.getPlugins().apply("biz.aQute.bnd.builder");

        OsgiAppExtension osgiAppExtension = project.getExtensions().create("osgiApp", OsgiAppExtension.class);

        ExtraPropertiesExtension ext = project.getExtensions().getExtraProperties();

        ConfigurationContainer cc = project.getConfigurations();

        Provider<Configuration> runtimeClasspathConfiguration = cc.named(JavaPlugin.RUNTIME_CLASSPATH_CONFIGURATION_NAME, conf -> {
            conf.getAttributes()
                .attribute(LibraryElements.LIBRARY_ELEMENTS_ATTRIBUTE,
                        project.getObjects().named(LibraryElements.class, LibraryElements.JAR));
        });

        Configuration systemPackagesConf = cc.create(OsgiAppExtension.SYSTEM_PACKAGES_CONFIGURATION_NAME);
        systemPackagesConf.setCanBeConsumed(false);
        systemPackagesConf.setTransitive(false);
        applyDependencySubstitution(systemPackagesConf);

        cc.named(JavaPlugin.COMPILE_ONLY_CONFIGURATION_NAME, conf -> conf.extendsFrom(systemPackagesConf));

        Provider<Configuration> bootstrapClasspathConf = cc.register(OsgiAppExtension.BOOTSTRAP_CLASSPATH_CONFIGURATION_NAME, conf -> {
            conf.setCanBeConsumed(false);
            conf.setTransitive(true);
            conf.extendsFrom(systemPackagesConf);
            applyDependencySubstitution(conf);
            alignVersionsTo(project.getConfigurations(), JavaPlugin.RUNTIME_CLASSPATH_CONFIGURATION_NAME, conf);
        });

        Provider<Configuration> bundlesConf = cc.register(OsgiAppExtension.BUNDLES_CONFIGURATION_NAME, conf -> {
            conf.setTransitive(true);
            conf.setCanBeConsumed(false);
            conf.extendsFrom(cc.getAt(JavaPlugin.RUNTIME_CLASSPATH_CONFIGURATION_NAME));
            conf.getAttributes().attribute(LibraryElements.LIBRARY_ELEMENTS_ATTRIBUTE, project.getObjects().named(LibraryElements.class, LibraryElements.JAR));
            applyDependencySubstitution(conf);
        });

        cc.named(JavaPlugin.COMPILE_ONLY_CONFIGURATION_NAME, conf -> conf.extendsFrom(systemPackagesConf));

        DependencyHandler dependencyHandler = project.getDependencies();

        Provider<Map<String, String>> bootstrapperDependencyNotationProvider = project.provider(() ->
            createDependencyNotation(
                    OsgiAppExtension.BOOTSTRAPPER_GROUP,
                    OsgiAppExtension.BOOTSTRAPPER_NAME,
                    osgiAppExtension.getBootstrapperVersion().get()));
        Provider<Map<String, String>> bootstrapperApiDependencyNotationProvider = project.provider(() ->
                createDependencyNotation(
                        OsgiAppExtension.BOOTSTRAPPER_GROUP,
                        OsgiAppExtension.BOOTSTRAPPER_API_NAME,
                        osgiAppExtension.getBootstrapperVersion().get()));
        Provider<Map<String, String>> bootstrapperApplicationDependencyNotationProvider = project.provider(() ->
                createDependencyNotation(
                        OsgiAppExtension.BOOTSTRAPPER_GROUP,
                        OsgiAppExtension.BOOTSTRAPPER_APPLICATION_NAME,
                        osgiAppExtension.getBootstrapperVersion().get()));

        project.getDependencies().addProvider(OsgiAppExtension.BOOTSTRAP_CLASSPATH_CONFIGURATION_NAME,
                bootstrapperDependencyNotationProvider,
                it -> {});
        project.getDependencies().addProvider(OsgiAppExtension.BUNDLES_CONFIGURATION_NAME,
                bootstrapperApplicationDependencyNotationProvider,
                it -> {});
        project.getDependencies().addProvider(OsgiAppExtension.SYSTEM_PACKAGES_CONFIGURATION_NAME,
                bootstrapperApiDependencyNotationProvider,
                it -> {});

        Provider<PropertyFileTask> frameworkPropertyFileTaskProvider = project.getTasks().register("frameworkPropertyFile", PropertyFileTask.class, task -> {
            task.getFileName().set("framework.properties");
            task.getProperties().set(osgiAppExtension.getFrameworkProperties());
        });

        Provider<PropertyFileTask> systemPropertyFileTaskProvider = project.getTasks().register("systemPropertyFile", PropertyFileTask.class, task -> {
            task.getFileName().set("system.properties");
            task.getProperties().set(osgiAppExtension.getSystemProperties());
        });

        Provider<JavaAgentFileTask> javaAgentFileTask = project.getTasks().register("javaAgentFile", JavaAgentFileTask.class, task -> {
            task.getJavaAgents().set(osgiAppExtension.javaAgents);
        });

        Provider<Jar> jarFileTask = project.getTasks().named(JavaPlugin.JAR_TASK_NAME, Jar.class);
        Supplier<FileCollection> bundlesSupplier = () -> {
            return bundlesConf.get()
                .minus(systemPackagesConf).plus(project.files(jarFileTask.get().getArchiveFile()))
                .filter(new Spec<File>() {
                    @Override
                    @SneakyThrows
                    public boolean isSatisfiedBy(File file) {
                        return OsgiAppUtils.isJar(file.getName());
                    }
                });
        };

        Provider<SystemPackageExtraFileTask> systemPackageExtraFileTask =
            project.getTasks().register("systemPackageExtraFile", SystemPackageExtraFileTask.class, t -> {
                t.getExtraSystemPackages().set(osgiAppExtension.getSystemPackages());
        });

        Provider<BundleFileTask> bundleFileTask = project.getTasks()
                .register("bundleFile", BundleFileTask.class, task -> {
                    task.getInputs().files(jarFileTask);
                    FileCollection bundles = bundlesSupplier.get();
                    task.getInputs().files(bundlesSupplier.get());
                    bundles.forEach(task::bundle);
                });

        Provider<Jar> osgiJar = project.getTasks().register("osgiJar", Jar.class, (Jar task) -> {
            BasePluginExtension basePluginExtension = project.getExtensions()
                    .findByType(BasePluginExtension.class);
            task.getDestinationDirectory().set(basePluginExtension.getDistsDirectory());
            task.getArchiveClassifier().set("osgi");

            FileCollection bundles = bundlesSupplier.get();
            Provider<Jar> jarTaskProvider = project.getTasks().named(JavaPlugin.JAR_TASK_NAME, Jar.class);
            task.getInputs().files(bootstrapClasspathConf);
            task.getInputs().files(bundles);
            task.getInputs().files(systemPackagesConf);
//            task.getInputs().file(jarTaskProvider);

            task.exclude("META-INF/MANIFEST.MF");
            task.exclude("META-INF/*.SF");
            task.exclude("META-INF/*.DSA");
            task.exclude("META-INF/*.RSA");
            task.exclude("META-INF/*.EC");
            task.exclude("META-INF/DEPENDENCIES");
            task.exclude("META-INF/LICENSE");
            task.exclude("META-INF/NOTICE");
            task.exclude("module-info.class");
            task.exclude("META-INF/versions/*/module-info.class");
            task.setDuplicatesStrategy(DuplicatesStrategy.WARN);
            MapBuilder<String, String, TreeMap<String,String>> mapBuilder = MapBuilder.getInstance(TreeMap<String,String>::new)
                    .of("Main-Class", "net.woggioni.osgi.simple.bootstrapper.Bootstrapper")
                    .of("Launcher-Agent-Class", "net.woggioni.osgi.simple.bootstrapper.JavaAgentLauncher")
                    .of("Can-Redefine-Classes", Boolean.toString(true))
                    .of("Can-Retransform-Classes", Boolean.toString(true));
            if(osgiAppExtension.getMainApplicationComponent().isPresent()) {
                mapBuilder.of("Main-Application-Component", osgiAppExtension.getMainApplicationComponent().get());
            }
            task.getManifest().attributes(mapBuilder.buildImmutable());


            task.into("META-INF/", copySpec -> {
                copySpec.from(javaAgentFileTask);
                copySpec.from(bundleFileTask);
                copySpec.from(frameworkPropertyFileTaskProvider);
                copySpec.from(systemPropertyFileTaskProvider);
                copySpec.from(systemPackageExtraFileTask);
            });
            task.from(bootstrapClasspathConf.get().getFiles().stream().map(it -> {
                if(it.isDirectory()) {
                    return it;
                } else {
                    return project.zipTree(it);
                }
            }).collect(Collectors.toList()));
            task.into("bundles", copySpec -> {
                copySpec.from(bundlesSupplier.get(), copySpec2 ->
                    copySpec2.eachFile(new Action<FileCopyDetails>() {
                       @Override
                       @SneakyThrows
                       public void execute(FileCopyDetails fcd) {
                           try (JarFile jarFile = new JarFile(fcd.getFile())) {
                               if (!OsgiAppUtils.isBundle(jarFile)) {
                                   fcd.exclude();
                               }
                           }
                       }
                   })
                );
            });
        });

        project.getTasks().register("osgiRun", JavaExec.class, javaExec -> {
            javaExec.setClasspath(project.files(osgiJar));
        });

        Provider<FrameworkRuntimeCheck> frameworkRuntimeCheckTaskProvider =
            project.getTasks().register("frameworkRuntimeCheck",
                FrameworkRuntimeCheck.class, bootstrapClasspathConf);
        project.getTasks().named(JavaPlugin.CLASSES_TASK_NAME,
                t -> t.dependsOn(frameworkRuntimeCheckTaskProvider));
    }
}
