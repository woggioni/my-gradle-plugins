package net.woggioni.finalguard;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.ArgumentsProvider;
import org.junit.jupiter.params.provider.ArgumentsSource;

import javax.tools.Diagnostic;
import javax.tools.FileObject;
import javax.tools.ForwardingJavaFileManager;
import javax.tools.JavaCompiler;
import javax.tools.JavaFileObject;
import javax.tools.SimpleJavaFileObject;
import javax.tools.StandardJavaFileManager;
import javax.tools.ToolProvider;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.Reader;
import java.io.StringWriter;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

import static net.woggioni.finalguard.FinalGuardPlugin.VariableType.ABSTRACT_METHOD_PARAM;
import static net.woggioni.finalguard.FinalGuardPlugin.VariableType.CATCH_PARAM;
import static net.woggioni.finalguard.FinalGuardPlugin.VariableType.LAMBDA_PARAM;
import static net.woggioni.finalguard.FinalGuardPlugin.VariableType.LOCAL_VAR;
import static net.woggioni.finalguard.FinalGuardPlugin.VariableType.LOOP_PARAM;
import static net.woggioni.finalguard.FinalGuardPlugin.VariableType.METHOD_PARAM;
import static net.woggioni.finalguard.FinalGuardPlugin.VariableType.TRY_WITH_PARAM;

public class PluginTest {

    private static class ClassFile extends SimpleJavaFileObject {

        private ByteArrayOutputStream out;

        public ClassFile(URI uri) {
            super(uri, Kind.CLASS);
        }

        @Override
        public OutputStream openOutputStream() {
            return out = new ByteArrayOutputStream();
        }

        public byte[] getCompiledBinaries() {
            return out.toByteArray();
        }
    }

    private static class SourceFile extends SimpleJavaFileObject {
        public SourceFile(URI uri) {
            super(uri, Kind.SOURCE);
        }

        @Override
        public CharSequence getCharContent(boolean ignoreEncodingErrors) throws IOException {
            Reader r = new InputStreamReader(uri.toURL().openStream());
            StringBuilder sb = new StringBuilder();
            char[] buffer = new char[0x1000];
            while (true) {
                int read = r.read(buffer);
                if (read < 0) break;
                sb.append(buffer, 0, read);
            }
            return sb.toString();
        }
    }

    private static class FileManager extends ForwardingJavaFileManager<StandardJavaFileManager> {

        private final List<ClassFile> compiled = new ArrayList<>();

        protected FileManager(StandardJavaFileManager fileManager) {
            super(fileManager);
        }

        @Override
        public JavaFileObject getJavaFileForOutput(Location location,
                                                   String className,
                                                   JavaFileObject.Kind kind,
                                                   FileObject sibling) {
            ClassFile result = new ClassFile(URI.create("string://" + className));
            compiled.add(result);
            return result;
        }

        public List<ClassFile> getCompiled() {
            return compiled;
        }
    }

    private Optional<Iterable<Diagnostic<? extends JavaFileObject>>> compile(Iterable<URI> sources) {
        StringWriter output = new StringWriter();
        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        FileManager fileManager =
                new FileManager(compiler.getStandardFileManager(null, null, null));
        List<JavaFileObject> compilationUnits = StreamSupport.stream(sources.spliterator(), false)
                .map(SourceFile::new).collect(Collectors.toList());
        List<String> arguments = Arrays.asList(
                "-classpath", System.getProperty("test.compilation.classpath"),
                "-Xplugin:" + FinalGuardPlugin.class.getName()
        );
        final ArrayList<Diagnostic<? extends JavaFileObject>> compilerMessages = new ArrayList<>();
        System.setProperty(FinalGuardPlugin.DIAGNOSTIC_LEVEL_KEY, "ERROR");
        JavaCompiler.CompilationTask task = compiler.getTask(
                output,
                fileManager,
                compilerMessages::add,
                arguments,
                null,
                compilationUnits
        );
        if (task.call()) return Optional.empty();
        else return Optional.of(compilerMessages);
    }

    private enum CompilationResult {
        SUCCESS, FAILURE
    }

    private static class TestCaseProvider implements ArgumentsProvider {
        @Override
        public Stream<? extends Arguments> provideArguments(ExtensionContext context) {
            String prefix = "net/woggioni/finalguard/test/";
            return Stream.of(
                    Arguments.of(prefix + "TestCase1.java", Arrays.asList(
                            LOOP_PARAM.getMessage("item"),
                            LAMBDA_PARAM.getMessage("s"),
                            LOCAL_VAR.getMessage("f"),
                            LOCAL_VAR.getMessage("localVar"),
                            LOCAL_VAR.getMessage("loopVar"),
                            LOOP_PARAM.getMessage("i"),
                            TRY_WITH_PARAM.getMessage("is"),
                            METHOD_PARAM.getMessage("param1"),
                            CATCH_PARAM.getMessage("ioe"),
                            LOCAL_VAR.getMessage("anotherVar"),
                            METHOD_PARAM.getMessage("param2")
                    )),
                    Arguments.of(prefix + "TestCase2.java", Collections.emptyList()),
                    Arguments.of(prefix + "TestCase3.java",
                            Arrays.asList(LOCAL_VAR.getMessage("n"))),
                    Arguments.of(prefix + "TestCase4.java",
                            Arrays.asList(LOOP_PARAM.getMessage("i"))),
                    Arguments.of(prefix + "TestCase5.java", Arrays.asList(LOCAL_VAR.getMessage("loopVar"))),
                    Arguments.of(prefix + "TestCase6.java", Arrays.asList(LOOP_PARAM.getMessage("item"))),
                    Arguments.of(prefix + "TestCase7.java", Arrays.asList(CATCH_PARAM.getMessage("re"))),
                    Arguments.of(prefix + "TestCase8.java", Arrays.asList(TRY_WITH_PARAM.getMessage("is"))),
                    Arguments.of(prefix + "TestCase9.java", Arrays.asList(LAMBDA_PARAM.getMessage("s"))),
                    Arguments.of(prefix + "TestCase10.java", Arrays.asList(METHOD_PARAM.getMessage("n"))),
                    Arguments.of(prefix + "TestCase11.java", Arrays.asList(
                            ABSTRACT_METHOD_PARAM.getMessage("n"),
                            LOCAL_VAR.getMessage("result"),
                            LOCAL_VAR.getMessage("size"),
                            METHOD_PARAM.getMessage("t1s")
                    )),
                    Arguments.of(prefix + "TestCase12.java", Collections.emptyList()),
                    Arguments.of(prefix + "TestCase13.java", Arrays.asList(ABSTRACT_METHOD_PARAM.getMessage("x"), ABSTRACT_METHOD_PARAM.getMessage("y"))),
                    Arguments.of(prefix + "TestCase14.java", Arrays.asList(ABSTRACT_METHOD_PARAM.getMessage("x"), ABSTRACT_METHOD_PARAM.getMessage("y")))
            );
        }
    }

    @ParameterizedTest(name = "{0}")
    @ArgumentsSource(TestCaseProvider.class)
    public void test(String sourceFilePath, List<String> expectedErrorMessages) {
        Optional<Iterable<Diagnostic<? extends JavaFileObject>>> result;
        try {
            ClassLoader cl = getClass().getClassLoader();
            result = compile(Collections.singletonList(cl.getResource(sourceFilePath).toURI()));
        } catch (URISyntaxException e) {
            throw new RuntimeException(e);
        }
        result.ifPresent(diagnostics -> {
            for (Diagnostic<? extends JavaFileObject> diagnostic : diagnostics) {
                System.err.printf("%s:%s %s\n",
                        diagnostic.getSource().getName(),
                        diagnostic.getLineNumber(),
                        diagnostic.getMessage(Locale.getDefault()));
            }
        });
        if (expectedErrorMessages.isEmpty()) {
            Assertions.assertFalse(result.isPresent(), "Compilation was expected to succeed but it has failed");
        } else {
            final List<String> compilationErrors = result
                    .map(it -> StreamSupport.stream(it.spliterator(), false))
                    .orElse(Stream.empty())
                    .map(it -> it.getMessage(Locale.ENGLISH))
                    .collect(Collectors.toList());
            for (String expectedErrorMessage : expectedErrorMessages) {
                int index = compilationErrors.indexOf(expectedErrorMessage);
                Assertions.assertTrue(index >= 0, String.format("Expected compilation error `%s` not found in output", expectedErrorMessage));
                compilationErrors.remove(index);
            }
            Assertions.assertTrue(compilationErrors.isEmpty(), "Unexpected compilation errors found in the output");
        }
    }
}
