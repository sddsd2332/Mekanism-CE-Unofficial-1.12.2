package mekanism.stress.timing;

import net.minecraft.launchwrapper.IClassTransformer;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.commons.AdviceAdapter;

/** Adds clocks only to the two named recipe methods; no production source or processing behavior is replaced. */
public final class RecipeTimingTransformer implements IClassTransformer {
    private static final String METER = "mekanism/stress/timing/RecipeTiming";

    public byte[] transform(String name, String transformedName, byte[] bytes) {
        if (bytes == null) return null;
        final int metric;
        if ("mekanism.common.recipe.RecipeHandler".equals(transformedName)) metric = 0;
        else if ("mekanism.common.recipe.cache.ICachedRecipeHolder".equals(transformedName)) metric = 1;
        else return bytes;
        ClassReader reader = new ClassReader(bytes);
        ClassWriter writer = new ClassWriter(reader, ClassWriter.COMPUTE_MAXS);
        reader.accept(new ClassVisitor(Opcodes.ASM5, writer) {
            @Override public MethodVisitor visitMethod(int access, String method, String descriptor, String signature, String[] exceptions) {
                MethodVisitor delegate = super.visitMethod(access, method, descriptor, signature, exceptions);
                boolean selected = metric == 0 ? "getRecipe".equals(method) &&
                      "(Lmekanism/common/recipe/inputs/MachineInput;Ljava/util/Map;)Lmekanism/common/recipe/machines/MachineRecipe;".equals(descriptor) :
                      "getUpdatedCache".equals(method) && "(I)Lmekanism/common/recipe/cache/CachedRecipe;".equals(descriptor);
                if (!selected) return delegate;
                RecipeTiming.instrumented(metric);
                return new AdviceAdapter(Opcodes.ASM5, delegate, access, method, descriptor) {
                    private int started;
                    @Override protected void onMethodEnter() {
                        visitMethodInsn(INVOKESTATIC, METER, "start", "()J", false);
                        started = newLocal(Type.LONG_TYPE);
                        storeLocal(started);
                    }
                    @Override protected void onMethodExit(int opcode) {
                        if (opcode != ARETURN) return;
                        dup(); loadLocal(started); push(metric);
                        visitMethodInsn(INVOKESTATIC, METER, "finish", "(Ljava/lang/Object;JI)V", false);
                    }
                };
            }
        }, ClassReader.EXPAND_FRAMES);
        return writer.toByteArray();
    }
}
