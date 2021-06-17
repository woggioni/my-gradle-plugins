package net.woggioni.gradle.lombok;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.gradle.api.provider.Property;

import javax.inject.Inject;

@RequiredArgsConstructor(onConstructor_ = { @Inject })
public class LombokExtension {
    @Getter
    private final Property<String> version;
}
