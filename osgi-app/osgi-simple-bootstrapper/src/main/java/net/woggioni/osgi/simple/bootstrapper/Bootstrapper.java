package net.woggioni.osgi.simple.bootstrapper;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import net.woggioni.osgi.simple.bootstrapper.api.FrameworkService;
import org.osgi.framework.Bundle;
import org.osgi.framework.BundleContext;
import org.osgi.framework.BundleEvent;
import org.osgi.framework.Constants;
import org.osgi.framework.FrameworkEvent;
import org.osgi.framework.launch.Framework;
import org.osgi.framework.launch.FrameworkFactory;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.AllPermission;
import java.security.CodeSource;
import java.security.Permission;
import java.security.PermissionCollection;
import java.security.Policy;
import java.util.AbstractMap;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.Enumeration;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Properties;
import java.util.ServiceLoader;
import java.util.function.Consumer;
import java.util.jar.JarFile;
import java.util.jar.Manifest;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@RequiredArgsConstructor
enum BundleState {
    STARTING(Bundle.STARTING, "starting"),
    INSTALLED(Bundle.INSTALLED, "installed"),
    STOPPING(Bundle.STOPPING, "stopping"),
    ACTIVE(Bundle.ACTIVE, "active"),
    RESOLVED(Bundle.RESOLVED, "resolved"),
    UNINSTALLED(Bundle.UNINSTALLED, "uninstalled");

    @Getter
    private final int code;

    @Getter
    private final String description;

    public static BundleState fromCode(int code) {
        return Arrays.stream(values())
                .filter(it -> it.code == code)
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Unknown bundle state with code $code"));
    }
}

@RequiredArgsConstructor
class FrameworkListener implements org.osgi.framework.FrameworkListener {
    private static final Logger log = Logger.getLogger(FrameworkListener.class.getName());
    private final Framework framework;

    private static String eventMessage(FrameworkEvent evt) {
        StringBuilder sb = new StringBuilder();
        Bundle bundle =evt.getBundle();
        sb.append("Bundle ");
        sb.append(bundle.getSymbolicName());
        sb.append("-");
        sb.append(bundle.getVersion());
        sb.append(':');
        Optional.ofNullable(evt.getThrowable())
                .map(Throwable::getMessage)
                .ifPresent(sb::append);
        return sb.toString();
    }

    @Override
    public void frameworkEvent(FrameworkEvent evt) {
        switch (evt.getType()) {
            case FrameworkEvent.ERROR:
                log.log(Level.SEVERE, evt.getThrowable(),
                        () -> eventMessage(evt));
                break;
            case FrameworkEvent.WARNING:
                log.log(Level.WARNING, evt.getThrowable(), () -> eventMessage(evt));
                break;
            case FrameworkEvent.INFO:
                log.log(Level.INFO, evt.getThrowable(), () -> eventMessage(evt));
                break;
            case FrameworkEvent.STARTED:
                log.log(Level.INFO, () -> String.format("OSGI framework '%s' started",
                        framework.getClass().getName()));
                break;
            case FrameworkEvent.WAIT_TIMEDOUT:
                log.log(Level.WARNING, () -> String.format("OSGI framework '%s' did not stop",
                        framework.getClass().getName()));
                break;
            case FrameworkEvent.STOPPED:
                log.log(Level.INFO, () -> String.format("OSGI framework '%s' stopped",
                        framework.getClass().getName()));
                break;
        }
    }
}

final class BundleListener implements org.osgi.framework.BundleListener {
    private static final Logger log = Logger.getLogger(BundleListener.class.getName());

    @Override
    public void bundleChanged(BundleEvent evt) {
        Bundle bundle = evt.getBundle();
        log.fine(() -> String.format("Bundle-Location: %s, " +
                        "Bundle ID: %s, Bundle-SymbolicName: %s, Bundle-Version: %s, State: %s",
                bundle.getLocation(),
                bundle.getBundleId(),
                bundle.getSymbolicName(),
                bundle.getVersion(),
                BundleState.fromCode(bundle.getState()).getDescription()));
    }
}

class AllPolicy extends Policy {
    private static final Logger log = Logger.getLogger(BundleListener.class.getName());
    private static final PermissionCollection all = new PermissionCollection() {
        private final List<Permission> PERMISSIONS = Collections.singletonList(new AllPermission());

        {
            setReadOnly();
        }

        @Override
        public void add(Permission permission) {}

        @Override
        public Enumeration<Permission> elements() {
            return Collections.enumeration(PERMISSIONS);
        }

        @Override
        public boolean implies(Permission permission) {
            return true;
        }
    };

    @Override
    public PermissionCollection getPermissions(CodeSource codesource) {
        if (codesource == null)
            log.finest("Granting AllPermission to a bundle without codesource!");
        else
            log.finest(String.format("Granting AllPermission to %s", codesource.getLocation()));
        return all;
    }

    @Override
    public void refresh() {
        log.finest("Policy refresh");
    }
}

public class Bootstrapper implements AutoCloseable {
    private static final String BUNDLE_LIST_FILE = "META-INF/bundles";
    private static final String SYSTEM_PACKAGES_FILE = "META-INF/system_packages";
    private static final String SYSTEM_PROPERTIES_FILE = "META-INF/system.properties";
    private static final String FRAMEWORK_PROPERTIES_FILE = "META-INF/framework.properties";
    private static final String MAIN_APPLICATION_COMPONENT_ATTRIBUTE = "Main-Application-Component";

    private static final Logger log = Logger.getLogger(Bootstrapper.class.getName());

    @SneakyThrows
    private FrameworkFactory getFrameWorkFactory() {
        ServiceLoader<FrameworkFactory> serviceLoader = ServiceLoader.load(FrameworkFactory.class);
        for (FrameworkFactory frameworkFactory : serviceLoader) {
            return frameworkFactory;
        }
        throw new IllegalStateException(String.format(
                "No provider found for service '%s'", FrameworkFactory.class));
    }

    @SneakyThrows
    private static String loadSystemPackages() {
        URL resourceUrl = Bootstrapper.class.getClassLoader().getResource(SYSTEM_PACKAGES_FILE);
        if(resourceUrl != null) {
            try(BufferedReader reader = new BufferedReader(new InputStreamReader(resourceUrl.openStream()))) {
                return reader.lines().collect(Collectors.joining(","));
            }
        } else {
            throw new IOException(String.format("'%s' not found", SYSTEM_PACKAGES_FILE));
        }
    }


    private final String[] cliArgs;
    private final Path storageDir;
    private final Framework framework;
    private final String mainApplicationComponentName;

    @Getter(AccessLevel.PACKAGE)
    private int exitCode = 0;

    @SneakyThrows
    private Bootstrapper(String[] cliArgs) {
        this.cliArgs = cliArgs;
        this.storageDir = Files.createTempDirectory("osgi-cache");
        
        InputStream is = getClass().getClassLoader().getResourceAsStream(SYSTEM_PROPERTIES_FILE);
        if(is != null) {
            Properties props = new Properties();
            try(Reader reader = new InputStreamReader(is)) {
                props.load(reader);
            }
            props.forEach((key, value) -> System.getProperties().computeIfAbsent(key, k -> value));
        }

        Stream<Map.Entry<String,String>> entryStream  = Stream.of(
                new AbstractMap.SimpleEntry<>(Constants.FRAMEWORK_STORAGE, storageDir.toString()),
                new AbstractMap.SimpleEntry<>(Constants.FRAMEWORK_STORAGE_CLEAN, Constants.FRAMEWORK_STORAGE_CLEAN_ONFIRSTINIT),
                new AbstractMap.SimpleEntry<>(Constants.FRAMEWORK_SYSTEMPACKAGES_EXTRA, loadSystemPackages())
        );


        is = getClass().getClassLoader().getResourceAsStream(FRAMEWORK_PROPERTIES_FILE);
        if(is != null) {
            Properties props = new Properties();
            try(Reader reader = new InputStreamReader(is)) {
                props.load(reader);
            }
            entryStream = Stream.concat(entryStream,
                    props.entrySet().stream()
                            .map(it -> new AbstractMap.SimpleEntry<>((String) it.getKey(),  (String) it.getValue())));
        }
        entryStream = Stream.concat(entryStream,
                System.getProperties().entrySet().stream()
                        .map(it -> new AbstractMap.SimpleEntry<>((String) it.getKey(),  (String) it.getValue())));
        Map<String, String> frameworkPropertyMap = entryStream.collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
        framework = getFrameWorkFactory().newFramework(frameworkPropertyMap);
        Manifest mf = new Manifest();
        URL manifestURL = getClass().getClassLoader().getResource(JarFile.MANIFEST_NAME);
        if(manifestURL != null) {
            mf.read(manifestURL.openStream());
        }
        mainApplicationComponentName = mf.getMainAttributes().getValue(MAIN_APPLICATION_COMPONENT_ATTRIBUTE);
        Policy.setPolicy(new AllPolicy());
    }

    @SneakyThrows
    private void start() {
        log.fine(() -> String.format("Starting OSGi framework %s %s",
                framework.getClass().getName(), framework.getVersion()));
        framework.start();
        framework.getBundleContext().addFrameworkListener(new FrameworkListener(framework));
        framework.getBundleContext().addBundleListener(new BundleListener());
        InputStream inputStream = getClass().getClassLoader().getResourceAsStream(BUNDLE_LIST_FILE);
        BundleContext ctx = framework.getBundleContext();
        try(BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream))) {
            reader.lines().forEach(new Consumer<String>() {
               @Override
               @SneakyThrows
               public void accept(String line) {
                   Enumeration<URL> it = getClass().getClassLoader().getResources(line);
                   while (it.hasMoreElements()) {
                       URL url = it.nextElement();
                       try (InputStream bundleInputStream = url.openStream()) {
                           ctx.installBundle(url.toString(), bundleInputStream);
                       }
                   }
               }
           });
        }
        ctx.registerService(FrameworkService.class, new FrameworkService() {
            @Override
            public String[] getArgs() {
                return cliArgs;
            }

            @Override
            public void setExitCode(int exitCode) {
                Bootstrapper.this.exitCode = exitCode;
            }

            @Override
            public String getMainApplicationComponentName() {
                return mainApplicationComponentName;
            }
        }, null);
    }

    @SneakyThrows
    private void activate(long ...bundleId) {
        BundleContext ctx = framework.getBundleContext();

        if(bundleId.length == 0) {
            for(Bundle bundle : ctx.getBundles()) {
                if(bundle.getHeaders().get(Constants.FRAGMENT_HOST) == null &&
                    (bundle.getState() == BundleState.INSTALLED.getCode() || bundle.getState() == BundleState.RESOLVED.getCode())) {
                    bundle.start();
                }
            }
        } else {
            for(long id : bundleId) {
                ctx.getBundle(id).start();
            }
        }
    }

    @Override
    @SneakyThrows
    public void close() {
            if(framework.getState() == BundleState.ACTIVE.getCode() || framework.getState() == BundleState.STARTING.getCode()) {
            framework.stop();
            waitForStop();
            Files.walk(storageDir)
                    .sorted(Comparator.reverseOrder())
                    .forEach(new Consumer<Path>() {
                        @Override
                        @SneakyThrows
                        public void accept(Path path) {
                            Files.delete(path);
                        }
                    });
        }
    }

    private void waitForStop() {
        waitForStop(5000L);
    }

    @SneakyThrows
    private void waitForStop(long timeout) {
        FrameworkEvent evt = framework.waitForStop(timeout);
        switch (evt.getType()) {
            case FrameworkEvent.ERROR:
                log.log(Level.SEVERE, evt.getThrowable().getMessage(), evt.getThrowable());
                throw evt.getThrowable();
            case FrameworkEvent.WAIT_TIMEDOUT:
                log.warning("OSGi framework shutdown timed out");
                break;
            case FrameworkEvent.STOPPED:
                break;
            default:
                throw new IllegalStateException(String.format("Unknown event type %d", evt.getType()));
        }
    }

    public static void main(String[] args) {
        int exitCode;
        Bootstrapper cnt = new Bootstrapper(args);
        Runtime.getRuntime().addShutdownHook(new Thread(cnt::close));
        try {
            cnt.start();
            cnt.activate();
            cnt.waitForStop(0L);
        } finally {
            cnt.close();
        }
        exitCode = cnt.getExitCode();
        System.exit(exitCode);
    }
}
