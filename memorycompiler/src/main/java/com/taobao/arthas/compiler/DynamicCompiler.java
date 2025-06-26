package com.taobao.arthas.compiler;


import javax.tools.Diagnostic;
import javax.tools.DiagnosticCollector;
import javax.tools.JavaCompiler;
import javax.tools.JavaFileManager;
import javax.tools.JavaFileObject;
import javax.tools.StandardJavaFileManager;
import javax.tools.ToolProvider;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public class DynamicCompiler {
    private final JavaCompiler javaCompiler = ToolProvider.getSystemJavaCompiler();
    private final StandardJavaFileManager standardFileManager;
    private final List<String> options = new ArrayList<String>();
    private final DynamicClassLoader dynamicClassLoader;

    private final Collection<JavaFileObject> compilationUnits = new ArrayList<JavaFileObject>();
    private final List<Diagnostic<? extends JavaFileObject>> errors = new ArrayList<Diagnostic<? extends JavaFileObject>>();
    private final List<Diagnostic<? extends JavaFileObject>> warnings = new ArrayList<Diagnostic<? extends JavaFileObject>>();

    public DynamicCompiler(ClassLoader classLoader, boolean enableProcessor) {
        if (javaCompiler == null) {
            throw new IllegalStateException(
                            "Can not load JavaCompiler from javax.tools.ToolProvider#getSystemJavaCompiler(),"
                                            + " please confirm the application running in JDK not JRE.");
        }
        standardFileManager = javaCompiler.getStandardFileManager(null, null, null);
        dynamicClassLoader = new DynamicClassLoader(classLoader);

        options.add("-Xlint:unchecked");
        options.add("-g");
        Set<String> annotationProcessors;
        if (enableProcessor && (annotationProcessors = findAnnotationProcessor(classLoader)).size() > 0) {
            options.add("-processor");
            options.add(String.join(",", annotationProcessors));
        }
    }

    public void addSource(String className, String source) {
        addSource(new StringSource(className, source));
    }

    public void addSource(JavaFileObject javaFileObject) {
        compilationUnits.add(javaFileObject);
    }

    public Map<String, Class<?>> build() {
        errors.clear();
        warnings.clear();

        JavaFileManager fileManager = new DynamicJavaFileManager(standardFileManager, dynamicClassLoader);
        DiagnosticCollector<JavaFileObject> collector = new DiagnosticCollector<JavaFileObject>();
        JavaCompiler.CompilationTask task = javaCompiler.getTask(null, fileManager, collector, options, null,
                        compilationUnits);
        try {
            if (!compilationUnits.isEmpty()) {
                boolean result = task.call();
                if (!result || collector.getDiagnostics().size() > 0) {
                    for (Diagnostic<? extends JavaFileObject> diagnostic : collector.getDiagnostics()) {
                        switch (diagnostic.getKind()) {
                        case NOTE:
                        case MANDATORY_WARNING:
                        case WARNING:
                            warnings.add(diagnostic);
                            break;
                        case OTHER:
                        case ERROR:
                        default:
                            errors.add(diagnostic);
                            break;
                        }
                    }
                    if (!errors.isEmpty()) {
                        throw new DynamicCompilerException("Compilation Error", errors);
                    }
                }
            }
            return dynamicClassLoader.getClasses();
        } catch (Throwable e) {
            throw new DynamicCompilerException(e, errors);
        } finally {
            compilationUnits.clear();
        }
    }

    public Map<String, byte[]> buildByteCodes() {
        errors.clear();
        warnings.clear();

        JavaFileManager fileManager = new DynamicJavaFileManager(standardFileManager, dynamicClassLoader);
        DiagnosticCollector<JavaFileObject> collector = new DiagnosticCollector<JavaFileObject>();
        JavaCompiler.CompilationTask task = javaCompiler.getTask(null, fileManager, collector, options, null,
                        compilationUnits);
        try {
            if (!compilationUnits.isEmpty()) {
                boolean result = task.call();
                if (!result || collector.getDiagnostics().size() > 0) {
                    for (Diagnostic<? extends JavaFileObject> diagnostic : collector.getDiagnostics()) {
                        switch (diagnostic.getKind()) {
                        case NOTE:
                        case MANDATORY_WARNING:
                        case WARNING:
                            warnings.add(diagnostic);
                            break;
                        case OTHER:
                        case ERROR:
                        default:
                            errors.add(diagnostic);
                            break;
                        }
                    }
                    if (!errors.isEmpty()) {
                        throw new DynamicCompilerException("Compilation Error", errors);
                    }
                }
            }
            return dynamicClassLoader.getByteCodes();
        } catch (ClassFormatError e) {
            throw new DynamicCompilerException(e, errors);
        } finally {
            compilationUnits.clear();
        }
    }

    private List<String> diagnosticToString(List<Diagnostic<? extends JavaFileObject>> diagnostics) {
        List<String> diagnosticMessages = new ArrayList<String>();
        for (Diagnostic<? extends JavaFileObject> diagnostic : diagnostics) {
            diagnosticMessages.add("line: " + diagnostic.getLineNumber() + ", message: " + diagnostic.getMessage(Locale.US));
        }
        return diagnosticMessages;
    }

    public List<String> getErrors() {
        return diagnosticToString(errors);
    }

    public List<String> getWarnings() {
        return diagnosticToString(warnings);
    }

    public ClassLoader getClassLoader() {
        return dynamicClassLoader;
    }

    static Set<String> findAnnotationProcessor(ClassLoader classLoader) {
        Enumeration<URL> resources;
        try {
            resources = classLoader.getResources("META-INF/services/javax.annotation.processing.Processor");
        } catch (IOException e) {
            return Collections.emptySet();
        }
        HashSet<String> processors = new HashSet<>();
        while (resources.hasMoreElements()) {
            try (InputStream in = resources.nextElement().openStream()) {
                byte[] bytes = new byte[in.available()];
                in.read(bytes);
                String[] split = new String(bytes).split("\n");
                if (split.length > 0) {
                    processors.addAll(Arrays.asList(split));
                }
            } catch (IOException ignore) {
            }
        }
        return processors;
    }
}
