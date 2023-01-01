package net.woggioni.gradle.wildfly;

import lombok.Getter;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.model.ObjectFactory;
import org.gradle.api.provider.Property;
import org.gradle.api.provider.Provider;
import org.gradle.api.tasks.Exec;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.InputFile;

import javax.annotation.Nonnull;
import javax.inject.Inject;
import java.util.Arrays;

public class Deploy2WildflyTask extends Exec {
    private static final String PROPERTY_PREFIX = "net.woggioni.gradle.wildfly.";
    private static final String HOST_PROPERTY_KEY = PROPERTY_PREFIX + "rpcHost";
    private static final String PORT_PROPERTY_KEY = PROPERTY_PREFIX + "rpcPort";
    private static final String USER_NAME_PROPERTY_KEY = PROPERTY_PREFIX + "rpcUsername";
    private static final String PASSWORD_PROPERTY_KEY = PROPERTY_PREFIX + "rpcPassword";

    @Input
    @Getter
    private final Property<String> rpcHost;

    @Input
    @Getter
    private final Property<Integer> rpcPort;

    @Input
    @Getter
    private final Property<String> rpcUsername;

    @Input
    @Getter
    private final Property<String> rpcPassword;

    @Getter
    @InputFile
    private final RegularFileProperty artifact;

    private String projectProperty(String key, String defaultValue) {
        String result = (String) getProject().findProperty(key);
        return result == null ? defaultValue : result;
    }

    @Inject
    public Deploy2WildflyTask(@Nonnull ObjectFactory objectFactory) {
        setGroup("deploy");
        setDescription("Deploy this project artifact to Wildfly application server");
        Provider<String> defaultHostProvider = getProject()
                .provider(() -> projectProperty(HOST_PROPERTY_KEY, "localhost"));
        Provider<Integer> defaultPortProvider = getProject()
                .provider(() -> Integer.parseInt(projectProperty(PORT_PROPERTY_KEY, "9990")));
        Provider<String> defaultUsernameProvider = getProject()
                .provider(() -> projectProperty(USER_NAME_PROPERTY_KEY, "admin"));
        Provider<String> defaultPasswordProvider = getProject()
                .provider(() -> projectProperty(PASSWORD_PROPERTY_KEY, "password"));

        executable("/opt/wildfly/bin/jboss-cli.sh");
        rpcHost = objectFactory.property(String.class).convention(defaultHostProvider);
        rpcPort = objectFactory.property(Integer.class).convention(defaultPortProvider);
        rpcUsername = objectFactory.property(String.class).convention(defaultUsernameProvider);
        rpcPassword = objectFactory.property(String.class).convention(defaultPasswordProvider);
        artifact = objectFactory.fileProperty();

        getArgumentProviders().add(() ->
            Arrays.asList(
                    "--controller=" + rpcHost.get() + ":" + rpcPort.get(),
                    "--connect",
                    "--user=" + rpcUsername.get(),
                    "--password=" + rpcPassword.get(),
                    "--command=deploy "  + artifact.getAsFile().get().getPath() + " --force")
        );
    }
}
