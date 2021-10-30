package net.woggioni.gradle.osgi.app;

import aQute.bnd.osgi.Constants;
import groovy.lang.Tuple2;
import lombok.SneakyThrows;
import org.gradle.api.DefaultTask;
import org.gradle.api.tasks.OutputFile;
import org.gradle.api.tasks.TaskAction;
import org.osgi.framework.Version;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStreamWriter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.jar.Attributes;
import java.util.jar.JarFile;

public class BundleFileTask extends DefaultTask {

    private final File systemBundleFile;
    private final List<File> bundles;

    public BundleFileTask() {
        systemBundleFile = new File(getTemporaryDir(), "bundles");
        bundles = new ArrayList<>();
    }

    @OutputFile
    public File getOutputFile() {
        return systemBundleFile;
    }

    public void bundle(File file) {
        bundles.add(file);
    }

    @TaskAction
    @SneakyThrows
    public void run() {
        Map<Tuple2<String, Version>, String> map = new TreeMap<>();
        for(File bundleFile : bundles) {
            try(JarFile jarFile = new JarFile(bundleFile)) {
                Attributes mainAttributes = jarFile.getManifest().getMainAttributes();
                String bundleSymbolicName = mainAttributes.getValue(Constants.BUNDLE_SYMBOLICNAME);
                String bundleVersion = mainAttributes.getValue(Constants.BUNDLE_VERSION);
                if(bundleSymbolicName != null && bundleVersion != null) {
                    Tuple2<String, Version> key = new Tuple2<>(
                            bundleSymbolicName,
                            Version.parseVersion(bundleVersion));
                    map.put(key, bundleFile.getName());
                }
            }
        }
        try(BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(systemBundleFile)))) {
            for(Map.Entry<Tuple2<String, Version>, String> entry : map.entrySet()) {
                writer.write("bundles/" + entry.getValue());
                writer.newLine();
            }
        }
    }
}
