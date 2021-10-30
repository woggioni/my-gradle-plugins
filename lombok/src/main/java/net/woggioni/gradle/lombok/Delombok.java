package net.woggioni.gradle.lombok;

import lombok.Getter;
import org.gradle.api.tasks.JavaExec;
import org.gradle.api.tasks.OutputDirectory;

import javax.inject.Inject;
import java.io.File;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

public class Delombok extends JavaExec {

    @Getter
    @OutputDirectory
    private final File outputDir;

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

    @Inject
    public Delombok(File lombokJar, File outputDir, Iterable<File> sourceDirs, String classpath) {
        this.outputDir = outputDir;
        classpath(lombokJar);
        List<String> args = new ArrayList<>();
        args.add("delombok");
        args.add("-d");
        args.add(outputDir.getPath());
        args.add("-c");
        args.add(classpath);
        for(File sourceDir : sourceDirs) args.add(sourceDir.getPath());
        Object[] argsArray = new String[args.size()];
        args.toArray(argsArray);
        args(argsArray);
    }
}
