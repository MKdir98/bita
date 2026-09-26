package ir.bita.esb.sync;

import org.codehaus.groovy.ast.ClassNode;
import org.codehaus.groovy.ast.expr.ClassExpression;
import org.codehaus.groovy.ast.expr.ConstructorCallExpression;
import org.codehaus.groovy.ast.expr.Expression;
import org.codehaus.groovy.ast.expr.MethodCallExpression;
import org.codehaus.groovy.ast.expr.PropertyExpression;
import org.codehaus.groovy.ast.expr.StaticMethodCallExpression;
import org.codehaus.groovy.control.CompilerConfiguration;
import org.codehaus.groovy.control.customizers.SecureASTCustomizer;

import java.util.List;
import java.util.Set;

/**
 * Compile-time restrictions for scripts the model wrote (templates added through groovy_template),
 * applied both to their trial run and to every later start on a service's gateway. Catalog
 * templates, written and reviewed by people, compile without them.
 *
 * <p>Refused: running processes, touching the file system, reflection and class loading,
 * evaluating strings as code, and reaching the JVM or environment — by import, by class
 * reference, by constructor and by method name, so that e.g. {@code "ls".execute()} or
 * {@code x.class.forName(...)} is refused as well as {@code Runtime.getRuntime().exec(...)}.
 *
 * <p>This narrows what a script can do; it is not an isolation boundary. Groovy's dynamic
 * dispatch can still reach refused members through indirection the compiler cannot see, and a
 * script can still send data wherever its HTTP client can connect. Running model-written
 * templates in a separate process or container is what would make that a boundary.
 */
public final class ScriptSandbox {

    private static final List<String> BLOCKED_PACKAGES = List.of(
            "java.io.", "java.nio.file.", "java.lang.reflect.", "java.lang.invoke.", "java.sql.", "javax.sql.",
            "java.net.Socket", "java.net.ServerSocket", "java.net.URLClassLoader", "sun.", "jdk.internal.");

    private static final Set<String> BLOCKED_CLASSES = Set.of(
            "java.lang.Runtime", "java.lang.ProcessBuilder", "java.lang.Process", "java.lang.ProcessHandle",
            "java.lang.System", "java.lang.Thread", "java.lang.ThreadGroup", "java.lang.ClassLoader",
            "java.lang.Class", "java.lang.SecurityManager",
            "groovy.lang.GroovyShell", "groovy.lang.GroovyClassLoader", "groovy.util.Eval",
            "groovy.lang.MetaClass", "groovy.lang.ExpandoMetaClass");

    private static final Set<String> BLOCKED_METHODS = Set.of(
            "exec", "execute", "evaluate", "forName", "loadClass", "defineClass", "newInstance",
            "getClassLoader", "getDeclaredMethod", "getDeclaredField", "getMethod", "setAccessible",
            "exit", "halt", "getenv", "setProperty", "loadLibrary", "invokeMethod",
            "getMetaClass", "setMetaClass");

    private static final Set<String> BLOCKED_PROPERTIES = Set.of("class", "metaClass", "classLoader", "declaredMethods");

    private ScriptSandbox() {
    }

    public static CompilerConfiguration configuration() {
        SecureASTCustomizer secure = new SecureASTCustomizer();
        secure.setIndirectImportCheckEnabled(true);
        secure.setDisallowedImports(List.copyOf(BLOCKED_CLASSES));
        secure.setDisallowedStarImports(BLOCKED_PACKAGES.stream().filter(p -> p.endsWith(".")).toList());
        secure.setDisallowedReceivers(List.copyOf(BLOCKED_CLASSES));
        secure.addExpressionCheckers(ScriptSandbox::allowed);
        CompilerConfiguration config = new CompilerConfiguration();
        config.addCompilationCustomizers(secure);
        return config;
    }

    /** False for an expression that names, builds or calls something refused. */
    static boolean allowed(Expression e) {
        if (e instanceof ClassExpression c) {
            return !blocked(c.getType());
        }
        if (e instanceof ConstructorCallExpression c) {
            return !blocked(c.getType());
        }
        if (e instanceof StaticMethodCallExpression s) {
            return !blocked(s.getOwnerType()) && !BLOCKED_METHODS.contains(s.getMethod());
        }
        if (e instanceof MethodCallExpression m) {
            String name = m.getMethodAsString();
            return name == null || !BLOCKED_METHODS.contains(name);
        }
        if (e instanceof PropertyExpression p) {
            String name = p.getPropertyAsString();
            return name == null || !BLOCKED_PROPERTIES.contains(name);
        }
        return true;
    }

    private static boolean blocked(ClassNode type) {
        String name = type.getName();
        return BLOCKED_CLASSES.contains(name) || BLOCKED_PACKAGES.stream().anyMatch(name::startsWith);
    }
}
