package net.woggioni.gradle.osgi.app;

import aQute.bnd.header.Attrs;
import aQute.bnd.header.OSGiHeader;
import lombok.Getter;
import lombok.SneakyThrows;
import org.gradle.api.DefaultTask;
import org.gradle.api.artifacts.ArtifactCollection;
import org.gradle.api.artifacts.ResolvableDependencies;
import org.gradle.api.artifacts.result.ResolvedArtifactResult;
import org.gradle.api.file.FileCollection;
import org.gradle.api.model.ObjectFactory;
import org.gradle.api.provider.ListProperty;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.InputFiles;
import org.gradle.api.tasks.OutputFile;
import org.gradle.api.tasks.TaskAction;

import javax.inject.Inject;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStreamWriter;
import java.util.Map;
import java.util.NavigableSet;
import java.util.TreeSet;
import java.util.jar.JarFile;

import static net.woggioni.gradle.osgi.app.OsgiAppUtils.isBundle;
import static net.woggioni.gradle.osgi.app.OsgiAppUtils.isJar;

public class SystemPackageExtraFileTask extends DefaultTask {

    @Getter(onMethod_ = @Input)
    private final ListProperty<String> extraSystemPackages;

    final File systemPackagesExtraFile;

    @Inject
    public SystemPackageExtraFileTask(ObjectFactory objects) {
        extraSystemPackages = objects.listProperty(String.class);
        systemPackagesExtraFile = new File(getTemporaryDir(), "system_packages");
    }

    @InputFiles
    FileCollection getInputFiles() {
        return getProject().getConfigurations().getByName("systemPackages");
    }

    @OutputFile
    File getOutputFile() {
        return systemPackagesExtraFile;
    }

    @SneakyThrows
    private NavigableSet<String> buildPackagesExtra(ArtifactCollection artifacts) {
        TreeSet<String> result = new TreeSet<>();
        for(ResolvedArtifactResult resolvedArtifactResult : artifacts.getArtifacts()) {
            if(isJar(resolvedArtifactResult.getFile().getName())) {
                try(JarFile jarFile = new JarFile(resolvedArtifactResult.getFile(), true, JarFile.OPEN_READ, JarFile.runtimeVersion())) {
                    if (isBundle(jarFile)) {
                        String exportPackages = jarFile.getManifest().getMainAttributes().getValue(org.osgi.framework.Constants.EXPORT_PACKAGE);
                        if(exportPackages != null) {
                            for (Map.Entry<String, Attrs> entry : OSGiHeader.parseHeader(exportPackages).entrySet()) {
                                String exportString = entry.getKey() + ";" + entry.getValue().toString();
                                result.add(exportString);
                            }
                        }
                    } else {
                        jarFile.versionedStream()
                                .filter(jarEntry -> jarEntry.getName().endsWith(".class"))
                                .forEach(jarEntry -> {
                                    String entryName = jarEntry.getName();
                                    int end = entryName.lastIndexOf('/');
                                    if (end > 0) {
                                        String export = entryName.substring(0, end).replace('/', '.');
                                        result.add(export);
                                    }
                                });
                    }
                }
            }
        }
        result.addAll(extraSystemPackages.get());
        return result;
    }

    @TaskAction
    @SneakyThrows
    public void run() {
        ResolvableDependencies resolvableDependencies = getProject().getConfigurations().getByName(OsgiAppExtension.SYSTEM_PACKAGES_CONFIGURATION_NAME).getIncoming();
        try(BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(systemPackagesExtraFile)))) {
            for(String export : buildPackagesExtra(resolvableDependencies.getArtifacts())) {
                writer.write(export);
                writer.newLine();
            }
        }
    }
}
