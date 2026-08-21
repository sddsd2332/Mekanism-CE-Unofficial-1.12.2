package mekanism.common.network;

import io.netty.buffer.ByteBuf;
import mekanism.common.Mekanism;
import mekanism.common.base.IModule;
import mekanism.common.config.MekanismConfig;
import mekanism.common.network.PacketConfigSync.ConfigSyncMessage;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.HashMap;
import java.util.Map;

public class PacketConfigSync implements IMessageHandler<ConfigSyncMessage, IMessage> {

    private static final Logger LOGGER = LogManager.getLogger("Mekanism-PacketConfigSync");
    private static final int MAX_SYNCED_MODULES = 64;
    private static final int MAX_MODULE_CONFIG_BYTES = 1_048_576;

    @Override
    public IMessage onMessage(ConfigSyncMessage message, MessageContext context) {
        MekanismConfig.setSyncedConfig(message.config);
        Mekanism.proxy.onConfigSync(true);
        return null;
    }

    public static class ConfigSyncMessage implements IMessage {

        private MekanismConfig config;

        public ConfigSyncMessage() {
            config = new MekanismConfig();
            config.client = null;
        }

        public ConfigSyncMessage(MekanismConfig config) {
            this.config = config;
        }

        @Override
        public void toBytes(ByteBuf dataStream) {
            config.general.write(dataStream);
            config.mekce.write(dataStream);
            config.usage.write(dataStream);
            config.storage.write(dataStream);
            int countIndex = dataStream.writerIndex();
            dataStream.writeInt(0);
            int writtenModules = 0;
            for (IModule module : Mekanism.modulesLoaded) {
                ByteBuf moduleData = dataStream.alloc().buffer();
                try {
                    module.writeConfig(moduleData, config);
                    int length = moduleData.readableBytes();
                    if (length > MAX_MODULE_CONFIG_BYTES) {
                        LOGGER.error("Skipping oversized config sync data for module {} ({} bytes)", module.getName(), length);
                        continue;
                    }
                    mekanism.common.PacketHandler.writeString(dataStream, module.getName());
                    dataStream.writeInt(length);
                    dataStream.writeBytes(moduleData, moduleData.readerIndex(), length);
                    writtenModules++;
                } catch (Exception e) {
                    LOGGER.error("Failed to serialize config for module {}", module.getName(), e);
                } finally {
                    moduleData.release();
                }
            }
            dataStream.setInt(countIndex, writtenModules);
        }

        @Override
        public void fromBytes(ByteBuf dataStream) {
            config.general.read(dataStream);
            config.mekce.read(dataStream);
            config.usage.read(dataStream);
            config.storage.read(dataStream);
            int moduleCount = dataStream.readInt();
            if (moduleCount < 0 || moduleCount > MAX_SYNCED_MODULES) {
                throw new IllegalArgumentException("Invalid config sync module count: " + moduleCount);
            }
            Map<String, IModule> localModules = new HashMap<>();
            for (IModule module : Mekanism.modulesLoaded) {
                localModules.put(module.getName(), module);
            }
            for (int i = 0; i < moduleCount; i++) {
                String moduleName = mekanism.common.PacketHandler.readString(dataStream);
                int length = dataStream.readInt();
                if (length < 0 || length > MAX_MODULE_CONFIG_BYTES || length > dataStream.readableBytes()) {
                    throw new IllegalArgumentException("Invalid config sync payload length for module " + moduleName + ": " + length);
                }
                ByteBuf moduleData = dataStream.readSlice(length);
                IModule module = localModules.get(moduleName);
                if (module != null) {
                    try {
                        module.readConfig(moduleData, config);
                    } catch (Exception e) {
                        LOGGER.error("Failed to deserialize config for module {}", moduleName, e);
                    }
                }
            }
        }
    }
}
