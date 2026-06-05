package net.woggioni.finalguard;

import com.sun.source.tree.CompilationUnitTree;
import com.sun.source.tree.MethodTree;
import com.sun.source.tree.VariableTree;
import com.sun.source.util.TreePath;
import com.sun.source.util.Trees;

import javax.lang.model.element.Element;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.TypeElement;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.util.Elements;
import java.util.List;

public class RecordUtils {

    private static class RecordConstructorDetector {

        private final Trees trees;

        public RecordConstructorDetector(Trees trees) {
            this.trees = trees;
        }

        public enum ConstructorKind {
            CANONICAL_FULL,    // Record(int x, String y) { ... }
            CANONICAL_COMPACT, // Record { ... }
            SECONDARY          // Record(String s) { this(0, s); }
        }

        public ConstructorKind detect(TreePath parent, MethodTree method, TypeElement recordType) {
            // 1. Verify it's a constructor
            if (method.getReturnType() != null || !method.getName().contentEquals("<init>")) {
                throw new IllegalArgumentException("Not a constructor: " + method.getName());
            }

            List<? extends VariableTree> params = method.getParameters();
            List<? extends javax.lang.model.element.RecordComponentElement> components =
                    recordType.getRecordComponents();

            // 2. Compact constructor: no explicit parameters
            if (params.isEmpty()) {
                return ConstructorKind.CANONICAL_COMPACT;
            }

            // 3. Check if parameters match record components exactly
            if (params.size() == components.size()) {
                boolean allMatch = true;
                for (int i = 0; i < components.size(); i++) {
                    TypeMirror paramType = trees.getTypeMirror(new TreePath(parent, params.get(i)));
                    TypeMirror componentType = components.get(i).asType();
                    if (paramType == null || !paramType.toString().equals(componentType.toString())) {
                        allMatch = false;
                        break;
                    }
                }
                if (allMatch) {
                    return ConstructorKind.CANONICAL_FULL;
                }
            }

            // 4. Otherwise it's a secondary constructor
            return ConstructorKind.SECONDARY;
        }
    }

    public static boolean isCanonicalConstructor(Trees trees, Elements elements, TypeElement recordType, TreePath method) {
        final RecordConstructorDetector.ConstructorKind ctorKind = new RecordConstructorDetector(trees).detect(method, (MethodTree) method.getLeaf(), recordType);
        return ctorKind == RecordConstructorDetector.ConstructorKind.CANONICAL_COMPACT || ctorKind == RecordConstructorDetector.ConstructorKind.CANONICAL_FULL;
    }

    public static boolean isCompactConstructor(Trees trees, Elements elements, TypeElement recordType, TreePath method) {
        final RecordConstructorDetector.ConstructorKind ctorKind = new RecordConstructorDetector(trees).detect(method, (MethodTree) method.getLeaf(), recordType);
        return ctorKind == RecordConstructorDetector.ConstructorKind.CANONICAL_COMPACT;
    }

    public static boolean isCanonicalConstructor(Trees trees, Elements elements, TreePath method) {
        Element element = trees.getElement(method);
        if(element instanceof ExecutableElement) {
            return elements.isCanonicalConstructor((ExecutableElement) element);
        } else {
            return false;
        }
    }

    public static boolean isCompactConstructor(Trees trees, Elements elements, TreePath method) {
        Element element = trees.getElement(method);
        if(element instanceof ExecutableElement) {
            return elements.isCompactConstructor((ExecutableElement) element);
        } else {
            return false;
        }
    }
}
