package com.alibaba.testable.transformer;

import com.alibaba.testable.model.MethodInfo;
import com.alibaba.testable.visitor.MethodRecordVisitor;
import com.alibaba.testable.visitor.TestableVisitor;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;

import java.lang.annotation.Annotation;
import java.lang.instrument.ClassFileTransformer;
import java.lang.reflect.Field;
import java.security.ProtectionDomain;
import java.util.List;
import java.util.Vector;

public class TestableFileTransformer implements ClassFileTransformer {

    private static final String ENABLE_TESTABLE = "com.alibaba.testable.annotation.EnableTestable";
    private static final String DOT = ".";
    private static final String SLASH = "/";
    private static final String TEST_POSTFIX = "Test";

    public byte[] transform(ClassLoader loader, String className, Class<?> classBeingRedefined,
                            ProtectionDomain protectionDomain, byte[] classfileBuffer) {
        if (null == loader || null == className) {
            // Ignore system class
            return null;
        }
        String dotClassName = className.replace(SLASH, DOT);
        MethodRecordVisitor methodRecordVisitor = getMemberMethods(classfileBuffer,
            isTestClassTestable(loader, dotClassName));
        if (!methodRecordVisitor.isNeedTransform()) {
            // Neither EnableTestable on test class, nor EnableTestableInject on source class
            return null;
        }

        ClassReader reader = new ClassReader(classfileBuffer);
        ClassWriter writer = new ClassWriter(reader, 0);
        reader.accept(new TestableVisitor(writer, methodRecordVisitor.getMethods()), 0);
        return writer.toByteArray();
    }

    private boolean isTestClassTestable(ClassLoader loader, String dotClassName) {
        boolean needTransform = false;
        try {
            Field classesField = ClassLoader.class.getDeclaredField("classes");
            classesField.setAccessible(true);
            Vector<Class> classesVector = (Vector<Class>)classesField.get(loader);
            if (null != classesVector) {
                for (Class c : classesVector) {
                    String testClassName = dotClassName + TEST_POSTFIX;
                    if (c.getName().endsWith(testClassName)) {
                        Class<?> testClazz = Class.forName(testClassName);
                        for (Annotation a : testClazz.getAnnotations()) {
                            if (a.annotationType().getName().equals(ENABLE_TESTABLE)) {
                                needTransform = true;
                            }
                        }
                    }
                }
            }
        } catch (NoSuchFieldException e) {
            return false;
        } catch (IllegalAccessException e) {
            return false;
        } catch (ClassNotFoundException e) {
            return false;
        }
        return needTransform;
    }

    private MethodRecordVisitor getMemberMethods(byte[] classfileBuffer, boolean needTransform) {
        ClassReader reader = new ClassReader(classfileBuffer);
        ClassWriter writer = new ClassWriter(reader, 0);
        MethodRecordVisitor visitor = new MethodRecordVisitor(writer, needTransform);
        reader.accept(visitor, 0);
        return visitor;
    }

}