package net.woggioni.gradle.lombok;

import org.gradle.api.provider.Property;

abstract public class LombokExtension {
    abstract public Property<String> getVersion();
}
