package net.woggioni.gradle.sambal;

import lombok.Getter;
import lombok.Setter;
import lombok.SneakyThrows;
import org.codehaus.groovy.runtime.MethodClosure;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.Status;
import org.eclipse.jgit.lib.Ref;
import org.gradle.api.Action;
import org.gradle.api.GradleException;
import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.attributes.Attribute;
import org.gradle.api.attributes.AttributeContainer;
import org.gradle.api.java.archives.Attributes;
import org.gradle.api.plugins.AppliedPlugin;
import org.gradle.api.plugins.ExtraPropertiesExtension;
import org.gradle.api.plugins.JavaPlugin;
import org.gradle.api.plugins.PluginManager;
import org.gradle.api.provider.Provider;
import org.gradle.api.provider.ValueSource;
import org.gradle.api.provider.ValueSourceParameters;
import org.gradle.api.tasks.TaskContainer;
import org.gradle.api.tasks.bundling.Jar;

import javax.annotation.Nonnull;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.io.Serializable;
import java.io.StringWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class SambalPlugin implements Plugin<Project> {
    private static Pattern tagPattern = Pattern.compile("^refs/tags/v?(\\d+\\.\\d+.*)");
    final private static char[] hexArray = "0123456789ABCDEF".toCharArray();

    private static String bytesToHex(byte[] bytes) {
        char[] hexChars = new char[bytes.length * 2];
        for (int j = 0; j < bytes.length; j++) {
            int v = bytes[j] & 0xFF;
            hexChars[j * 2] = hexArray[v >>> 4];
            hexChars[j * 2 + 1] = hexArray[v & 0x0F];
        }
        return new String(hexChars);
    }

    private static void copyConfigurationAttributes(Configuration source, Configuration destination) {
        destination.attributes(new Action<AttributeContainer>() {
            @Override
            public void execute(@Nonnull AttributeContainer destinationAttributes) {
                AttributeContainer sourceAttributes = source.getAttributes();
                for (Attribute attr : sourceAttributes.keySet()) {
                    destinationAttributes.attribute(attr, Objects.requireNonNull(sourceAttributes.getAttribute(attr)));
                }
            }
        });
    }

    @SneakyThrows
    private static String sha256(File f) {
        byte[] buffer = new byte[0x10000];
        MessageDigest md = MessageDigest.getInstance("SHA-256");
        try (InputStream inputStream = new DigestInputStream(new FileInputStream(f), md)) {
            while (true) {
                int read = inputStream.read(buffer);
                if (read < 0) break;
                md.update(buffer, 0, read);
            }
        }
        return bytesToHex(md.digest());
    }

    private static Optional<Path> which(String command) {
        return Stream.of(System.getenv("PATH").split(Pattern.quote(File.pathSeparator)))
                .map(path -> Paths.get(path, command))
                .filter(Files::exists)
                .filter(Files::isExecutable)
                .findFirst();
    }

    private static int getVersionInt(Project project) {
        int result = 0;
        int scale = 100 * 100;
        String versionString = project.getVersion().toString();
        int dash = versionString.indexOf('-');
        if (dash < 0) {
            dash = versionString.length();
        }
        versionString = versionString.substring(0, dash);
        for (String part : versionString.split("\\.")) {
            if (scale >= 1) {
                result += scale * Integer.parseInt(part);
                scale /= 100;
            }
        }
        return result;
    }

    @SneakyThrows
    private static String runCommand(String... args) {
        ProcessBuilder pb = new ProcessBuilder(args);
        pb.redirectError(ProcessBuilder.Redirect.INHERIT);
        pb.redirectOutput(ProcessBuilder.Redirect.PIPE);
        Process p = pb.start();
        p.waitFor();
        if (p.exitValue() != 0) throw new GradleException("Error invoking subprocess");
        StringWriter sw = new StringWriter();
        char[] buffer = new char[0x1024];
        try (Reader reader = new InputStreamReader(p.getInputStream())) {
            while (true) {
                int read = reader.read(buffer);
                if (read <= 0) break;
                sw.write(buffer, 0, read);
            }
        }
        return sw.toString();
    }

    @SneakyThrows
    private static List<String> getCurrentTag(Git git) {
        List<Ref> tags = git.tagList().call();
        Ref currentRef = git.getRepository().findRef("HEAD");
        List<String> currentTag = tags.stream()
                .filter(it -> Objects.equals(it.getObjectId(), currentRef.getObjectId()))
                .map(it -> tagPattern.matcher(it.getName())).filter(Matcher::matches)
                .map(it -> it.group(1))
                .collect(Collectors.toList());
        if (currentTag.isEmpty()) return null;
        else {
            return currentTag;
        }
    }

    private static String resolveProperty(Project project, String key, String defaultValue) {
        if (project.hasProperty(key)) return project.property(key).toString();
        else {
            String envVarKey = key.replace('.', '_').toUpperCase();
            String envVarValue = System.getenv().get(envVarKey);
            if (envVarValue != null) return envVarValue;
            else return defaultValue;
        }
    }

    private static String resolveProperty(Project project, String key) {
        if (project.hasProperty(key)) return project.property(key).toString();
        else {
            String envVarKey = key.replace('.', '_').toUpperCase();
            String envVarValue = System.getenv().get(envVarKey);
            if (envVarValue != null) return envVarValue;
            else {
                String msg = String.format("Impossible to resolve property '%s'," +
                        " either add it to Gradle's project properties from the command line using:\n" +
                        "./gradlew -P%s=someValue\n" +
                        "or set the environmental variable %s", key, key, envVarKey);
                throw new GradleException(msg);
            }
        }
    }

    @Getter
    @Setter
    public static class ProjectParameters implements ValueSourceParameters, Serializable {
        private File rootDirectory;
    }

    public abstract static class GitTagValueSource implements ValueSource<List<String>, ProjectParameters> {
        @Override
        @SneakyThrows
        public List<String> obtain() {
            File rootDirectory = getParameters().getRootDirectory();
            try(Git git = Git.open(rootDirectory)) {
                Status status = git.status().call();
                if (status.isClean()) {
                    return getCurrentTag(git);
                } else {
                    return null;
                }
            }
        }
    }

    public abstract static class GitRevisionValueSource implements ValueSource<String, ProjectParameters> {

        @Override
        @SneakyThrows
        public String obtain() {
            File rootDirectory = getParameters().getRootDirectory();
            try (Git git = Git.open(rootDirectory)) {
                return git.getRepository().findRef("HEAD").getObjectId().name();
            }
        }
    }

    @Override
    public void apply(Project project) {
        ExtraPropertiesExtension ext = project.getRootProject().getExtensions().getExtraProperties();
        ext.set("getIntegerVersion", new MethodClosure(this, "getVersionInt").curry(project));


        final Provider<List<String>> gitTagProvider = project.getProviders().of(GitTagValueSource.class, it -> {
            it.parameters( params -> params.setRootDirectory(project.getRootDir()));
        });
        ext.set("currentTag", gitTagProvider);
        ext.set("resolveProperty", new MethodClosure(this, "resolveProperty").curry(project));
        ext.set("copyConfigurationAttributes", new MethodClosure(this, "copyConfigurationAttributes"));

        final Provider<String> gitRevisionProvider = project.getProviders().of(GitRevisionValueSource.class, it -> {
            it.parameters( params -> params.setRootDirectory(project.getRootDir()));
        });
        ext.set("gitRevision", gitRevisionProvider);

        PluginManager pluginManager = project.getPluginManager();
        pluginManager.withPlugin("java-library", (AppliedPlugin plugin) -> {
            TaskContainer tasks = project.getTasks();
            project.afterEvaluate(p -> {
                tasks.named(JavaPlugin.JAR_TASK_NAME, Jar.class, jarTask -> {
                    jarTask.manifest(mf -> {
                        Attributes attrs = mf.getAttributes();
                        attrs.put(java.util.jar.Attributes.Name.SPECIFICATION_TITLE.toString(), project.getName());
                        attrs.put(java.util.jar.Attributes.Name.SPECIFICATION_VERSION.toString(), project.getVersion());
                        attrs.put(java.util.jar.Attributes.Name.IMPLEMENTATION_VERSION.toString(), gitRevisionProvider);
                    });
                });
            });
        });
    }
}
