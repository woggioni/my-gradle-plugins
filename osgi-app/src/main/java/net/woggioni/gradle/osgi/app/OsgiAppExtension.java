package net.woggioni.gradle.osgi.app;

import lombok.Getter;
import org.gradle.api.model.ObjectFactory;
import org.gradle.api.provider.ListProperty;
import org.gradle.api.provider.MapProperty;
import org.gradle.api.provider.Property;

import javax.inject.Inject;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

class OsgiAppExtension {

    public static final String BOOTSTRAPPER_GROUP = "net.woggioni.osgi";
    public static final String BOOTSTRAPPER_NAME = "osgi-simple-bootstrapper";
    public static final String BOOTSTRAPPER_API_NAME = "osgi-simple-bootstrapper-api";
    public static final String BOOTSTRAPPER_APPLICATION_NAME = "osgi-simple-bootstrapper-application";

    public static final String BOOTSTRAP_CLASSPATH_CONFIGURATION_NAME = "bootstrapClasspath";
    public static final String BUNDLES_CONFIGURATION_NAME = "bundles";
    public static final String SYSTEM_PACKAGES_CONFIGURATION_NAME = "systemPackages";


    final List<JavaAgent> javaAgents = new ArrayList<>();

    @Getter
    private final MapProperty<String, String> frameworkProperties;

    @Getter
    private final MapProperty<String, String> systemProperties;

    @Getter
    private final Property<String> bootstrapperVersion;

    @Getter
    private final ListProperty<String> systemPackages;

    @Getter
    private final Property<String> mainApplicationComponent;

    @Getter
    private final Property<String> frameworkFactoryClass;

    @Inject
    public OsgiAppExtension(ObjectFactory objects) {
        frameworkFactoryClass = objects.property(String.class)
                .convention("org.apache.felix.framework.FrameworkFactory");
        frameworkProperties = objects.mapProperty(String.class, String.class).convention(new HashMap<>());
        systemProperties = objects.mapProperty(String.class, String.class).convention(new HashMap<>());
        bootstrapperVersion = objects.property(String.class);
        systemPackages = objects.listProperty(String.class).convention(
            Stream.of(
                "sun.net.www.protocol.jar",
                "sun.nio.ch",
                "sun.security.x509",
                "sun.security.ssl",
                "javax.servlet",
                "javax.transaction.xa;version=1.1.0",
                "javax.xml.stream;version=1.0",
                "javax.xml.stream.events;version=1.0",
                "javax.xml.stream.util;version=1.0").collect(Collectors.toList()));
        mainApplicationComponent = objects.property(String.class);
    }

    public void agent(String className, String args) {
        javaAgents.add(new JavaAgent(className, args));
    }
}
