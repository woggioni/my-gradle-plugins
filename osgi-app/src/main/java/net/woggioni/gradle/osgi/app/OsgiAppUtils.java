package net.woggioni.gradle.osgi.app;

import aQute.bnd.osgi.Constants;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import lombok.SneakyThrows;

import java.util.jar.JarFile;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class OsgiAppUtils {

    @SneakyThrows
    static boolean isBundle(JarFile jarFile) {
        java.util.jar.Attributes mainAttributes = jarFile.getManifest().getMainAttributes();
        return mainAttributes.getValue(Constants.BUNDLE_SYMBOLICNAME) != null && mainAttributes.getValue(Constants.BUNDLE_VERSION) != null;
    }

    static boolean isJar(String fileName) {
        return fileName.endsWith(".jar");
    }
}
