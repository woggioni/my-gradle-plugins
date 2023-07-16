package net.woggioni.gradle.lombok;

import lombok.RequiredArgsConstructor;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.file.FileCollection;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.file.SourceDirectorySet;
import org.gradle.api.provider.Property;
import org.gradle.api.provider.Provider;
import org.gradle.api.tasks.InputFiles;
import org.gradle.api.tasks.Internal;
import org.gradle.api.tasks.JavaExec;
import org.gradle.api.tasks.OutputDirectory;
import org.gradle.api.tasks.SourceSet;
import org.gradle.internal.jvm.JavaModuleDetector;

import javax.inject.Inject;
import java.io.File;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

@RequiredArgsConstructor(onConstructor_ = {@Inject})
public abstract class Delombok extends JavaExec {

    private final JavaModuleDetector javaModuleDetector;
    private static String buildClasspathString(Iterable<File> classpath) {
        StringBuilder sb = new StringBuilder();
        Iterator<File> it = classpath.iterator();
        String separator = System.getProperty("path.separator");
        if(it.hasNext()) {
            sb.append(it.next().getPath());
        }
        while(it.hasNext()) {
            sb.append(separator);
            sb.append(it.next().getPath());
        }
        for(File file : classpath) {
            sb.append(file.getPath());
        }
        return sb.toString();
    }

    @InputFiles
    public Provider<FileCollection> getSourceClasspath() {
        return getSourceSet()
                .map(SourceSet::getCompileClasspath);
    }

    @Internal
    abstract public Property<SourceSet> getSourceSet();
    @Internal
    abstract public Property<Configuration> getLombokJar();
    @Internal
    abstract public Property<Boolean> getInferModulePath();
    @OutputDirectory
    abstract public RegularFileProperty getOutputDir();
    @InputFiles
    public Provider<FileCollection> getInputFiles() {
        return getSourceSet()
            .map(SourceSet::getAllSource)
            .map(SourceDirectorySet::getSourceDirectories)
            .zip(getLombokJar(), FileCollection::plus);
    }


    @Override
    public void exec() {
        classpath(getLombokJar());
        List<String> args = new ArrayList<>();
        args.add("delombok");
        args.add("-d");
        args.add(getOutputDir().getAsFile().get().getPath());
        SourceSet sourceSet = getSourceSet().get();
        Boolean inferModulePath = getInferModulePath().get();
        FileCollection classpath = javaModuleDetector.inferClasspath(inferModulePath, sourceSet.getCompileClasspath());
        if(!classpath.isEmpty()) {
            args.add("-c");
            args.add(classpath.getAsPath());
        }
        if(inferModulePath) {
            FileCollection modulepath = javaModuleDetector.inferModulePath(true, sourceSet.getCompileClasspath());
            if(!modulepath.isEmpty()) {
                args.add("--module-path");
                args.add(modulepath.getAsPath());
            }
        }
        for(File sourceDir : sourceSet.getJava().getSrcDirs()) {
            args.add(sourceDir.getPath());
        }
        Object[] argsArray = new String[args.size()];
        args.toArray(argsArray);
        args(argsArray);
        super.exec();
    }
}
