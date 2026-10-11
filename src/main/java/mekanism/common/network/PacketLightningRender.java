package mekanism.common.network;

import io.netty.buffer.ByteBuf;
import mekanism.client.render.RenderTickHandler;
import mekanism.common.PacketHandler;
import mekanism.common.config.MekanismConfig;
import mekanism.common.lib.effect.BoltEffect;
import mekanism.common.lib.effect.BoltEffect.BoltRenderInfo;
import mekanism.common.lib.effect.BoltEffect.SpawnFunction;
import mekanism.common.network.PacketLightningRender.LightningRenderMessage;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.math.Vec3d;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;

import java.util.function.BooleanSupplier;

public class PacketLightningRender implements IMessageHandler<LightningRenderMessage, IMessage> {

    @Override
    public IMessage onMessage(LightningRenderMessage message, MessageContext context) {
        EntityPlayer player = PacketHandler.getPlayer(context);
        if (player == null || message.preset == null) {
            return null;
        }
        PacketHandler.handlePacket(() -> {
            if (message.preset.shouldAdd.getAsBoolean()) {
                RenderTickHandler.renderBolt(message.renderer, message.preset.boltCreator.create(message.start, message.end, message.segments));
            }
        }, player);
        return null;
    }

    @FunctionalInterface
    public interface BoltCreator {

        BoltEffect create(Vec3d start, Vec3d end, int segments);
    }

    public enum LightningPreset {
        TOOL_AOE(() -> MekanismConfig.current().client.renderToolAOEParticles.val(), (start, end, segments) ->
                new BoltEffect(BoltRenderInfo.ELECTRICITY, start, end, segments).size(0.015F).lifespan(12).spawn(SpawnFunction.NO_DELAY));

        private final BooleanSupplier shouldAdd;
        private final BoltCreator boltCreator;

        LightningPreset(BooleanSupplier shouldAdd, BoltCreator boltCreator) {
            this.shouldAdd = shouldAdd;
            this.boltCreator = boltCreator;
        }
    }

    public static class LightningRenderMessage implements IMessage {

        public LightningPreset preset;
        public int renderer;
        public Vec3d start = Vec3d.ZERO;
        public Vec3d end = Vec3d.ZERO;
        public int segments;

        public LightningRenderMessage() {
        }

        public LightningRenderMessage(LightningPreset preset, int renderer, Vec3d start, Vec3d end, int segments) {
            this.preset = preset;
            this.renderer = renderer;
            this.start = start;
            this.end = end;
            this.segments = segments;
        }

        @Override
        public void toBytes(ByteBuf dataStream) {
            dataStream.writeByte((preset == null ? LightningPreset.TOOL_AOE : preset).ordinal());
            dataStream.writeInt(renderer);
            dataStream.writeDouble(start.x);
            dataStream.writeDouble(start.y);
            dataStream.writeDouble(start.z);
            dataStream.writeDouble(end.x);
            dataStream.writeDouble(end.y);
            dataStream.writeDouble(end.z);
            dataStream.writeInt(segments);
        }

        @Override
        public void fromBytes(ByteBuf dataStream) {
            int ordinal = dataStream.readUnsignedByte();
            LightningPreset[] presets = LightningPreset.values();
            preset = ordinal < presets.length ? presets[ordinal] : LightningPreset.TOOL_AOE;
            renderer = dataStream.readInt();
            start = new Vec3d(dataStream.readDouble(), dataStream.readDouble(), dataStream.readDouble());
            end = new Vec3d(dataStream.readDouble(), dataStream.readDouble(), dataStream.readDouble());
            segments = dataStream.readInt();
        }
    }
}
