package net.woggioni.gradle.graalvm;

import org.gradle.api.Project;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.plugins.BasePluginExtension;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.CacheableTask;
import org.gradle.api.tasks.Exec;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.InputFile;
import org.gradle.api.tasks.OutputFile;
import org.gradle.api.tasks.PathSensitive;
import org.gradle.api.tasks.PathSensitivity;
import org.gradle.process.CommandLineArgumentProvider;

import javax.inject.Inject;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@CacheableTask
public abstract class UpxTask extends Exec {

    @InputFile
    @PathSensitive(PathSensitivity.NONE)
    public abstract RegularFileProperty getInputFile();

    @OutputFile
    public abstract RegularFileProperty getOutputFile();

    @Input
    public abstract Property<Boolean> getUseLZMA();

    @Input
    public abstract Property<Integer> getCompressionLevel();

    @Inject
    public UpxTask(Project project) {
        BasePluginExtension be = project.getExtensions().findByType(BasePluginExtension.class);
        getOutputFile().convention(
            be.getDistsDirectory().file(String.format("%s.upx", project.getName()))
        );
        getUseLZMA().convention(false);
        getCompressionLevel().convention(10);

        executable("upx");

        getArgumentProviders().add(new CommandLineArgumentProvider() {
            @Override
            public Iterable<String> asArguments() {
                List<String> result = new ArrayList<String>();
                if(getUseLZMA().get()) {
                    result.add("--lzma");
                } else {
                    result.add("--no-lzma");
                }
                String compressionLevel;
                int cl = getCompressionLevel().get();
                switch (cl) {
                    case 1:
                        compressionLevel = "-1";
                        break;
                    case 2:
                        compressionLevel = "-2";
                        break;
                    case 3:
                        compressionLevel = "-3";
                        break;
                    case 4:
                        compressionLevel = "-4";
                        break;
                    case 5:
                        compressionLevel = "-5";
                        break;
                    case 6:
                        compressionLevel = "-6";
                        break;
                    case 7:
                        compressionLevel = "-7";
                        break;
                    case 8:
                        compressionLevel = "-8";
                        break;
                    case 9:
                        compressionLevel = "-9";
                        break;
                    case 10:
                        compressionLevel = "--best";
                        break;
                    case 11:
                        compressionLevel = "--brute";
                        break;
                    case 12:
                        compressionLevel = "--ultra-brute";
                        break;
                    default:
                        throw new IllegalArgumentException(String.format("Unsupported compression level %d", cl));
                }
                result.add(compressionLevel);
                result.add(getInputFile().getAsFile().get().toString());
                result.add("-f");
                result.add("-o");
                result.add(getOutputFile().getAsFile().get().toString());
                return Collections.unmodifiableList(result);
            }
        });
    }
}
