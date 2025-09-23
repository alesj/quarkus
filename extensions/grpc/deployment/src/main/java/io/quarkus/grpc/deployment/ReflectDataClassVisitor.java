package io.quarkus.grpc.deployment;

import static org.objectweb.asm.Opcodes.AALOAD;
import static org.objectweb.asm.Opcodes.ALOAD;
import static org.objectweb.asm.Opcodes.ARETURN;
import static org.objectweb.asm.Opcodes.ASTORE;
import static org.objectweb.asm.Opcodes.GETSTATIC;
import static org.objectweb.asm.Opcodes.ICONST_0;
import static org.objectweb.asm.Opcodes.ICONST_1;
import static org.objectweb.asm.Opcodes.IFEQ;
import static org.objectweb.asm.Opcodes.IFNE;
import static org.objectweb.asm.Opcodes.INVOKEVIRTUAL;

import java.lang.reflect.Method;
import java.lang.reflect.Type;
import java.util.HashMap;
import java.util.Map;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Label;
import org.objectweb.asm.MethodVisitor;

import com.google.protobuf.GeneratedMessageV3;

import io.quarkus.deployment.QuarkusClassVisitor;
import io.quarkus.gizmo.Gizmo;

// The goal is to inject HACK into the org.apache.avro.reflect.ReflectData:createSchema method

//protected Schema createSchema(Type type, Map<String, Schema> names) {
//    if (type instanceof GenericArrayType) { // generic array
//        Type component = ((GenericArrayType) type).getGenericComponentType();
//        if (component == Byte.TYPE) // byte array
//            return Schema.create(Schema.Type.BYTES);
//        Schema result = Schema.createArray(createSchema(component, names));
//        setElement(result, component);
//        return result;
//    } else if (type instanceof ParameterizedType) {
//        ParameterizedType ptype = (ParameterizedType) type;
//        Class raw = (Class) ptype.getRawType();
//        Type[] params = ptype.getActualTypeArguments();
//        if (Map.class.isAssignableFrom(raw)) { // Map

// -- HACK

//                if (!Class.class.isInstance(params[0])) {
//                    Schema schema = createNonStringMapSchema(params[0], params[1], names);
//                    schema.addProp(CLASS_PROP, raw.getName());
//                    return schema;
//                }

// ---

//            Class key = (Class) params[0];

public class ReflectDataClassVisitor extends QuarkusClassVisitor {
    public ReflectDataClassVisitor(ClassVisitor classVisitor) {
        super(Gizmo.ASM_API_VERSION, classVisitor);
    }

    public MethodVisitor visitMethod(int access, String name, String descriptor,
            String signature, String[] exceptions) {
        MethodVisitor mv = super.visitMethod(access, name, descriptor, signature, exceptions);
        return new MethodVisitor(Gizmo.ASM_API_VERSION, mv) {
            private boolean found; // so we know we're inside the right "if"
            private Label falseLabel = null;
            private boolean injected; // to inject only once

            @Override
            public void visitMethodInsn(int opcode, String owner, String methodName,
                    String descriptor, boolean isInterface) {
                if (opcode == INVOKEVIRTUAL
                        && owner.equals("java/lang/Class")
                        && methodName.equals("isAssignableFrom")
                        && descriptor.equals("(Ljava/lang/Class;)Z")) {

                    found = true;

                    super.visitMethodInsn(opcode, owner, methodName, descriptor, isInterface);
                } else {
                    super.visitMethodInsn(opcode, owner, methodName, descriptor, isInterface);
                }
            }

            @Override
            public void visitJumpInsn(int opcode, Label label) {
                if (!injected && opcode == IFEQ) {
                    // this is the branch for "if condition is false"
                    falseLabel = label;
                }
                super.visitJumpInsn(opcode, label);
            }

            @Override
            public void visitLabel(Label label) {
                if (found && !injected && falseLabel != null && label != falseLabel) {
                    injected = true; // inject only once

                    Label elseLabel = new Label();

                    int namesIndex = 2;
                    int rawIndex = 4;
                    int paramsIndex = 5;

                    // if (!Class.class.isInstance(params[0]))
                    mv.visitLdcInsn(org.objectweb.asm.Type.getType(Class.class));
                    mv.visitVarInsn(ALOAD, paramsIndex);
                    mv.visitInsn(ICONST_0);
                    mv.visitInsn(AALOAD);
                    mv.visitMethodInsn(INVOKEVIRTUAL,
                            "java/lang/Class",
                            "isInstance",
                            "(Ljava/lang/Object;)Z",
                            false);
                    mv.visitJumpInsn(IFNE, elseLabel);

                    // body
                    mv.visitVarInsn(ALOAD, paramsIndex);
                    mv.visitInsn(ICONST_0);
                    mv.visitInsn(AALOAD);
                    mv.visitVarInsn(ALOAD, paramsIndex);
                    mv.visitInsn(ICONST_1);
                    mv.visitInsn(AALOAD);
                    mv.visitVarInsn(ALOAD, namesIndex);
                    mv.visitMethodInsn(INVOKEVIRTUAL,
                            "org/apache/avro/reflect/ReflectData",
                            "createNonStringMapSchema",
                            "(Ljava/lang/reflect/Type;Ljava/lang/reflect/Type;Ljava/util/Map;)Lorg/apache/avro/Schema;",
                            false);
                    mv.visitVarInsn(ASTORE, 6);
                    mv.visitFieldInsn(GETSTATIC,
                            "org/apache/avro/reflect/ReflectData",
                            "CLASS_PROP",
                            "Ljava/lang/String;");
                    mv.visitVarInsn(ALOAD, rawIndex);
                    mv.visitMethodInsn(INVOKEVIRTUAL,
                            "java/lang/Class",
                            "getName",
                            "()Ljava/lang/String;",
                            false);
                    mv.visitVarInsn(ASTORE, 7);
                    //                    mv.visitVarInsn(ALOAD, 6);
                    //                    mv.visitVarInsn(ALOAD, 7);
                    mv.visitMethodInsn(INVOKEVIRTUAL,
                            "org/apache/avro/Schema",
                            "addProp",
                            "(Ljava/lang/String;Ljava/lang/Object;)V",
                            false);
                    mv.visitVarInsn(ALOAD, 6);
                    mv.visitInsn(ARETURN);
                }

                mv.visitLabel(label); // do original
            }
        };
    }

    public static void main(String[] args) throws Exception {
        ClassReader cr = new ClassReader("org.apache.avro.reflect.ReflectData");
        ClassWriter cw = new ClassWriter(cr, 0);
        ClassVisitor cv = new ReflectDataClassVisitor(cw);
        cr.accept(cv, 0);

        byte[] modifiedClass = cw.toByteArray();

        Class<?> modifiedRD = new ClassLoader() {
            Class<?> define(byte[] b) {
                return defineClass(null, b, 0, b.length);
            }
        }.define(modifiedClass);

        Object data = modifiedRD.getDeclaredConstructor().newInstance();
        Method m = ReflectDataClassVisitor.class.getDeclaredMethod("type");
        Type type = m.getGenericReturnType();

        Method method = data.getClass().getDeclaredMethod("createSchema", Type.class, Map.class);
        method.setAccessible(true);

        method.invoke(data, type, new HashMap<>());
    }

    private static Map<GeneratedMessageV3, String> type() {
        return null;
    }

    //    public static void main(String[] args) throws Exception {
    //        Method m = ReflectDataClassVisitor.class.getDeclaredMethod("type");
    //        Type type = m.getGenericReturnType();
    //
    //        ReflectData data = new ReflectData();
    //
    //        Method method = data.getClass().getDeclaredMethod("createSchema", Type.class, Map.class);
    //        method.setAccessible(true);
    //
    //        method.invoke(data, type, new HashMap<>());
    //    }
}
