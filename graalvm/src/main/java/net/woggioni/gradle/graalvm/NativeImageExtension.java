package net.woggioni.gradle.graalvm;

import org.gradle.api.Action;
import org.gradle.api.file.FileCollection;
import org.gradle.api.provider.ListProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.Nested;
import org.gradle.jvm.toolchain.JavaToolchainSpec;


public interface NativeImageExtension {
    Property<FileCollection> getClasspath();

    @Nested
    JavaToolchainSpec getToolchain();

    JavaToolchainSpec toolchain(Action<? super JavaToolchainSpec> action);

    ListProperty<String> getJvmArgs();
    Property<Boolean> getUseMusl();
    Property<Boolean> getBuildStaticImage();
    Property<Boolean> getEnableFallback();
    Property<Boolean> getLinkAtBuildTime();

    Property<String> getMainClass();

    Property<String> getMainModule();

    Property<Boolean> getCompressExecutable();

    Property<Boolean> getUseLZMA();

    Property<Boolean> getVerbose();

    Property<Integer> getCompressionLevel();
}
