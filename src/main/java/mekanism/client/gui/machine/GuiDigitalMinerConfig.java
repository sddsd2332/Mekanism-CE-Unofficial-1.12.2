package mekanism.client.gui.machine;

import mekanism.client.gui.GuiFilterHolder;
import mekanism.api.Coord4D;
import mekanism.api.TileNetworkList;
import mekanism.client.gui.element.GuiDigitalSwitch;
import mekanism.client.gui.element.GuiDigitalSwitch.SwitchType;
import mekanism.client.gui.element.button.FilterButton;
import mekanism.client.gui.element.button.MekanismImageButton;
import mekanism.client.gui.element.button.TooltipToggleButton;
import mekanism.client.gui.element.button.TranslationButton;
import mekanism.client.gui.element.slot.GuiSlot;
import mekanism.client.gui.element.slot.SlotType;
import mekanism.client.gui.element.text.GuiTextField;
import mekanism.client.gui.element.window.filter.miner.GuiMinerFilterSelect;
import mekanism.client.recipe_viewer.interfaces.IRecipeViewerGhostTarget.IGhostBlockItemConsumer;
import mekanism.common.Mekanism;
import mekanism.common.MekanismLang;
import mekanism.common.OreDictCache;
import mekanism.common.config.MekanismConfig;
import mekanism.common.content.filter.IFilter;
import mekanism.common.content.miner.*;
import mekanism.common.inventory.container.ContainerDigitalMinerConfig;
import mekanism.common.inventory.warning.WarningTracker.WarningType;
import mekanism.common.network.PacketDigitalMinerGui.DigitalMinerGuiMessage;
import mekanism.common.network.PacketDigitalMinerGui.MinerGuiPacket;
import mekanism.common.network.PacketTileEntity.TileEntityMessage;
import mekanism.common.tile.machine.TileEntityDigitalMiner;
import mekanism.common.util.LangUtils;
import mekanism.common.util.MekanismUtils;
import mekanism.common.util.StackUtils;
import net.minecraft.block.Block;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.Blocks;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.text.TextComponentString;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;
import org.lwjgl.input.Keyboard;

import java.util.List;
import java.util.function.IntConsumer;

@SideOnly(Side.CLIENT)
public class GuiDigitalMinerConfig extends GuiFilterHolder<MinerFilter, TileEntityDigitalMiner, ContainerDigitalMinerConfig> {

    private static final ResourceLocation INVERSE = MekanismUtils.getResource(MekanismUtils.ResourceType.GUI, "switch/inverse.png");

    private GuiTextField radiusField;
    private GuiTextField minField;
    private GuiTextField maxField;

    public GuiDigitalMinerConfig(EntityPlayer player, TileEntityDigitalMiner tile) {
        super(tile, new ContainerDigitalMinerConfig(player.inventory, tile));
    }

    @Override
    protected void addGuiElements() {
        super.addGuiElements();
        addButton(new TranslationButton(this, 96, 136, 156, 20, MekanismLang.BUTTON_NEW_FILTER,
              () -> addWindow(new GuiMinerFilterSelect(this, tileEntity))));
        addButton(new MekanismImageButton(this, 5, 5, 11, 14, getButtonLocation("back"),
              () -> sendGuiPacket(MinerGuiPacket.SERVER, 4, 0), getOnHover(() -> new TextComponentString(LangUtils.localize("gui.back")))));
        addButton(new GuiDigitalSwitch(this, 10, 115, INVERSE, () -> tileEntity.inverse,
              () -> Mekanism.packetHandler.sendToServer(new TileEntityMessage(tileEntity, TileNetworkList.withContents(10))),
              null, SwitchType.LEFT_ICON).setTooltip(MekanismLang.MINER_INVERSE.translate()));
        addButton(new GuiSlot(SlotType.NORMAL, this, 13, 135).setRenderAboveSlots().setRenderHover(true)
              .stored(() -> tileEntity.getInverseReplaceTarget()).click((element, mouseX, mouseY) -> {
                  ItemStack stack = Keyboard.isKeyDown(Keyboard.KEY_LSHIFT) ? ItemStack.EMPTY : mc.player.inventory.getItemStack();
                  if (!stack.isEmpty() && Block.getBlockFromItem(stack.getItem()) == Blocks.AIR) {
                      return false;
                  }
                  setInverseReplaceTarget(stack);
                  return true;
              }).setGhostHandler(new IGhostBlockItemConsumer() {
                  @Override
                  public ItemStack supportedTarget(Object ingredient) {
                      ItemStack stack = IGhostBlockItemConsumer.super.supportedTarget(ingredient);
                      return stack != null && Block.getBlockFromItem(stack.getItem()) != Blocks.AIR &&
                            Block.getBlockFromItem(stack.getItem()) != Blocks.BEDROCK ? stack : null;
                  }

                  @Override
                  public void accept(Object ingredient) {
                      setInverseReplaceTarget(StackUtils.size((ItemStack) ingredient, 1));
                  }
              }));
        addButton(new TooltipToggleButton(this, 35, 137, 14, 16, getButtonLocation("exclamation"), tileEntity::getInverseRequiresReplacement,
              () -> Mekanism.packetHandler.sendToServer(new TileEntityMessage(tileEntity, TileNetworkList.withContents(17))),
              MekanismLang.MINER_REQUIRE_REPLACE_INVERSE.translate(MekanismLang.YES.translate()),
              MekanismLang.MINER_REQUIRE_REPLACE_INVERSE.translate(MekanismLang.NO.translate())));

        int lengthY = Math.max(Integer.toString(tileEntity.getMinimumYLimit()).length(), Integer.toString(tileEntity.getMaximumYLimit()).length());
        radiusField = addButton(new GuiTextField(this, 1, 13, 45, 38, 11)
              .setMaxLength(Integer.toString(MekanismConfig.current().general.digitalMinerMaxRadius.val()).length())
              .setInputValidator(this::isDigitOrTextKey)
              .configureDigitalBorderInput(this::setRadius));
        minField = addButton(new GuiTextField(this, 2, 13, 71, 38, 11)
              .setMaxLength(lengthY)
              .setInputValidator(this::isSignedDigitOrTextKey)
              .configureDigitalBorderInput(this::setMinY));
        maxField = addButton(new GuiTextField(this, 3, 13, 98, 38, 11)
              .setMaxLength(lengthY)
              .setInputValidator(this::isSignedDigitOrTextKey)
              .configureDigitalBorderInput(this::setMaxY));
        trackWarning(WarningType.FILTER_HAS_BLACKLISTED_ELEMENT, () -> tileEntity.getFilterManager().anyEnabledMatch(MinerFilter::hasBlacklistedElement));
    }

    private boolean isDigitOrTextKey(char c, int keyCode) {
        return Character.isDigit(c) || isTextboxKey(keyCode);
    }

    private boolean isSignedDigitOrTextKey(char c, int keyCode) {
        return Character.isDigit(c) || keyCode == Keyboard.KEY_MINUS || keyCode == Keyboard.KEY_SUBTRACT || isTextboxKey(keyCode);
    }

    private boolean isTextboxKey(int keyCode) {
        return keyCode == Keyboard.KEY_BACK || keyCode == Keyboard.KEY_DELETE || keyCode == Keyboard.KEY_LEFT || keyCode == Keyboard.KEY_RIGHT ||
              keyCode == Keyboard.KEY_HOME || keyCode == Keyboard.KEY_END || keyCode == Keyboard.KEY_A || keyCode == Keyboard.KEY_C ||
              keyCode == Keyboard.KEY_V || keyCode == Keyboard.KEY_X;
    }

    private void sendGuiPacket(MinerGuiPacket type, int guiID, int extra) {
        Mekanism.packetHandler.sendToServer(new DigitalMinerGuiMessage(type, Coord4D.get(tileEntity), guiID, extra, 0));
    }

    private void setInverseReplaceTarget(ItemStack stack) {
        Mekanism.packetHandler.sendToServer(new TileEntityMessage(tileEntity, TileNetworkList.withContents(16, stack.isEmpty() ? ItemStack.EMPTY : stack.copy())));
    }

    private void setRadius() {
        if (!radiusField.isEmpty()) {
            try {
                int toUse = Math.max(0, Math.min(Integer.parseInt(radiusField.getText()), MekanismConfig.current().general.digitalMinerMaxRadius.val()));
                Mekanism.packetHandler.sendToServer(new TileEntityMessage(tileEntity, TileNetworkList.withContents(6, toUse)));
            } catch (NumberFormatException ignored) {
            }
            radiusField.clear();
        }
    }

    private void setMinY() {
        if (!minField.isEmpty()) {
            try {
                Mekanism.packetHandler.sendToServer(new TileEntityMessage(tileEntity, TileNetworkList.withContents(7, Integer.parseInt(minField.getText()))));
            } catch (NumberFormatException ignored) {
            }
            minField.clear();
        }
    }

    private void setMaxY() {
        if (!maxField.isEmpty()) {
            try {
                Mekanism.packetHandler.sendToServer(new TileEntityMessage(tileEntity, TileNetworkList.withContents(8, Integer.parseInt(maxField.getText()))));
            } catch (NumberFormatException ignored) {
            }
            maxField.clear();
        }
    }

    @Override
    protected void drawForegroundText(int mouseX, int mouseY) {
        super.drawForegroundText(mouseX, mouseY);
        drawTitleTextWithOffset(MekanismLang.MINER_CONFIG.translate(), 14, 4, getXSize());
        drawScreenText(MekanismLang.FILTER_COUNT.translate(getFilterManager().count()), 5);
        drawScreenText(MekanismLang.MINER_RADIUS.translate(tileEntity.getRadius()), 18);
        drawScreenText(MekanismLang.MIN_DIGITAL_MINER.translate(tileEntity.minY), 44);
        drawScreenText(MekanismLang.MAX_DIGITAL_MINER.translate(tileEntity.maxY), 71);
    }

    @Override
    protected void addGenericTabs() {
        // The miner config screen is a focused filter/config editor; high versions do not add the normal machine tabs here.
    }

    @Override
    protected FilterButton addFilterButton(FilterButton button) {
        return super.addFilterButton(button).warning(WarningType.FILTER_HAS_BLACKLISTED_ELEMENT,
              filter -> filter instanceof MinerFilter minerFilter && filter.isEnabled() && minerFilter.hasBlacklistedElement());
    }

    @Override
    protected void onClick(IFilter filter, int index) {
        if (filter instanceof MItemStackFilter itemFilter) {
            addWindow(mekanism.client.gui.element.window.filter.miner.GuiMinerItemStackFilter.edit(this, tileEntity, itemFilter));
        } else if (filter instanceof MOreDictFilter oreFilter) {
            addWindow(mekanism.client.gui.element.window.filter.miner.GuiMinerOreDictFilter.edit(this, tileEntity, oreFilter));
        } else if (filter instanceof MMaterialFilter materialFilter) {
            addWindow(mekanism.client.gui.element.window.filter.miner.GuiMinerMaterialFilter.edit(this, tileEntity, materialFilter));
        } else if (filter instanceof MModIDFilter modIDFilter) {
            addWindow(mekanism.client.gui.element.window.filter.miner.GuiMinerModIDFilter.edit(this, tileEntity, modIDFilter));
        }
    }

    @Override
    protected IntConsumer getMoveUpSender() {
        return index -> Mekanism.packetHandler.sendToServer(new TileEntityMessage(tileEntity, TileNetworkList.withContents(Keyboard.isKeyDown(Keyboard.KEY_LSHIFT) ? 14 : 11, index)));
    }

    @Override
    protected IntConsumer getMoveDownSender() {
        return index -> Mekanism.packetHandler.sendToServer(new TileEntityMessage(tileEntity, TileNetworkList.withContents(Keyboard.isKeyDown(Keyboard.KEY_LSHIFT) ? 15 : 12, index)));
    }

    @Override
    protected IntConsumer getToggleSender() {
        return index -> Mekanism.packetHandler.sendToServer(new TileEntityMessage(tileEntity, TileNetworkList.withContents(13, index)));
    }

    @Override
    protected List<ItemStack> getOreDictStacks(String oreName) {
        return OreDictCache.getOreDictStacks(oreName, true);
    }

    @Override
    protected List<ItemStack> getModIDStacks(String modID) {
        return OreDictCache.getModIDStacks(modID, true);
    }
}