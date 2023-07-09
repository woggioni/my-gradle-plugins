package net.woggioni.gradle.multi.release.jar;

import org.gradle.api.Project;
import org.gradle.api.model.ObjectFactory;
import org.gradle.api.provider.MapProperty;
import org.gradle.api.provider.Provider;

import javax.inject.Inject;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;

public class MultiReleaseJarPluginExtension {
    private final Provider<List<String>> listCtor;
    private final MapProperty<String, List<String>> patchModules;
    @Inject
    public MultiReleaseJarPluginExtension(Project project, ObjectFactory objects) {
        patchModules = objects.mapProperty(String.class,
                (Class<List<String>>) ((List<String>) new ArrayList<String>()).getClass());
        listCtor = project.provider(ArrayList::new);
    }

    private static <T> List<T> listAdd(List<T> l, T el) {
        l.add(el);
        return l;
    }

    public void patchModule(String moduleName, Provider<String> path) {
        Provider<List<String>> listProvider = this.patchModules.getting(moduleName);
        if(listProvider.isPresent()) {
            patchModules.put(moduleName, listProvider.zip(path, MultiReleaseJarPluginExtension::listAdd));
        } else {
            patchModules.put(moduleName, listCtor.zip(path, MultiReleaseJarPluginExtension::listAdd));
        }
    }

    public MapProperty<String, List<String>> getPatchModules() {
        return patchModules;
    }
}
