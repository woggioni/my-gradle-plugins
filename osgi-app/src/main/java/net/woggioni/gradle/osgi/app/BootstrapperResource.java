package net.woggioni.gradle.osgi.app;

import lombok.SneakyThrows;
import org.gradle.api.resources.ReadableResource;
import org.gradle.api.resources.ResourceException;

import javax.annotation.Nonnull;
import java.io.InputStream;
import java.net.URI;
import java.net.URL;

abstract class EmbeddedResource implements ReadableResource {
    private final URL url;

    private final String baseName;
    private final String extension;

    protected EmbeddedResource(String baseName, String extension) {
        this.baseName = baseName;
        this.extension = extension;
        url = getClass().getResource(String.format("/META-INF/%s.%s", baseName, extension));
    }

    @Override
    @Nonnull
    @SneakyThrows
    public InputStream read() throws ResourceException {
        return url.openStream();
    }

    @Override
    public String getDisplayName() {
        return getBaseName() + "." + extension;
    }

    @Override
    @SneakyThrows
    public URI getURI() {
        return url.toURI();
    }

    @Override
    public String getBaseName() {
        return baseName;
    }
}

final class BootstrapperResource extends EmbeddedResource {
    static final ReadableResource instance = new BootstrapperResource();

    private BootstrapperResource() {
        super("osgi-simple-bootstrapper", "tar");
    }
}

final class BootstrapperApiResource extends EmbeddedResource {
    static final ReadableResource instance = new BootstrapperApiResource();

    private BootstrapperApiResource() {
        super("osgi-simple-bootstrapper-api", "jar");
    }
}

final class BootstrapperApplicationResource extends EmbeddedResource {
    static final ReadableResource instance = new BootstrapperApplicationResource();

    private BootstrapperApplicationResource() {
        super("osgi-simple-bootstrapper-application", "jar");
    }
}
