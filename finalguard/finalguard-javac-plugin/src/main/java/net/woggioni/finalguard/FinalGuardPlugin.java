package net.woggioni.finalguard;

import com.sun.source.tree.AssignmentTree;
import com.sun.source.tree.BlockTree;
import com.sun.source.tree.CatchTree;
import com.sun.source.tree.CompilationUnitTree;
import com.sun.source.tree.CompoundAssignmentTree;
import com.sun.source.tree.EnhancedForLoopTree;
import com.sun.source.tree.ForLoopTree;
import com.sun.source.tree.IdentifierTree;
import com.sun.source.tree.LambdaExpressionTree;
import com.sun.source.tree.MethodTree;
import com.sun.source.tree.Tree;
import com.sun.source.tree.TryTree;
import com.sun.source.tree.UnaryTree;
import com.sun.source.tree.VariableTree;
import com.sun.source.util.JavacTask;
import com.sun.source.util.Plugin;
import com.sun.source.util.TaskEvent;
import com.sun.source.util.TaskListener;
import com.sun.source.util.TreePath;
import com.sun.source.util.TreePathScanner;
import com.sun.source.util.Trees;

import javax.lang.model.element.Element;
import javax.lang.model.element.Modifier;
import javax.tools.Diagnostic;
import java.util.AbstractMap;
import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

public class FinalGuardPlugin implements Plugin {

    public static final String DIAGNOSTIC_LEVEL_KEY = "net.woggioni.finalguard.diagnostic.level";

    enum VariableType {
        LOCAL_VAR("net.woggioni.finalguard.diagnostic.local.variable.level"),
        METHOD_PARAM("net.woggioni.finalguard.diagnostic.method.param.level"),
        LOOP_PARAM("net.woggioni.finalguard.diagnostic.for.param.level"),
        TRY_WITH_PARAM("net.woggioni.finalguard.diagnostic.try.param.level"),
        CATCH_PARAM("net.woggioni.finalguard.diagnostic.catch.param.level"),
        LAMBDA_PARAM("net.woggioni.finalguard.diagnostic.lambda.param.level");

        private final String propertyKey;

        VariableType(final String propertyKey) {
            this.propertyKey = propertyKey;
        }

        public String getPropertyKey() {
            return propertyKey;
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

        public Configuration() {
            final Diagnostic.Kind defaultLevel =
                    Optional.ofNullable(System.getProperty(DIAGNOSTIC_LEVEL_KEY)).map(Diagnostic.Kind::valueOf).orElse(null);
            this.levels = Arrays.stream(VariableType.values()).map(vt -> {
                final Diagnostic.Kind level = Optional.ofNullable(System.getProperty(vt.getPropertyKey())).map(Diagnostic.Kind::valueOf).orElse(defaultLevel);
                if (level != null) {
                    return new AbstractMap.SimpleEntry<>(vt, level);
                } else {
                    return null;
                }
            }).filter(Objects::nonNull).collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
        }
    }

    private static boolean isJava17OrHigher() {
        return System.getProperty("java.version").compareTo("17") >= 0;
    }

    private static final Configuration configuration = new Configuration();

    private static final boolean isJava17OrHigher = isJava17OrHigher();

    @Override
    public String getName() {
        return getClass().getName();
    }

    @Override
    public void init(JavacTask task, String... args) {
        task.addTaskListener(new TaskListener() {
            @Override
            public void started(TaskEvent e) {
            }

            @Override
            public void finished(TaskEvent e) {
                if (e.getKind() == TaskEvent.Kind.ANALYZE) {
                    analyzeFinalVariables(e.getCompilationUnit(), task, e.getTypeElement());
                }
            }
        });
    }

    private void analyzeFinalVariables(CompilationUnitTree compilationUnit, JavacTask task, Element typeElement) {
        FinalVariableAnalyzer analyzer = new FinalVariableAnalyzer(compilationUnit, task);
        TreePath path = Trees.instance(task).getPath(typeElement);
        if (path != null) {
            analyzer.scan(path, null);
        }
    }

    private static class FinalVariableAnalyzer extends TreePathScanner<Void, Void> {
        private final CompilationUnitTree compilationUnit;
        private final Trees trees;
        private final Map<String, VariableInfo> variableInfoMap = new LinkedHashMap<>();
        private final Set<String> reassignedVariables = new HashSet<>();

        public FinalVariableAnalyzer(CompilationUnitTree compilationUnit, JavacTask task) {
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
                type = VariableType.METHOD_PARAM;
                if(isJava17OrHigher && ((MethodTree) parent).getName().contentEquals("<init>")) {
                    final TreePath grandParentPath = parentPath.getParentPath();
                    if(grandParentPath.getLeaf().getKind() == Tree.Kind.RECORD) {
                        return super.visitVariable(node, p);
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

                final IdentifierTree ident = (IdentifierTree) node.getExpression();
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