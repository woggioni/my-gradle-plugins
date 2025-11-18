package net.woggioni.finalguard;

import com.sun.source.tree.AssignmentTree;
import com.sun.source.tree.CompilationUnitTree;
import com.sun.source.tree.CompoundAssignmentTree;
import com.sun.source.tree.IdentifierTree;
import com.sun.source.tree.MethodTree;
import com.sun.source.tree.Tree;
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
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

public class FinalGuardPlugin implements Plugin {

    public static final String DIAGNOSTIC_LEVEL_KEY = "net.woggioni.finalguard.diagnostic.level";

    private static final Diagnostic.Kind diagnosticLevel =
            Optional.ofNullable(System.getProperty(DIAGNOSTIC_LEVEL_KEY))
                    .map(Diagnostic.Kind::valueOf)
                    .orElse(Diagnostic.Kind.WARNING);

    @Override
    public String getName() {
        return getClass().getName();
    }

    @Override
    public void init(JavacTask task, String... args) {
        task.addTaskListener(new TaskListener() {
            @Override
            public void started(TaskEvent e) {}

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
        if(path != null) {
            analyzer.scan(path, null);
        }
    }

    private static class FinalVariableAnalyzer extends TreePathScanner<Void, Void> {
        private final CompilationUnitTree compilationUnit;
        private final Trees trees;
        private final Map<String, VariableInfo> variableInfoMap = new HashMap<>();
        private final Set<String> reassignedVariables = new HashSet<>();
        private String currentMethod;

        public FinalVariableAnalyzer(CompilationUnitTree compilationUnit, JavacTask task) {
            this.compilationUnit = compilationUnit;
            this.trees = Trees.instance(task);
        }

        @Override
        public Void visitMethod(MethodTree node, Void p) {
            String previousMethod = currentMethod;
            currentMethod = node.getName().toString();
            variableInfoMap.clear();
            reassignedVariables.clear();

            // Analyze parameters first
            for (VariableTree param : node.getParameters()) {
                String varName = param.getName().toString();
                variableInfoMap.put(varName, new VariableInfo(param, false));
            }

            // Then analyze method body
            super.visitMethod(node, p);

            // Check for variables that could be final
            checkForFinalCandidates();

            currentMethod = previousMethod;
            return null;
        }

        @Override
        public Void visitVariable(VariableTree node, Void p) {
            if (currentMethod != null) {
                String varName = node.getName().toString();
                boolean isParameter = node.getKind() == Tree.Kind.METHOD;
                variableInfoMap.put(varName, new VariableInfo(node, isParameter));
            }
            return super.visitVariable(node, p);
        }

        @Override
        public Void visitAssignment(AssignmentTree node, Void p) {
            if (node.getVariable() instanceof IdentifierTree) {
                IdentifierTree ident = (IdentifierTree) node.getVariable();
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
                IdentifierTree ident = (IdentifierTree) node.getVariable();
                reassignedVariables.add(ident.getName().toString());
            }
            return super.visitCompoundAssignment(node, p);
        }

        private void checkForFinalCandidates() {
            for (Map.Entry<String, VariableInfo> entry : variableInfoMap.entrySet()) {
                String varName = entry.getKey();
                VariableInfo info = entry.getValue();

                // Skip if already final
                if (isFinal(info.variableTree)) {
                    continue;
                }

                // Skip if reassigned
                if (reassignedVariables.contains(varName)) {
                    continue;
                }

                String message = "Local variable '" + varName + "' is never reassigned, so it should be declared final";
                trees.printMessage(FinalGuardPlugin.diagnosticLevel,
                        message,
                        info.variableTree,
                        compilationUnit);
            }
        }

        private static boolean isFinal(VariableTree variableTree) {
            Set<Modifier> modifiers = variableTree.getModifiers().getFlags();
            return modifiers.contains(Modifier.FINAL);
        }
    }

    private static class VariableInfo {
        final VariableTree variableTree;

        VariableInfo(VariableTree variableTree, boolean isParameter) {
            this.variableTree = variableTree;
        }
    }
}