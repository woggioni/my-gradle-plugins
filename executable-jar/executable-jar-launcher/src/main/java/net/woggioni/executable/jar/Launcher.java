package net.woggioni.executable.jar;

import java.io.InputStream;
import java.lang.module.Configuration;
import java.lang.module.ModuleFinder;
import java.lang.reflect.Method;
import java.net.URI;
import java.net.URL;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.jar.Attributes;
import java.util.jar.JarFile;
import java.util.jar.Manifest;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;
import lombok.SneakyThrows;
import lombok.extern.java.Log;

@Log
public class Launcher {

    @SneakyThrows
    private static URI findCurrentJar() {
        String launcherClassName = Launcher.class.getName();
        URL url = Launcher.class.getClassLoader().getResource(launcherClassName.replace('.', '/') + ".class");
        if (url == null || !"jar".equals(url.getProtocol()))
            throw new IllegalStateException(String.format("The class %s must be used inside a JAR file", launcherClassName));
        String path = url.getPath();
        return new URI(path.substring(0, path.indexOf('!')));
    }

    @SneakyThrows
    public static void main(String[] args) {
        URI currentJar = findCurrentJar();
        URL manifestResource = Launcher.class.getResource("/" + JarFile.MANIFEST_NAME);
        if(Objects.isNull(manifestResource)) {
            throw new RuntimeException("Launcher manifest not found");
        }
        Manifest mf = new Manifest();
        try(InputStream is = manifestResource.openStream()) {
            mf.read(is);
        }
        try(FileSystem fs = FileSystems.newFileSystem(Path.of(currentJar.getPath()), null)) {
            Attributes mainAttributes = mf.getMainAttributes();

            List<Path> jarList = StreamSupport.stream(fs.getRootDirectories().spliterator(), false).flatMap(new Function<Path, Stream<Path>>() {
                @Override
                @SneakyThrows
                public Stream<Path> apply(Path path) {
                    return Files.list(path.resolve(Constants.LIBRARIES_FOLDER))
                            .filter(Files::isRegularFile)
                            .filter(p -> p.getFileName().toString().endsWith(".jar"));
                }
            }).flatMap(new Function<Path, Stream<Path>>() {
                @Override
                @SneakyThrows
                public Stream<Path> apply(Path path) {
                    return StreamSupport.stream(FileSystems.newFileSystem(path, null).getRootDirectories().spliterator(), false);
                }
            }).collect(Collectors.toUnmodifiableList());

//            for (Map.Entry<String, Attributes> entry : mf.getEntries().entrySet()) {
//                String jarEntryName = entry.getKey();
//                Attributes attributes = entry.getValue();
//                if (jarEntryName.startsWith(Constants.LIBRARIES_FOLDER + '/') && attributes.getValue(Constants.ManifestAttributes.ENTRY_HASH) != null) {
//                    jarList.add(fs.getPath(jarEntryName));
//                }
//            }

            Path[] jars = jarList.toArray(new Path[jarList.size()]);
            ClassLoader pathClassLoader = new PathClassLoader(jars);
            String mainClassName = mainAttributes.getValue(Constants.ManifestAttributes.MAIN_CLASS);
            String mainModuleName = mainAttributes.getValue(Constants.ManifestAttributes.MAIN_MODULE);
            Class<?> mainClass;
            if (mainModuleName == null) {
                mainClass = pathClassLoader.loadClass(mainClassName);
            } else {
                ModuleLayer bootLayer = ModuleLayer.boot();
                Configuration bootConfiguration = ModuleLayer.boot().configuration();
                Configuration cfg = bootConfiguration.resolve(ModuleFinder.of(jars), ModuleFinder.of(), Arrays.asList(mainModuleName));
                ModuleLayer layer = bootLayer.defineModulesWithOneLoader(cfg, pathClassLoader);
                Module mainModule = layer.findModule(mainModuleName).orElseThrow(
                        () -> new IllegalStateException(String.format("Main module '%s' not found", mainModuleName)));
                mainClass = Class.forName(mainModule, mainClassName);
            }
            try {
                Method mainMethod = mainClass.getMethod("main", String[].class);
                Class<?> returnType = mainMethod.getReturnType();
                if (mainMethod.getReturnType() != Void.TYPE) {
                    throw new IllegalArgumentException(String.format("Main method in class '%s' " +
                            "has wrong return type, expected '%s', found '%s' instead", mainClass, Void.class.getName(), returnType));
                }
                mainMethod.invoke(null, (Object) args);
            } catch (NoSuchMethodException nsme) {
                throw new IllegalArgumentException(String.format("No valid main method found in class '%s'", mainClass), nsme);
            }
        }
    }
}
