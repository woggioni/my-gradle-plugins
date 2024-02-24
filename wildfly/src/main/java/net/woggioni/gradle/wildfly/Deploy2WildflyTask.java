package net.woggioni.gradle.wildfly;

import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.provider.Provider;
import org.gradle.api.tasks.Exec;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.InputFile;

import javax.inject.Inject;
import java.util.Arrays;

public abstract class Deploy2WildflyTask extends Exec {
    private static final String PROPERTY_PREFIX = "net.woggioni.gradle.wildfly.";
    private static final String HOST_PROPERTY_KEY = PROPERTY_PREFIX + "rpcHost";
    private static final String PORT_PROPERTY_KEY = PROPERTY_PREFIX + "rpcPort";
    private static final String USER_NAME_PROPERTY_KEY = PROPERTY_PREFIX + "rpcUsername";
    private static final String PASSWORD_PROPERTY_KEY = PROPERTY_PREFIX + "rpcPassword";

    @Input
    public abstract Property<String> getRpcHost();

    @Input
    public abstract Property<Integer> getRpcPort();

    @Input
    public abstract Property<String> getRpcUsername();

    @Input
    public abstract Property<String> getRpcPassword();
    @Input
    public abstract Property<String> getDeploymentName();

    @InputFile
    public abstract RegularFileProperty getArtifact();

    private String projectProperty(String key, String defaultValue) {
        String result = (String) getProject().findProperty(key);
        return result == null ? defaultValue : result;
    }

    @Inject
    public Deploy2WildflyTask() {
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
        getRpcHost().convention(defaultHostProvider);
        getRpcPort().convention(defaultPortProvider);
        getRpcUsername().convention(defaultUsernameProvider);
        getRpcPassword().convention(defaultPasswordProvider);
        getDeploymentName().convention(getArtifact().map(it -> it.getAsFile().getName()));
        getArgumentProviders().add(() ->
            Arrays.asList(
                    "--controller=" + getRpcHost().get() + ":" + getRpcPort().get(),
                    "--connect",
                    "--user=" + getRpcUsername().get(),
                    "--password=" + getRpcPassword().get(),
                    "--command=deploy "
                            + getArtifact().getAsFile().get().getPath()
                            + " --name=" + getDeploymentName().get()
                            + " --force")
        );
    }
}
