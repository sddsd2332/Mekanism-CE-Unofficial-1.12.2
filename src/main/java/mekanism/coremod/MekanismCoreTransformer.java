package mekanism.coremod;


import net.minecraft.launchwrapper.IClassTransformer;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Label;
import org.objectweb.asm.tree.*;

import java.util.Iterator;

import static mekanism.coremod.MekanismCoremod.mainLogger;
import static mekanism.coremod.MekanismCoremod.runtimeDeobfEnabled;
import static org.objectweb.asm.Opcodes.*;

public class MekanismCoreTransformer implements IClassTransformer {

    protected static class ObfSafeName {
        final String deobf, srg;

        public ObfSafeName(String deobf, String srg) {
            this.deobf = deobf;
            this.srg = srg;
        }

        public String getName() {
            return runtimeDeobfEnabled ? srg : deobf;
        }

        public boolean matches(String obj) {
            if (obj != null) {
                return obj.equals(deobf) || obj.equals(srg);
            }
            return false;
        }

        @Override
        public boolean equals(Object obj) {
            if (obj instanceof ObfSafeName obf) {
                return obf.deobf.equals(deobf) && obf.srg.equals(srg);
            }
            return false;
        }

        @Override
        public int hashCode() {
            int result = deobf.hashCode();
            return 31 * result + srg.hashCode();
        }

    }


    protected static abstract class Transform {
        abstract void transform(Iterator<MethodNode> methods);
    }

    static final String renderItemClass = "net.minecraft.client.renderer.RenderItem";
    static final ObfSafeName renderItemOverlayIntoGUIMethod = new ObfSafeName("renderItemOverlayIntoGUI", "func_180453_a");
    static final ObfSafeName renderItemAndEffectIntoGUI = new ObfSafeName("renderItemAndEffectIntoGUI", "func_184391_a");
    static final ObfSafeName renderItemDisplayName = new ObfSafeName("renderItemAndEffectIntoGUI, renderItemOverlayIntoGUI", "func_180453_a, func_184391_a");
    static final String tileEntityRendererDispatcherClass = "net.minecraft.client.renderer.tileentity.TileEntityRendererDispatcher";
    static final String tileEntityRenderMethodDesc = "(Lnet/minecraft/tileentity/TileEntity;FI)V";
    static final ObfSafeName tileEntityRenderMethodName = new ObfSafeName("render", "func_180546_a");
    static final String coreMethodsClass = "mekanism/coremod/MekanismCoreMethods";
    static final String occlusionHookMethod = "shouldCullTileEntityForOcclusion";
    static final String occlusionHookDesc = "(Lnet/minecraft/tileentity/TileEntity;)Z";

    @Override
    public byte[] transform(String name, String transformedName, byte[] basicClass) {


        // Item Overlay Rendering hook
        if (renderItemClass.equals(transformedName)) {
            return transform(basicClass, renderItemClass, renderItemDisplayName, new Transform() {
                @Override
                void transform(Iterator<MethodNode> methods) {
                    int done = 0;
                    while (methods.hasNext()) {
                        MethodNode m = methods.next();
                        if (renderItemAndEffectIntoGUI.matches(m.name) && "(Lnet/minecraft/entity/EntityLivingBase;Lnet/minecraft/item/ItemStack;II)V".equals(m.desc)) {
                            InsnList toAdd = new InsnList();
                            toAdd.add(new VarInsnNode(ALOAD, 2));
                            toAdd.add(new VarInsnNode(ILOAD, 3));
                            toAdd.add(new VarInsnNode(ILOAD, 4));
                            toAdd.add(new MethodInsnNode(INVOKESTATIC, "mekanism/coremod/MekanismCoreMethods", "renderItemAndEffectIntoGUI",
                                    "(Lnet/minecraft/item/ItemStack;II)V", false));
                            m.instructions.insert(toAdd);
                            done++;
                        }
                        if (renderItemOverlayIntoGUIMethod.matches(m.name)) {

                            InsnList toAdd = new InsnList();
                            toAdd.add(new VarInsnNode(ALOAD, 2));
                            toAdd.add(new VarInsnNode(ILOAD, 3));
                            toAdd.add(new VarInsnNode(ILOAD, 4));
                            toAdd.add(new MethodInsnNode(INVOKESTATIC, "mekanism/coremod/MekanismCoreMethods", "renderItemOverlayIntoGUI",
                                    "(Lnet/minecraft/item/ItemStack;II)V", false));

                            boolean primed = false, onframe = false, applied = false;
                            Label target = null;
                            for (int i = 0; i < m.instructions.size(); i++) {
                                AbstractInsnNode next = m.instructions.get(i);

                                // (1) find "if (stack.getItem().showDurabilityBar(stack)) {"
                                if (!primed && target == null && next.getOpcode() == INVOKEVIRTUAL && next instanceof MethodInsnNode) {
                                    if ("showDurabilityBar".equals(((MethodInsnNode) next).name)) { // Forge method, never obf'ed
                                        primed = true;
                                    }
                                }

                                // (2) where is the matching "}"?
                                if (primed && next.getOpcode() == IFEQ && next instanceof JumpInsnNode) {
                                    target = ((JumpInsnNode) next).label.getLabel();
                                    primed = false;
                                }

                                // (3) insert our callback there
                                if (target != null && next instanceof LabelNode && ((LabelNode) next).getLabel() == target) {
                                    onframe = true;
                                    continue;
                                }
                                if (onframe && next instanceof FrameNode) {
                                    m.instructions.insert(next, toAdd);
                                    done++;
                                    applied = true;
                                    break;
                                }
                            }
                            if (!applied) {
                                mainLogger.info("Transforming failed. Applying ersatz patch...");
                                m.instructions.insert(toAdd);
                                mainLogger.warn("Ersatz patch applied, things may break!");
                                done++;
                            }
                            break;
                        }
                    }
                    if (done != 2) {
                        mainLogger.info("Transforming failed.");
                    }
                }
            });
        }
        if (tileEntityRendererDispatcherClass.equals(transformedName)) {
            return transform(basicClass, tileEntityRendererDispatcherClass, tileEntityRenderMethodName, new Transform() {
                @Override
                void transform(Iterator<MethodNode> methods) {
                    boolean transformed = false;
                    while (methods.hasNext()) {
                        MethodNode m = methods.next();
                        if (!tileEntityRenderMethodName.matches(m.name) || !tileEntityRenderMethodDesc.equals(m.desc)) {
                            continue;
                        }
                        if (containsOcclusionHook(m)) {
                            mainLogger.info("Occlusion culling hook already present in method: " + m.name + m.desc);
                            transformed = true;
                            break;
                        }

                        AbstractInsnNode first = m.instructions.getFirst();
                        if (first == null) {
                            break;
                        }

                        // Use a dedicated jump target label with an explicit frame.
                        // Jumping to the original first instruction can fail bytecode verification because
                        // that offset often has no explicit stack map frame.
                        LabelNode continueLabel = new LabelNode(new Label());
                        InsnList toAdd = new InsnList();
                        toAdd.add(new VarInsnNode(ALOAD, 1));
                        toAdd.add(new MethodInsnNode(INVOKESTATIC, coreMethodsClass, occlusionHookMethod, occlusionHookDesc, false));
                        toAdd.add(new JumpInsnNode(IFEQ, continueLabel));
                        toAdd.add(new InsnNode(RETURN));
                        toAdd.add(continueLabel);
                        toAdd.add(new FrameNode(F_SAME, 0, null, 0, null));
                        m.instructions.insertBefore(first, toAdd);
                        transformed = true;
                        mainLogger.info("Injected occlusion culling hook into " + m.name + m.desc);
                        break;
                    }
                    if (!transformed) {
                        mainLogger.warn("Failed to transform {} method {}.", tileEntityRendererDispatcherClass, tileEntityRenderMethodDesc);
                    }
                }
            });
        }
        return basicClass;

    }

    private static boolean containsOcclusionHook(MethodNode methodNode) {
        for (int i = 0; i < methodNode.instructions.size(); i++) {
            AbstractInsnNode next = methodNode.instructions.get(i);
            if (!(next instanceof MethodInsnNode)) {
                continue;
            }
            MethodInsnNode methodInsnNode = (MethodInsnNode) next;
            if (INVOKESTATIC == methodInsnNode.getOpcode() && coreMethodsClass.equals(methodInsnNode.owner)
                    && occlusionHookMethod.equals(methodInsnNode.name) && occlusionHookDesc.equals(methodInsnNode.desc)) {
                return true;
            }
        }
        return false;
    }


    protected final static byte[] transform(byte[] classBytes, String className, ObfSafeName methodName, Transform transformer) {
        mainLogger.info("Transforming Class [" + className + "], Method [" + methodName.getName() + "]");

        ClassNode classNode = new ClassNode();
        ClassReader classReader = new ClassReader(classBytes);
        classReader.accept(classNode, 0);

        Iterator<MethodNode> methods = classNode.methods.iterator();

        transformer.transform(methods);

        ClassWriter cw = new ClassWriter(0);
        classNode.accept(cw);
        mainLogger.info("Transforming " + className + " Finished.");
        return cw.toByteArray();
    }

}
