package net.woggioni.gradle.osgi.app;

import java.util.Collections;
import java.util.Map;
import java.util.function.Supplier;

public class MapBuilder<K, V, M extends Map<K, V>> {

    public static <K, V, M extends Map<K, V>> MapBuilder<K, V, M> getInstance(Supplier<M> mapConstructor) {
        return new MapBuilder<>(mapConstructor);
    }

    private final M map;

    private MapBuilder(Supplier<M> mapConstructor) {
        map = mapConstructor.get();
    }

    public MapBuilder<K, V, M> of(K key, V value) {
        map.put(key, value);
        return this;
    }

    public M build() {
        return map;
    }

    public Map<K, V> buildImmutable() {
        return Collections.unmodifiableMap(map);
    }
}
