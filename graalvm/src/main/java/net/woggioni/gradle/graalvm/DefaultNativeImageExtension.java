package net.woggioni.gradle.graalvm;

import org.gradle.api.Action;
import org.gradle.api.model.ObjectFactory;
import org.gradle.jvm.toolchain.JavaToolchainSpec;

import javax.inject.Inject;


abstract class DefaultNativeImageExtension implements NativeImageExtension {

    @Inject
    public DefaultNativeImageExtension(ObjectFactory objects) {
    }

    @Override
    public JavaToolchainSpec toolchain(Action<? super JavaToolchainSpec> action) {
        JavaToolchainSpec jts = getToolchain();
        action.execute(getToolchain());
        return jts;
    }
}
