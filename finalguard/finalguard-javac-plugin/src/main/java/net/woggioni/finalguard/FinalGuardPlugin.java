package net.woggioni.finalguard;

import com.sun.source.tree.*;
import com.sun.source.util.*;

import javax.lang.model.element.Element;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.Modifier;
import javax.tools.Diagnostic;
import java.util.*;
import java.util.stream.Collectors;

public class FinalGuardPlugin implements Plugin {
    public static final String DEFAULT_LEVEL_KEY = "default.level";
    public static final String EXCLUDE_KEY = "exclude";
    public static final String IGNORE_ABSTRACT_METHOD_PARAMS_KEY = "net.woggioni.finalguard.ignore.abstract.method.params";
    enum VariableType {
        LOCAL_VAR("local.variable.level"),
        METHOD_PARAM("method.param.level"),
        LOOP_PARAM("for.param.level"),
        TRY_WITH_PARAM("try.param.level"),
        CATCH_PARAM("catch.param.level"),
        LAMBDA_PARAM("lambda.param.level"),
        ABSTRACT_METHOD_PARAM("abstract.method.param.level");

        private final String argKey;

        VariableType(final String argKey) {
                this.argKey = argKey;
        }

        public String getArgKey() {
            return argKey;
        }

        public String getMessage(final String variableName) {
            switch (this) {
                case LOCAL_VAR:
                    return "Local variable '" + variableName + "' is never reassigned, so it should be declared final";
                case METHOD_PARAM:
                    return "Method parameter '" + variableName + "' is never reassigned, so it should be declared final";
                case LOOP_PARAM:
                    return "Loop parameter '" + variableName + "' is never reassigned, so it should be declared final";
                case TRY_WITH_PARAM:
                    return "Try-with-resources parameter '" + variableName + "' is never reassigned, so it should be declared final";
                case CATCH_PARAM:
                    return "Catch parameter '" + variableName + "' is never reassigned, so it should be declared final";
                case LAMBDA_PARAM:
                    return "Lambda parameter '" + variableName + "' is never reassigned, so it should be declared final";
                case ABSTRACT_METHOD_PARAM:
                    return "Abstract method parameter '" + variableName + "' is never reassigned, so it should be declared final";
                default:
                    throw new UnsupportedOperationException();
            }
        }
    }

    private static class VariableInfo {
        final VariableTree variableTree;
        final VariableType variableType;
        VariableInfo(VariableTree variableTree, VariableType variableType) {
            this.variableTree = variableTree;
            this.variableType = variableType;
        }
    }

    private static final class Configuration {
        private final Map<VariableType, Diagnostic.Kind> levels;
        private final List<String> excludedPaths;

        public Configuration(final String... args) {
                final Map<String, String> props = new HashMap<>();
                final List<String> excluded = new ArrayList<>();
                for (final String arg : args) {
                    final String[] parts = arg.split("=", 2);
                    if (parts.length == 2) {
                        if (EXCLUDE_KEY.equals(parts[0])) {
                            excluded.add(parts[1]);
                        } else {
                            props.put(parts[0], parts[1]);
                        }
                    }
                }
                this.excludedPaths = Collections.unmodifiableList(excluded);
            final Diagnostic.Kind defaultLevel =
                    Optional.ofNullable(props.get(DEFAULT_LEVEL_KEY)).map(Diagnostic.Kind::valueOf).orElse(null);
            this.levels = Arrays.stream(VariableType.values()).map(vt -> {
                final Diagnostic.Kind level = Optional.ofNullable(props.get(vt.getArgKey())).map(Diagnostic.Kind::valueOf).orElse(defaultLevel);
                if (level != null) {
                    return new AbstractMap.SimpleEntry<>(vt, level);
                } else {
                    return null;
                }
            }).filter(Objects::nonNull).collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
        }

        public boolean isExcluded(final String sourcePath) {
            for (final String excludedPath : excludedPaths) {
                if (sourcePath.startsWith(excludedPath)) {
                    return true;
                }
            }
            return false;
        }
    }

    private static boolean isJava17OrHigher() {
        return System.getProperty("java.version").compareTo("17") >= 0;
    }

    private static final boolean isJava17OrHigher = isJava17OrHigher();

    @Override
    public String getName() {
        return getClass().getName();
    }

    @Override
    public void init(JavacTask task, String... args) {
        final Configuration configuration = new Configuration(args);
        task.addTaskListener(new TaskListener() {
            @Override
            public void started(TaskEvent e) {
            }

            @Override
            public void finished(TaskEvent e) {
                if (e.getKind() == TaskEvent.Kind.ANALYZE) {
                    analyzeFinalVariables(e.getCompilationUnit(), task, e.getTypeElement(), configuration);
                }
            }
        });
    }

    private void analyzeFinalVariables(CompilationUnitTree compilationUnit, JavacTask task, Element typeElement, Configuration configuration) {
        final String sourcePath = compilationUnit.getSourceFile().toUri().getPath();
        if (sourcePath != null && configuration.isExcluded(sourcePath)) {
            return;
        }
        FinalVariableAnalyzer analyzer = new FinalVariableAnalyzer(compilationUnit, task, configuration);
        TreePath path = Trees.instance(task).getPath(typeElement);
        if (path != null) {
            analyzer.scan(path, null);
        }
    }

    private static class FinalVariableAnalyzer extends TreePathScanner<Void, Void> {
        private final Configuration configuration;
        private final CompilationUnitTree compilationUnit;
        private final Trees trees;
        private final Map<String, VariableInfo> variableInfoMap = new LinkedHashMap<>();
        private final Set<String> reassignedVariables = new HashSet<>();

        public FinalVariableAnalyzer(CompilationUnitTree compilationUnit, JavacTask task, Configuration configuration) {
            this.configuration = configuration;
            this.compilationUnit = compilationUnit;
            this.trees = Trees.instance(task);
        }

        @Override
        public Void visitMethod(MethodTree node, Void p) {
            variableInfoMap.clear();
            reassignedVariables.clear();
            super.visitMethod(node, p);
            // Check for variables that could be final
            checkForFinalCandidates();
            return null;
        }

        @Override
        public Void visitVariable(VariableTree node, Void p) {
            final String varName = node.getName().toString();
            final TreePath currentPath = getCurrentPath();
            final TreePath parentPath = currentPath.getParentPath();
            final Tree parent = parentPath.getLeaf();
            final VariableType type;

            if (parent instanceof LambdaExpressionTree) {
                type = VariableType.LAMBDA_PARAM;
            } else if (parent instanceof ForLoopTree || parent instanceof EnhancedForLoopTree) {
                type = VariableType.LOOP_PARAM;
            } else if (parent instanceof CatchTree) {
                type = VariableType.CATCH_PARAM;
            } else if (parent instanceof TryTree) {
                type = VariableType.TRY_WITH_PARAM;
            } else if (parent instanceof MethodTree) {
                if (isAbstractMethodParameter(node, (MethodTree) parent)) {
                    type = VariableType.ABSTRACT_METHOD_PARAM;
                } else {
                    type = VariableType.METHOD_PARAM;
                    if (isJava17OrHigher && ((MethodTree) parent).getName().contentEquals("<init>")) {
                        final TreePath grandParentPath = parentPath.getParentPath();
                        if (grandParentPath.getLeaf().getKind() == Tree.Kind.RECORD) {
                            return super.visitVariable(node, p);
                        }
                    }
                }
            } else if (parent instanceof BlockTree) {
                type = VariableType.LOCAL_VAR;
            } else {
                type = VariableType.LOCAL_VAR;
            }

            variableInfoMap.put(varName, new VariableInfo(node, type));
            return super.visitVariable(node, p);
        }

        private boolean isAbstractMethodParameter(VariableTree variableTree, MethodTree methodTree) {
            // Get the element for the method
            Element methodElement = trees.getElement(getCurrentPath().getParentPath());
            if (methodElement instanceof ExecutableElement) {
                ExecutableElement executableElement = (ExecutableElement) methodElement;
                return executableElement.getModifiers().contains(Modifier.ABSTRACT);
            }
            return false;
        }

        @Override
        public Void visitAssignment(AssignmentTree node, Void p) {
            if (node.getVariable() instanceof IdentifierTree) {
                final IdentifierTree ident = (IdentifierTree) node.getVariable();
                reassignedVariables.add(ident.getName().toString());
            }
            return super.visitAssignment(node, p);
        }

        @Override
        public Void visitUnary(UnaryTree node, Void p) {
            if ((node.getKind() == Tree.Kind.PREFIX_INCREMENT ||
                    node.getKind() == Tree.Kind.PREFIX_DECREMENT ||
                    node.getKind() == Tree.Kind.POSTFIX_INCREMENT ||
                    node.getKind() == Tree.Kind.POSTFIX_DECREMENT) &&
                    node.getExpression() instanceof IdentifierTree) {
                IdentifierTree ident = (IdentifierTree) node.getExpression();
                reassignedVariables.add(ident.getName().toString());
            }
            return super.visitUnary(node, p);
        }

        @Override
        public Void visitCompoundAssignment(CompoundAssignmentTree node, Void p) {
            if (node.getVariable() instanceof IdentifierTree) {
                final IdentifierTree ident = (IdentifierTree) node.getVariable();
                reassignedVariables.add(ident.getName().toString());
            }
            return super.visitCompoundAssignment(node, p);
        }

        private void checkForFinalCandidates() {
            for (final Map.Entry<String, VariableInfo> entry : variableInfoMap.entrySet()) {
                final String varName = entry.getKey();
                final VariableInfo info = entry.getValue();
                Diagnostic.Kind level = configuration.levels.get(info.variableType);
                // Skip if level is not configured
                if (level == null) {
                    continue;
                }
                // Skip if already final
                if (isFinal(info.variableTree)) {
                    continue;
                }
                // Skip if reassigned
                if (reassignedVariables.contains(varName)) {
                    continue;
                }
                trees.printMessage(level,
                        info.variableType.getMessage(varName),
                        info.variableTree,
                        compilationUnit);
            }
        }

        private static boolean isFinal(VariableTree variableTree) {
            final Set<Modifier> modifiers = variableTree.getModifiers().getFlags();
            return modifiers.contains(Modifier.FINAL);
        }
    }
}