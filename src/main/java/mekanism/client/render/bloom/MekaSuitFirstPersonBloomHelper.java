package mekanism.client.render.bloom;

import mekanism.client.model.mekasuitarmour.IMekaSuitBloomModel;
import mekanism.client.model.mekasuitarmour.ModelMekAsuitBodyArm;
import mekanism.common.Mekanism;
import mekanism.common.item.armor.ItemMekaSuitArmor;
import mekanism.common.item.armor.ItemMekaSuitBodyArmor;
import mekanism.common.lib.Color;
import mekanism.common.util.BloomDependencyHelper;
import net.minecraft.client.entity.AbstractClientPlayer;
import net.minecraft.client.model.ModelBiped.ArmPose;
import net.minecraft.client.renderer.BufferBuilder;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.inventory.EntityEquipmentSlot;
import net.minecraft.item.ItemStack;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.nio.FloatBuffer;
import java.util.function.Consumer;

public final class MekaSuitFirstPersonBloomHelper {

    private static final float ARM_SCALE = 0.0625F;

    private MekaSuitFirstPersonBloomHelper() {
    }

    public static void requestArmBloom(AbstractClientPlayer player, boolean rightHand) {
        if (player == null || !Mekanism.hooks.Bloom || !IMekaSuitBloomModel.shouldUseBloom()) {
            return;
        }
        ItemStack chestStack = player.getItemStackFromSlot(EntityEquipmentSlot.CHEST);
        if (!(chestStack.getItem() instanceof ItemMekaSuitBodyArmor)) {
            return;
        }
        MatrixSnapshot snapshot = MatrixSnapshot.capture();
        if (!BloomBridge.requestCustomBloom(player, rightHand, snapshot)) {
            BloomDependencyHelper.disableBloom("MekaSuitFirstPersonBloomHelper");
        }
    }

    private static void renderArmBloom(AbstractClientPlayer player, boolean rightHand, MatrixSnapshot snapshot) {
        ItemStack chestStack = player.getItemStackFromSlot(EntityEquipmentSlot.CHEST);
        if (!(chestStack.getItem() instanceof ItemMekaSuitBodyArmor)) {
            return;
        }
        ModelMekAsuitBodyArm armor = ModelMekAsuitBodyArm.armorModel;
        armor.setVisible(true);
        armor.leftArmPose = ArmPose.EMPTY;
        armor.rightArmPose = ArmPose.EMPTY;
        armor.isSneak = false;
        armor.swingProgress = 0.0F;
        armor.setRotationAngles(0.0F, 0.0F, 0.0F, 0.0F, 0.0F, ARM_SCALE, player);
        snapshot.apply(() -> {
            Color color = getBloomTint(chestStack);
            GlStateManager.disableBlend();
            GlStateManager.color(color.rf(), color.gf(), color.bf(), 1.0F);
            if (rightHand) {
                armor.rightArmRenderBloom(ARM_SCALE);
            } else {
                armor.leftArmRenderBloom(ARM_SCALE);
            }
            GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
        });
    }

    private static Color getBloomTint(ItemStack chestStack) {
        Color color = ItemMekaSuitArmor.getColorModulation(chestStack);
        if (color == null) {
            return Color.WHITE;
        }
        double tintStrength = color.ad();
        double tintBase = 1.0D - tintStrength;
        return Color.rgbd(color.rd() * tintStrength + tintBase, color.gd() * tintStrength + tintBase, color.bd() * tintStrength + tintBase);
    }

    private static final class MatrixSnapshot {

        private final FloatBuffer modelView;
        private final FloatBuffer projection;

        private MatrixSnapshot(FloatBuffer modelView, FloatBuffer projection) {
            this.modelView = modelView;
            this.projection = projection;
        }

        private static MatrixSnapshot capture() {
            FloatBuffer modelView = BufferUtils.createFloatBuffer(16);
            FloatBuffer projection = BufferUtils.createFloatBuffer(16);
            GL11.glGetFloat(GL11.GL_MODELVIEW_MATRIX, modelView);
            GL11.glGetFloat(GL11.GL_PROJECTION_MATRIX, projection);
            modelView.rewind();
            projection.rewind();
            return new MatrixSnapshot(modelView, projection);
        }

        private void apply(Runnable renderer) {
            int previousMatrixMode = GL11.glGetInteger(GL11.GL_MATRIX_MODE);
            GL11.glMatrixMode(GL11.GL_PROJECTION);
            GL11.glPushMatrix();
            projection.rewind();
            GL11.glLoadMatrix(projection);
            GL11.glMatrixMode(GL11.GL_MODELVIEW);
            GL11.glPushMatrix();
            modelView.rewind();
            GL11.glLoadMatrix(modelView);
            renderer.run();
            GL11.glPopMatrix();
            GL11.glMatrixMode(GL11.GL_PROJECTION);
            GL11.glPopMatrix();
            GL11.glMatrixMode(previousMatrixMode);
        }
    }

    private static final class BloomBridge {

        private static final boolean AVAILABLE;
        private static final Method REQUEST_CUSTOM_BLOOM_METHOD;
        private static final Class<?> BLOOM_RENDER_FAST_CLASS;

        static {
            boolean available = false;
            Method requestCustomBloomMethod = null;
            Class<?> bloomRenderFastClass = null;
            try {
                ClassLoader classLoader = MekaSuitFirstPersonBloomHelper.class.getClassLoader();
                Class<?> bloomEffectUtilClass = Class.forName("gregtech.client.utils.BloomEffectUtil", false, classLoader);
                bloomRenderFastClass = Class.forName("gregtech.client.utils.BloomEffectUtil$IBloomRenderFast", false, classLoader);
                requestCustomBloomMethod = bloomEffectUtilClass.getMethod("requestCustomBloom", bloomRenderFastClass, Consumer.class);
                available = true;
            } catch (ReflectiveOperationException | LinkageError ignored) {
            }
            AVAILABLE = available;
            BLOOM_RENDER_FAST_CLASS = bloomRenderFastClass;
            REQUEST_CUSTOM_BLOOM_METHOD = requestCustomBloomMethod;
        }

        private BloomBridge() {
        }

        private static boolean requestCustomBloom(AbstractClientPlayer player, boolean rightHand, MatrixSnapshot snapshot) {
            if (!AVAILABLE) {
                return false;
            }
            try {
                Object renderSetup = Proxy.newProxyInstance(BLOOM_RENDER_FAST_CLASS.getClassLoader(), new Class<?>[]{BLOOM_RENDER_FAST_CLASS}, new BloomRenderSetupHandler());
                REQUEST_CUSTOM_BLOOM_METHOD.invoke(null, renderSetup, (Consumer<BufferBuilder>) bufferBuilder -> renderArmBloom(player, rightHand, snapshot));
                return true;
            } catch (ReflectiveOperationException | LinkageError e) {
                return false;
            }
        }
    }

    private static final class BloomRenderSetupHandler implements InvocationHandler {

        @Override
        public Object invoke(Object proxy, Method method, Object[] args) {
            String methodName = method.getName();
            if ("customBloomStyle".equals(methodName)) {
                return mekanism.common.config.MekanismConfig.current().client.customBloomStyle.val();
            } else if ("preDraw".equals(methodName) || "postDraw".equals(methodName)) {
                return null;
            } else if ("toString".equals(methodName)) {
                return "MekaSuitFirstPersonBloomHelper";
            } else if ("hashCode".equals(methodName)) {
                return System.identityHashCode(proxy);
            } else if ("equals".equals(methodName)) {
                return args != null && args.length > 0 && proxy == args[0];
            }
            return null;
        }
    }
}
