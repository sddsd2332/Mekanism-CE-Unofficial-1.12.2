package mekanism.coremod;

import net.minecraft.client.renderer.tileentity.TileEntityRendererDispatcher;
import org.junit.jupiter.api.Test;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.util.CheckClassAdapter;

import java.io.IOException;
import java.io.InputStream;
import java.io.PrintWriter;
import java.io.StringWriter;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.objectweb.asm.Opcodes.ACC_PUBLIC;
import static org.objectweb.asm.Opcodes.INVOKESTATIC;
import static org.objectweb.asm.Opcodes.RETURN;
import static org.objectweb.asm.Opcodes.V1_8;

class MekanismCoreTransformerTest {

    @Test
    void injectsOnlyIntoTheExactTileRenderMethodAndDoesNotDuplicateTheHook() {
        byte[] original = createDispatcherClass();
        MekanismCoreTransformer transformer = new MekanismCoreTransformer();

        byte[] transformed = transformer.transform(
              MekanismCoreTransformer.tileEntityRendererDispatcherClass,
              MekanismCoreTransformer.tileEntityRendererDispatcherClass,
              original
        );
        byte[] transformedTwice = transformer.transform(
              MekanismCoreTransformer.tileEntityRendererDispatcherClass,
              MekanismCoreTransformer.tileEntityRendererDispatcherClass,
              transformed
        );

        ClassNode classNode = new ClassNode();
        new ClassReader(transformedTwice).accept(classNode, 0);
        MethodNode target = findMethod(classNode, "render", MekanismCoreTransformer.tileEntityRenderMethodDesc);
        MethodNode overload = findMethod(classNode, "render", "(Lnet/minecraft/tileentity/TileEntity;FIF)V");

        assertEquals(1, countOcclusionHooks(target));
        assertEquals(0, countOcclusionHooks(overload));
    }

    @Test
    void injectsIntoTheRuntimeSrgTileRenderMethod() {
        byte[] original = createRuntimeDispatcherClass();
        MekanismCoreTransformer transformer = new MekanismCoreTransformer();

        byte[] transformed = transformer.transform(
              "bwx",
              MekanismCoreTransformer.tileEntityRendererDispatcherClass,
              original
        );

        ClassNode classNode = new ClassNode();
        new ClassReader(transformed).accept(classNode, 0);
        MethodNode target = findMethod(classNode, "func_180546_a", MekanismCoreTransformer.tileEntityRenderMethodDesc);
        MethodNode otherRenderOverload = findMethod(classNode, "func_192855_a",
              "(Lnet/minecraft/tileentity/TileEntity;DDDFF)V");

        assertEquals(1, countOcclusionHooks(target));
        assertEquals(0, countOcclusionHooks(otherRenderOverload));
    }

    @Test
    void runtimeInjectionVerifiesAgainstTheRealDispatcherBytecode() throws IOException {
        byte[] runtimeDispatcher = readRealDispatcherInRuntimeNamespace();
        byte[] transformed = new MekanismCoreTransformer().transform(
              "bwx",
              MekanismCoreTransformer.tileEntityRendererDispatcherClass,
              runtimeDispatcher
        );

        ClassNode classNode = new ClassNode();
        new ClassReader(transformed).accept(classNode, 0);
        MethodNode target = findMethod(classNode, "func_180546_a", MekanismCoreTransformer.tileEntityRenderMethodDesc);
        assertEquals(1, countOcclusionHooks(target));

        StringWriter verifierOutput = new StringWriter();
        CheckClassAdapter.verify(new ClassReader(transformed), false, new PrintWriter(verifierOutput));
        assertEquals("", verifierOutput.toString());
    }

    @Test
    void obfuscatedNamesUseAStableEqualityContract() {
        MekanismCoreTransformer.ObfSafeName first = new MekanismCoreTransformer.ObfSafeName("render", "func_180546_a");
        MekanismCoreTransformer.ObfSafeName second = new MekanismCoreTransformer.ObfSafeName("render", "func_180546_a");

        assertTrue(first.matches("render"));
        assertTrue(first.matches("func_180546_a"));
        assertEquals(first, second);
        assertEquals(first.hashCode(), second.hashCode());
        assertNotEquals(first, "render");
    }

    private static byte[] createDispatcherClass() {
        ClassWriter writer = new ClassWriter(0);
        writer.visit(V1_8, ACC_PUBLIC, MekanismCoreTransformer.tileEntityRendererDispatcherClass.replace('.', '/'),
              null, "java/lang/Object", null);
        addVoidMethod(writer, "render", MekanismCoreTransformer.tileEntityRenderMethodDesc, 4);
        addVoidMethod(writer, "render", "(Lnet/minecraft/tileentity/TileEntity;FIF)V", 5);
        writer.visitEnd();
        return writer.toByteArray();
    }

    private static byte[] createRuntimeDispatcherClass() {
        ClassWriter writer = new ClassWriter(0);
        writer.visit(V1_8, ACC_PUBLIC, MekanismCoreTransformer.tileEntityRendererDispatcherClass.replace('.', '/'),
              null, "java/lang/Object", null);
        addVoidMethod(writer, "func_180546_a", MekanismCoreTransformer.tileEntityRenderMethodDesc, 4);
        addVoidMethod(writer, "func_192855_a", "(Lnet/minecraft/tileentity/TileEntity;DDDFF)V", 11);
        writer.visitEnd();
        return writer.toByteArray();
    }

    private static byte[] readRealDispatcherInRuntimeNamespace() throws IOException {
        String resource = "/" + TileEntityRendererDispatcher.class.getName().replace('.', '/') + ".class";
        try (InputStream stream = TileEntityRendererDispatcher.class.getResourceAsStream(resource)) {
            if (stream == null) {
                throw new IOException("Missing dispatcher class resource: " + resource);
            }
            ClassNode classNode = new ClassNode();
            new ClassReader(stream).accept(classNode, 0);
            findMethod(classNode, "render", MekanismCoreTransformer.tileEntityRenderMethodDesc).name = "func_180546_a";
            ClassWriter writer = new ClassWriter(0);
            classNode.accept(writer);
            return writer.toByteArray();
        }
    }

    private static void addVoidMethod(ClassWriter writer, String name, String descriptor, int locals) {
        MethodVisitor method = writer.visitMethod(ACC_PUBLIC, name, descriptor, null, null);
        method.visitCode();
        method.visitInsn(RETURN);
        method.visitMaxs(1, locals);
        method.visitEnd();
    }

    private static MethodNode findMethod(ClassNode classNode, String name, String descriptor) {
        return classNode.methods.stream()
              .filter(method -> name.equals(method.name) && descriptor.equals(method.desc))
              .findFirst()
              .orElseThrow(AssertionError::new);
    }

    private static long countOcclusionHooks(MethodNode method) {
        long count = 0;
        for (int index = 0; index < method.instructions.size(); index++) {
            AbstractInsnNode instruction = method.instructions.get(index);
            if (instruction instanceof MethodInsnNode call && call.getOpcode() == INVOKESTATIC
                  && MekanismCoreTransformer.coreMethodsClass.equals(call.owner)
                  && MekanismCoreTransformer.occlusionHookMethod.equals(call.name)
                  && MekanismCoreTransformer.occlusionHookDesc.equals(call.desc)) {
                count++;
            }
        }
        return count;
    }
}
