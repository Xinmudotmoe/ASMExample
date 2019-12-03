package moe.xinmu.wllambda;

import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.commons.GeneratorAdapter;

import java.io.PrintStream;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.security.AccessController;
import java.security.PrivilegedActionException;
import java.security.PrivilegedExceptionAction;
import java.util.Arrays;
import java.util.Objects;
import java.util.Random;
import java.util.UUID;

public interface PureLambdaMain {
    int class_version = ((Double) (Double.parseDouble(System.getProperty("java.class.version"))))
            .intValue();
    Type object_class = Type.getType(Object.class);
    org.objectweb.asm.commons.Method object_constructor =
            new org.objectweb.asm.commons.Method("<init>","()V");

    static void main(String[] args) throws Exception{
        @SuppressWarnings("unchecked")
        R<String> stringR=(R<String>)genClass(
                        new LClassLoader(),
                        System.out,
                        PrintStream.class.getMethod("print", Object.class),
                        R.class.getMethod("run",Object.class),
                        R.class, true);
        stringR.run("Hello, World!");
    }
    class LClassLoader extends ClassLoader implements LambdaClassLoader {
        LClassLoader(){
            super(PureLambdaMain.class.getClassLoader());
        }
        @Override
        public Class<?> l_defineClass(String name, byte[] b, int off, int len) {
            return defineClass(name, b, off, len);
        }
    }
    interface R<T>{
        void run(T t) throws Exception;
    }

    static <T>T genClass(
            LambdaClassLoader loader,
            Object o,
            Method source,
            Method target,
            Class<T> target_class,
            boolean force_cast) throws PrivilegedActionException {
        Objects.requireNonNull(loader);
        Objects.requireNonNull(source);
        Objects.requireNonNull(target);
        Objects.requireNonNull(target_class);
        if(target.getDeclaringClass()!=target_class)
            throw new IllegalArgumentException();
        final boolean isStatic= Modifier.isStatic(source.getModifiers());
        final boolean isInterface=target_class.isInterface();
        final boolean sourceIsInterface=source.getDeclaringClass().isInterface();
        String clazzname=Type.getInternalName(source.getDeclaringClass());
        ClassWriter cw=new ClassWriter(ClassWriter.COMPUTE_MAXS);
        String cwname="x"+Type.getInternalName(target.getDeclaringClass())+"$"+
                (new Random().nextDouble()+"").replace(".","/");
        cw.visit(class_version, Opcodes.ACC_PUBLIC|Opcodes.ACC_SUPER,cwname,null,
                isInterface?"java/lang/Object":Type.getInternalName(target_class),
                isInterface?
                        new String[]{Type.getInternalName(target.getDeclaringClass())}:
                        new String[0]);
        /* constructor */
        final GeneratorAdapter constructor=new GeneratorAdapter(
                cw.visitMethod(Opcodes.ACC_PUBLIC,"<init>","()V",null,null)
                ,Opcodes.ACC_PUBLIC,"<init>","()V");
        constructor.loadThis();
        constructor.invokeConstructor(object_class,object_constructor);
        constructor.returnValue();
        constructor.endMethod();
        /* set */
        String setname=null;
        if(!isStatic) {
            cw.visitField(Opcodes.ACC_PRIVATE, "$this",
                    Type.getDescriptor(source.getDeclaringClass()),clazzname, null);
            setname="set$this"+ UUID.randomUUID();
            final GeneratorAdapter set=new GeneratorAdapter(
                    cw.visitMethod(Opcodes.ACC_PUBLIC,
                            setname,
                            "(L"+clazzname+";)V",
                            null,null),
                    Opcodes.ACC_PUBLIC,setname,"(L"+clazzname+";)V");
            set.loadThis();
            set.loadArg(0);
            set.putField(Type.getType("L"+cwname+";"),"$this",Type.getType("L"+clazzname+";"));
            set.returnValue();
            set.endMethod();
        }
        /* invoke */
        org.objectweb.asm.commons.Method targetmethod
                =org.objectweb.asm.commons.Method.getMethod(target);
        String[] exceptions= Arrays.stream(target.getExceptionTypes())
                .map(Type::getInternalName)
                .toArray(String[]::new);
        final GeneratorAdapter invoke=new GeneratorAdapter(
                cw.visitMethod(Opcodes.ACC_PUBLIC,
                        targetmethod.getName(),
                        targetmethod.getDescriptor(),
                        null,
                        exceptions),
                Opcodes.ACC_PUBLIC,
                targetmethod.getName(),
                targetmethod.getDescriptor());
        if(!isStatic){
            invoke.loadThis();
            invoke.getField(Type.getType("L"+cwname+";"),
                    "$this",
                    Type.getType(source.getDeclaringClass()));
        }
        Type[] types=targetmethod.getArgumentTypes();
        Type[] types1=org.objectweb.asm.commons.Method.getMethod(source).getArgumentTypes();
        for (int i = 0; i < types.length; i++){
            invoke.loadArg(i);
            if(force_cast)
                if(!types[i].equals(types1[i]))
                    invoke.checkCast(types1[i]);
        }
        if(!isStatic)
            invoke.visitMethodInsn(sourceIsInterface?Opcodes.INVOKEINTERFACE:Opcodes.INVOKEVIRTUAL,
                    Type.getType(source.getDeclaringClass()).getInternalName(),
                    source.getName(),
                    org.objectweb.asm.commons.Method.getMethod(source).getDescriptor(),
                    sourceIsInterface);
        else
            invoke.visitMethodInsn(Opcodes.INVOKESTATIC,
                    Type.getType(source.getDeclaringClass()).getInternalName(),
                    source.getName(),
                    org.objectweb.asm.commons.Method.getMethod(source).getDescriptor(),
                    sourceIsInterface);
        if(force_cast)
            if(!targetmethod.getReturnType()
                    .equals(org.objectweb.asm.commons.Method.getMethod(source).getReturnType()))
                invoke.checkCast(org.objectweb.asm.commons.Method.getMethod(source).getReturnType());
        invoke.returnValue();
        invoke.endMethod();
        byte[] bytes=cw.toByteArray();

        Class<?> t =AccessController.doPrivileged((PrivilegedExceptionAction<Class<?>>)
                ()-> loader.l_defineClass(cwname.replace("/","."),bytes,0,bytes.length));
        T tt=null;
        try {
            tt=target_class.cast(t.getConstructor().newInstance());
        } catch (Exception e) {
            e.printStackTrace();
        }
        if(!isStatic) {
            for(Method method:t.getMethods()){
                if(method.getName().equals(setname)) {
                    try {
                        method.invoke(tt,o);
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }
            }
        }
        return tt;
    }
    interface LambdaClassLoader{
        Class<?> l_defineClass(String name, byte[] b, int off, int len);
    }
}
