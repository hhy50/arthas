package com.taobao.arthas.compiler;

import org.junit.Assert;
import org.junit.Test;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Paths;
import java.util.Map;

/**
 *
 * @author hengyunabc 2019-02-06
 *
 */
public class DynamicCompilerTest {

    @Test
    public void test() throws IOException {
        String jarPath = LoggerFactory.class.getProtectionDomain().getCodeSource().getLocation().getFile();
        File file = new File(jarPath);

        URLClassLoader classLoader = new URLClassLoader(new URL[] { file.toURI().toURL() },
                        ClassLoader.getSystemClassLoader().getParent());

        DynamicCompiler dynamicCompiler = new DynamicCompiler(classLoader, false);

        InputStream logger1Stream = DynamicCompilerTest.class.getClassLoader().getResourceAsStream("TestLogger1.java");
        InputStream logger2Stream = DynamicCompilerTest.class.getClassLoader().getResourceAsStream("TestLogger2.java");

        dynamicCompiler.addSource("TestLogger2", toString(logger2Stream));
        dynamicCompiler.addSource("TestLogger1", toString(logger1Stream));

        Map<String, byte[]> byteCodes = dynamicCompiler.buildByteCodes();

        Assert.assertTrue("TestLogger1", byteCodes.containsKey("com.test.TestLogger1"));
        Assert.assertTrue("TestLogger2", byteCodes.containsKey("com.hello.TestLogger2"));
    }

    /**
     *
     * @throws IOException
     */
    @Test
    public void testAnnotationProcessor() throws IOException, ClassNotFoundException, NoSuchMethodException, InstantiationException, IllegalAccessException, InvocationTargetException {
        String jarPath = DynamicCompilerTest.class.getProtectionDomain().getCodeSource().getLocation().getFile();
        File file = new File(jarPath);

        URLClassLoader classLoader = new URLClassLoader(new URL[] { file.toURI().toURL(), Paths.get(file.getPath(), "file").toUri().toURL() },
                ClassLoader.getSystemClassLoader());

        DynamicCompiler dynamicCompiler = new DynamicCompiler(classLoader, true);
        InputStream userClass = DynamicCompilerTest.class.getClassLoader().getResourceAsStream("testAnnotationProcessor/User.java");

        dynamicCompiler.addSource("arthas.memorycompiler.test.User", toString(userClass));

        Map<String, byte[]> byteCodes = dynamicCompiler.buildByteCodes();
        ApClassLoader apClassLoader = new ApClassLoader(ClassLoader.getSystemClassLoader(), byteCodes);
        Class<?> clazz = apClassLoader.loadClass("arthas.memorycompiler.test.User");

        Field[] declaredFields = clazz.getDeclaredFields();
        Assert.assertEquals(declaredFields.length, 2);
        Assert.assertEquals(clazz.getDeclaredMethods().length, declaredFields.length*2);

        Object obj = clazz.newInstance();
        for (Field field : declaredFields) {
            String name = StringUtil.firstCharUpper(field.getName());
            Method setterMethod = clazz.getDeclaredMethod("set"+name, field.getType());
            Method getterMethod = clazz.getDeclaredMethod("get"+name);

            Object value = null;
            if (field.getType() == String.class) {
                value = field.getName() + "_value";
            } else if (field.getType() == Integer.class) {
                value = 10;
            }
            setterMethod.invoke(obj, value);
            Assert.assertEquals(getterMethod.invoke(obj), value);
        }
    }


    /**
     * Get the contents of an <code>InputStream</code> as a String
     * using the default character encoding of the platform.
     * <p>
     * This method buffers the input internally, so there is no need to use a
     * <code>BufferedInputStream</code>.
     *
     * @param input  the <code>InputStream</code> to read from
     * @return the requested String
     * @throws NullPointerException if the input is null
     * @throws IOException if an I/O error occurs
     */
    public static String toString(InputStream input) throws IOException {
        BufferedReader br = null;
        try {
            StringBuilder sb = new StringBuilder();
            br = new BufferedReader(new InputStreamReader(input));
            String line;
            while ((line = br.readLine()) != null) {
                sb.append(line).append("\n");
            }
            return sb.toString();
        } finally {
            if (br != null) {
                try {
                    br.close();
                } catch (IOException e) {
                    // ignore
                }
            }
        }
    }

    static class ApClassLoader extends ClassLoader {
        private final Map<String, byte[]> namespace;

        ApClassLoader(ClassLoader classLoader, Map<String, byte[]> namespace) {
            super(classLoader);
            this.namespace = namespace;
        }

        @Override
        protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
            if (namespace.containsKey(name)) {
                byte[] bytes = namespace.get(name);
                return defineClass(name, bytes, 0, bytes.length);
            }
            return super.loadClass(name, resolve);
        }
    }
}
