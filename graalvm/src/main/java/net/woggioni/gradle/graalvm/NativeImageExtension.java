package net.woggioni.gradle.graalvm;

import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.file.FileCollection;
import org.gradle.api.provider.Property;

abstract public class NativeImageExtension {
    public abstract Property<FileCollection> getClasspath();

    public abstract DirectoryProperty getGraalVmHome();

    public abstract Property<Boolean> getUseMusl();
    public abstract Property<Boolean> getBuildStaticImage();
    public abstract Property<Boolean> getEnableFallback();
    public abstract Property<Boolean> getLinkAtBuildTime();

    public abstract Property<String> getMainClass();

    public abstract Property<String> getMainModule();

    public abstract Property<Boolean> getCompressExecutable();

    public abstract Property<Boolean> getUseLZMA();

    public abstract Property<Integer> getCompressionLevel();
}
