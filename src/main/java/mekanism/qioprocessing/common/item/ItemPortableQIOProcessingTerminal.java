package mekanism.qioprocessing.common.item;

import mekanism.api.qio.external.QIOFrequencyReference;
import mekanism.common.content.qio.QIOFrequency;
import mekanism.common.content.qio.QIOFrequencyStorageAccess;
import mekanism.common.frequency.Frequency.FrequencyIdentity;
import mekanism.common.frequency.FrequencyAware;
import mekanism.common.frequency.FrequencyType;
import mekanism.common.frequency.IFrequencyItem;
import mekanism.common.inventory.container.item.ItemStackSlotAccess;
import mekanism.common.item.ItemMekanism;
import mekanism.common.item.interfaces.IColoredItem;
import mekanism.common.security.ISecurityItem;
import mekanism.common.security.ISecurityTile.SecurityMode;
import mekanism.common.util.SecurityUtils;
import mekanism.qioprocessing.common.MekanismQIOProcessing;
import mekanism.qioprocessing.common.QIOProcessingCommonProxy;
import mekanism.qioprocessing.common.content.QIOProcessingDataException;
import mekanism.qioprocessing.common.terminal.PortableQIOProcessingTerminalData;
import mekanism.qioprocessing.common.terminal.QIOProcessingTerminalType;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.Entity;
import net.minecraft.item.EnumRarity;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ActionResult;
import net.minecraft.util.EnumActionResult;
import net.minecraft.util.EnumHand;
import net.minecraft.world.World;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.Objects;
import java.util.UUID;

/** Shared item implementation; each terminal responsibility is a distinct registered instance. */
/**
 * QIO 处理模块中的 ItemPortableQIOProcessingTerminal 类型。
 *
 * <p>该类型封装本层的数据、状态或服务职责；调用方应遵守其公开方法的输入约束，
 * 实现负责保持状态与持久化表示的一致。</p>
 */
public class ItemPortableQIOProcessingTerminal extends ItemMekanism
      implements IFrequencyItem, ISecurityItem, IColoredItem {

    private final QIOProcessingTerminalType terminalType;

    public ItemPortableQIOProcessingTerminal(
          @Nonnull QIOProcessingTerminalType terminalType) {
        this.terminalType = Objects.requireNonNull(terminalType, "terminalType");
        setMaxStackSize(1);
        setRarity(EnumRarity.RARE);
    }

    @Nonnull
    public QIOProcessingTerminalType getTerminalType() {
        return terminalType;
    }

    @Nonnull
    @Override
    public ActionResult<ItemStack> onItemRightClick(World world, EntityPlayer player,
          @Nonnull EnumHand hand) {
        ItemStack stack = player.getHeldItem(hand);
        if (!world.isRemote) {
            PortableQIOProcessingTerminalData data;
            try {
                data = PortableQIOProcessingTerminalData.read(stack);
                if (data == null) {
                    data = PortableQIOProcessingTerminalData.create(player.getUniqueID());
                    data.writeTo(stack);
                    player.inventory.markDirty();
                }
            } catch (QIOProcessingDataException e) {
                return new ActionResult<>(EnumActionResult.FAIL, stack);
            }
            if (!SecurityUtils.canAccess(player, stack)) {
                SecurityUtils.displayNoAccess(player);
                return new ActionResult<>(EnumActionResult.FAIL, stack);
            }
            int slot = ItemStackSlotAccess.getSlotForHand(player.inventory, hand);
            player.openGui(MekanismQIOProcessing.instance,
                  QIOProcessingCommonProxy.GUI_PORTABLE_TERMINAL, world, slot,
                  hand.ordinal(), 0);
        }
        return new ActionResult<>(EnumActionResult.SUCCESS, stack);
    }

    @Override
    public FrequencyType<?> getFrequencyType() {
        return FrequencyType.QIO;
    }

    @Nullable
    @Override
    public FrequencyIdentity getFrequency(ItemStack stack) {
        PortableQIOProcessingTerminalData data = readSafely(stack);
        QIOFrequencyReference reference = data == null ? null :
              data.getFrequencyReference();
        return reference == null ? null : new FrequencyIdentity(
              reference.getFrequencyName(), reference.getSecurityMode(),
              reference.getOwnerUUID());
    }

    @Override
    public FrequencyAware<?> getFrequencyAware(ItemStack stack) {
        FrequencyIdentity identity = getFrequency(stack);
        return identity == null ? FrequencyAware.none() : FrequencyAware.identity(identity);
    }

    /** Name-only writes are rejected; an exact resolved QIO frequency is required. */
    @Override
    public void setFrequencyAware(ItemStack stack,
          @Nullable FrequencyAware<?> frequencyAware) {
        if (frequencyAware == null || frequencyAware.identity() == null) {
            applyAuthorizedFrequency(stack, null);
        } else if (frequencyAware.frequency() instanceof QIOFrequency frequency) {
            applyAuthorizedFrequency(stack, frequency);
        }
    }

    public boolean applyAuthorizedFrequency(@Nonnull ItemStack stack,
          @Nullable QIOFrequency frequency) {
        PortableQIOProcessingTerminalData data = readSafely(stack);
        return data != null && applyAuthorizedFrequency(stack, frequency,
              data.getOwnerUUID());
    }

    public boolean applyAuthorizedFrequency(@Nonnull ItemStack stack,
          @Nullable QIOFrequency frequency, @Nonnull UUID bindingPlayerUUID) {
        PortableQIOProcessingTerminalData data = readSafely(stack);
        if (data == null) {
            return false;
        }
        QIOFrequencyReference reference = frequency == null ? null :
              QIOFrequencyStorageAccess.INSTANCE.createReference(frequency,
                    Objects.requireNonNull(bindingPlayerUUID, "bindingPlayerUUID"));
        PortableQIOProcessingTerminalData updated = data.withFrequency(reference);
        if (updated == data) {
            return false;
        }
        updated.writeTo(stack);
        setColor(stack, frequency == null ? null : frequency.getColor());
        return true;
    }

    @Override
    public void onUpdate(@Nonnull ItemStack stack, @Nonnull World world,
          @Nonnull Entity entity, int itemSlot, boolean isSelected) {
        super.onUpdate(stack, world, entity, itemSlot, isSelected);
        if (!world.isRemote && world.getTotalWorldTime() % 100 == 0) {
            syncColorWithFrequency(stack);
        }
    }

    @Nullable
    public QIOFrequencyReference getFrequencyReference(ItemStack stack) {
        PortableQIOProcessingTerminalData data = readSafely(stack);
        return data == null ? null : data.getFrequencyReference();
    }

    @Nullable
    @Override
    public UUID getOwnerUUID(ItemStack stack) {
        PortableQIOProcessingTerminalData data = readSafely(stack);
        return data == null ? null : data.getOwnerUUID();
    }

    @Override
    public void setOwnerUUID(ItemStack stack, UUID owner) {
        if (owner == null || stack == null || stack.isEmpty()) {
            return;
        }
        PortableQIOProcessingTerminalData data = readSafely(stack);
        PortableQIOProcessingTerminalData updated = data == null ?
              PortableQIOProcessingTerminalData.create(owner) : data.withOwner(owner);
        if (updated != data) {
            updated.writeTo(stack);
        }
    }

    @Override
    public boolean hasOwner(ItemStack stack) {
        return true;
    }

    @Override
    public SecurityMode getSecurity(ItemStack stack) {
        PortableQIOProcessingTerminalData data = readSafely(stack);
        return data == null ? SecurityMode.PRIVATE : data.getSecurityMode();
    }

    @Override
    public void setSecurity(ItemStack stack, SecurityMode mode) {
        PortableQIOProcessingTerminalData data = readSafely(stack);
        if (data != null && mode != null) {
            PortableQIOProcessingTerminalData updated = data.withSecurity(mode);
            if (updated != data) {
                updated.writeTo(stack);
            }
        }
    }

    @Override
    public boolean hasSecurity(ItemStack stack) {
        return true;
    }

    @Nullable
    private static PortableQIOProcessingTerminalData readSafely(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return null;
        }
        try {
            return PortableQIOProcessingTerminalData.read(stack);
        } catch (QIOProcessingDataException e) {
            return null;
        }
    }
}
