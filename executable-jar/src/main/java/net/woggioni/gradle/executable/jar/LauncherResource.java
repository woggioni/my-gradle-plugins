package net.woggioni.gradle.executable.jar;

import java.io.InputStream;
import java.net.URI;
import java.net.URL;
import javax.annotation.Nonnull;
import lombok.SneakyThrows;
import org.gradle.api.resources.ReadableResource;
import org.gradle.api.resources.ResourceException;

final class LauncherResource implements ReadableResource {
    static final ReadableResource instance = new LauncherResource();

    private final URL url;

    private LauncherResource() {
        url = getClass().getResource(String.format("/META-INF/%s", getDisplayName()));
    }

    @Override
    @Nonnull
    @SneakyThrows
    public InputStream read() throws ResourceException {
        return url.openStream();
    }

    @Override
    public String getDisplayName() {
        return getBaseName() + ".tar";
    }

    @Override
    @SneakyThrows
    public URI getURI() {
        return url.toURI();
    }

    @Override
    public String getBaseName() {
        return "executable-jar-launcher";
    }
}