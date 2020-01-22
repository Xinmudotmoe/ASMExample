package moe.xinmu.invoke_dynamic;

import org.objectweb.asm.*;
import org.objectweb.asm.commons.GeneratorAdapter;

import java.io.FileOutputStream;
import java.lang.invoke.*;
import java.lang.reflect.Method;

public class DynamicMain1 extends ClassLoader {
    DynamicMain1() {
        super(DynamicMain1.class.getClassLoader());
    }

    public static void main(String[] args) throws Throwable {
        ClassWriter classWriter = new ClassWriter(ClassWriter.COMPUTE_FRAMES);
        classWriter.visit(52, Opcodes.ACC_PUBLIC | Opcodes.ACC_SUPER, "XM/A", null, "java/lang/Object", null);
        MethodVisitor method = classWriter.visitMethod(Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC, "aaa", "()Ljava/lang/String;", null, null);
        MethodType mt = MethodType.methodType(CallSite.class, MethodHandles.Lookup.class, String.class,
                MethodType.class);
        Handle bootstrap = new Handle(Opcodes.H_INVOKESTATIC, Type.getType(DynamicMain1.class).getInternalName(), "boot",
                mt.toMethodDescriptorString(), false);
        GeneratorAdapter adapter = new GeneratorAdapter(method, Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC, "aaa", "()Ljava/lang/String;");
        adapter.invokeDynamic("print", "()Ljava/lang/String;", bootstrap);
        adapter.returnValue();
        adapter.endMethod();
        byte[] bytes = classWriter.toByteArray();
        try (FileOutputStream outputStream = new FileOutputStream("z:\\asdfawfwaaf.class")) {
            outputStream.write(bytes);
        }
        Method method1 = new DynamicMain1().defineClass("XM.A", bytes, 0, bytes.length).getMethod("aaa");

        System.out.println(method1.invoke(null));
        callSite.setTarget(MethodHandles.lookup().findStatic(DynamicMain1.class, "print1", MethodType.methodType(String.class)));
        System.out.println(method1.invoke(null));
    }

    private static VolatileCallSite callSite;

    public static String print() {
        System.out.println("HelloWorld");
        return "H";
    }

    public static String print1() {
        System.out.println("Hello, World!");
        return "He";
    }

    public static CallSite boot(MethodHandles.Lookup caller, String name, MethodType type) throws Throwable {
        MethodHandles.Lookup lookup = MethodHandles.lookup();
        MethodHandle mh = lookup.findStatic(DynamicMain1.class, name, MethodType.methodType(String.class));
        return callSite = new VolatileCallSite(mh.asType(type));
    }
}
