package net.woggioni.gradle.multi.release.jar;

import org.gradle.api.model.ObjectFactory;
import org.gradle.api.provider.ListProperty;
import org.gradle.api.provider.Provider;

import javax.inject.Inject;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

public class MultiReleaseJarPluginExtension {

    private final ObjectFactory objects;
    private final Map<String, ListProperty<String>> patchModules;

    @Inject
    public MultiReleaseJarPluginExtension(ObjectFactory objects) {
        this.objects = objects;
        patchModules = new HashMap<>();
    }

    public void patchModule(String moduleName, Provider<String> path) {
        this.patchModules
            .computeIfAbsent(moduleName, key -> objects.listProperty(String.class)
            .convention(new ArrayList<>()))
            .add(path);
    }

    public Map<String, ListProperty<String>> getPatchModules() {
        return patchModules;
    }
}
