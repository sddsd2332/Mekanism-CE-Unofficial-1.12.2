package mekanism.client.gui;

import mekanism.api.TileNetworkList;
import mekanism.client.gui.button.GuiDisableableButton;
import mekanism.client.gui.element.*;
import mekanism.client.gui.element.GuiProgress.IProgressInfoHandler;
import mekanism.client.gui.element.GuiProgress.ProgressBar;
import mekanism.client.gui.element.GuiSlot.ISlotInfoHandler;
import mekanism.client.gui.element.bar.GuiBar;
import mekanism.client.gui.element.gauge.GuiFluidGauge;
import mekanism.client.gui.element.gauge.GuiGasGauge;
import mekanism.client.gui.element.gauge.GuiGauge.Type;
import mekanism.client.gui.element.gauge.GuiGauge.TypeColor;
import mekanism.client.gui.element.slot.GuiEnergySlot;
import mekanism.client.gui.element.slot.GuiExtraSlot;
import mekanism.client.gui.element.slot.GuiInputSlot;
import mekanism.client.gui.element.slot.GuiOutputSlot;
import mekanism.client.gui.element.tab.*;
import mekanism.client.sound.SoundHandler;
import mekanism.common.Mekanism;
import mekanism.common.base.IFactory.MachineFuelType;
import mekanism.common.base.IFactory.RecipeType;
import mekanism.common.config.MekanismConfig;
import mekanism.common.inventory.container.ContainerFactory;
import mekanism.common.item.ItemGaugeDropper;
import mekanism.common.network.PacketTileEntity.TileEntityMessage;
import mekanism.common.recipe.RecipeHandler;
import mekanism.common.recipe.machines.NucleosynthesizerRecipe;
import mekanism.common.recipe.machines.PressurizedRecipe;
import mekanism.common.tier.FactoryTier;
import mekanism.common.tile.factory.TileEntityFactory;
import mekanism.common.util.LangUtils;
import mekanism.common.util.MekanismUtils;
import mekanism.common.util.MekanismUtils.ResourceType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.init.SoundEvents;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;
import org.lwjgl.input.Keyboard;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;


@SideOnly(Side.CLIENT)
public class GuiFactory extends GuiMekanismTile<TileEntityFactory> implements IJeiFactoryRecipe {

    private GuiButton infuserDumpButton = null;
    private GuiButton FactoryOldSortingButton;
    private final InventoryPlayer inventory;
    private RecipeType displayedRecipeType;

    public GuiFactory(InventoryPlayer inventory, TileEntityFactory tile) {
        super(tile, new ContainerFactory(inventory, tile));
        this.inventory = inventory;
        displayedRecipeType = tileEntity.getRecipeType();
        addFactoryGuiElements();
    }

    private void addFactoryGuiElements() {
        ResourceLocation resource = getGuiLocation();
        int ymove = 0;
        if (tileEntity.OuputItemSecondaryMachine()) {
            ymove += 21;
        }
        if (tileEntity.getRecipeType() == RecipeType.PRC) {
            //输出气体
            ymove += 21;
            //输入流体
            ymove += 21;
        }
        if (tileEntity.getRecipeType() == RecipeType.INFUSING) {
            ymove += 10;
        }
        int xmove = tileEntity.tier == FactoryTier.CREATIVE ? 72 : tileEntity.tier == FactoryTier.ULTIMATE ? 34 : 0;


        xSize += xmove;
        ySize += ymove;


        //gui左边tab
        addGuiElement(new GuiSideConfigurationTab(this, tileEntity, resource));
        addGuiElement(new GuiTransporterConfigTab(this, 32, tileEntity, resource));
        addGuiElement(new GuiSortingTab(this, tileEntity, resource));
        addGuiElement(new GuiEnergyInfo(() -> {
            String multiplier = MekanismUtils.getEnergyDisplay(tileEntity.energyPerTick);
            double extra;
            for (int i = 0; i < tileEntity.tier.processes; i++) {
                if (tileEntity.getRecipeType() == RecipeType.PRC) {
                    PressurizedRecipe PRCrecipe = tileEntity.getRecipeType().getPressurizedRecipe(tileEntity.inventory.get(tileEntity.getInputSlot(i)), tileEntity.fluidTank.getFluid(), tileEntity.gasTank.getGas());
                    extra = PRCrecipe != null ? PRCrecipe.extraEnergy : 0;
                    if (tileEntity.progress[i] != 0) {
                        int cacheIndex = i + 1; //Fixed 1 recipe energy missing
                        multiplier = MekanismUtils.getEnergyDisplay(MekanismUtils.getEnergyPerTick(tileEntity, (tileEntity.BASE_ENERGY_PER_TICK + extra) * cacheIndex));
                    }
                } else if (tileEntity.getRecipeType() == RecipeType.NUCLEOSYNTHESIZER) {
                    NucleosynthesizerRecipe NnRecipe = tileEntity.getRecipeType().getNucleosynthesizerRecipe(tileEntity.inventory.get(tileEntity.getInputSlot(i)), tileEntity.gasTank.getGas());
                    extra = NnRecipe != null ? NnRecipe.extraEnergy : 0;
                    if (tileEntity.progress[i] != 0) {
                        int cacheIndex = i + 1; //Fixed 1 recipe energy missing
                        multiplier = MekanismUtils.getEnergyDisplay(MekanismUtils.getEnergyPerTick(tileEntity, (tileEntity.BASE_ENERGY_PER_TICK + extra) * cacheIndex));
                    }
                }
            }
            return Arrays.asList(LangUtils.localize("gui.using") + ": " + multiplier + "/t", LangUtils.localize("gui.needed") + ": " + MekanismUtils.getEnergyDisplay(tileEntity.getNeedEnergy()));
        }, this, resource));
        //gui右边tab
        addGuiElement(new GuiRecipeType(this, tileEntity, resource, xmove, 0));
        addGuiElement(new GuiUpgradeTab(this, tileEntity, resource, xmove, 0));
        addGuiElement(new GuiSecurityTab(this, tileEntity, resource, xmove, 0));
        addGuiElement(new GuiRedstoneControl(this, tileEntity, resource, xmove, 0));

        int Slotlocation = tileEntity.tier == FactoryTier.BASIC ? 54 : tileEntity.tier == FactoryTier.ADVANCED ? 34 : tileEntity.tier == FactoryTier.ELITE ? 28 : 26;
        //输入/输出插槽间距
        int xDistance = tileEntity.tier == FactoryTier.BASIC ? 38 : tileEntity.tier == FactoryTier.ADVANCED ? 26 : 19;

        int xOffset = tileEntity.tier == FactoryTier.BASIC ? 57 : tileEntity.tier == FactoryTier.ADVANCED ? 37 : tileEntity.tier == FactoryTier.ELITE ? 31 : 29;

        //gui内部
        //能量插槽
        addGuiElement(new GuiEnergySlot(this, resource, 6, 12, tileEntity));

        //能量条
        if (tileEntity.OuputItemSecondaryMachine() || tileEntity.getRecipeType() == RecipeType.PRC) {
            addGuiElement(new GuiPowerBarLong(this, tileEntity, resource, 164 + xmove, 15));
        } else {
            addGuiElement(new GuiPowerBar(this, tileEntity, resource, 164 + xmove, 15));
        }

        //额外插槽
        if (tileEntity.getRecipeType().getFuelType() == MachineFuelType.DOUBLE || tileEntity.getRecipeType() == RecipeType.INFUSING || tileEntity.GasInputMachine()) {
            if (tileEntity.getRecipeType().getFuelType() == MachineFuelType.FARM) {
                addGuiElement(new GuiExtraSlot(this, resource, 6, 68, tileEntity));
            } else if (tileEntity.getRecipeType() == RecipeType.PRC) {
                addGuiElement(new GuiExtraSlot(this, resource, 6, 98, tileEntity));
            } else {
                addGuiElement(new GuiExtraSlot(this, resource, 6, 56, tileEntity));
            }
        }

        //输入/输出插槽位置

        for (int i = 0; i < tileEntity.tier.processes; i++) {
            //输入物品插槽
            if (!tileEntity.NoItemInputMachine()) {
                int finalI = i;
                addGuiElement(new GuiInputSlot(this, resource, Slotlocation + (i * xDistance), 12, new ISlotInfoHandler() {
                    @Override
                    public boolean getSlotCanTip() {
                        return tileEntity.inventory.get(tileEntity.getInputSlot(finalI)).isEmpty();
                    }
                }));
            }
            //输出物品插槽
            if (tileEntity.OuputItemMachine()) {
                int finalI = i;
                addGuiElement(new GuiOutputSlot(this, resource, Slotlocation + (i * xDistance), 56, new ISlotInfoHandler() {
                    @Override
                    public boolean getSlotCanTip() {
                        return tileEntity.inventory.get(tileEntity.getOutputSlot(finalI)).isEmpty();
                    }
                }));
            }

            //输出次要物品插槽
            if (tileEntity.OuputItemSecondaryMachine()) {
                int finalI = i;
                addGuiElement(new GuiOutputSlot(this, resource, Slotlocation + (i * xDistance), 77, new ISlotInfoHandler() {
                    @Override
                    public boolean getSlotCanTip() {
                        return tileEntity.inventory.get(tileEntity.getSecondaryOutputSlot(finalI)).isEmpty();
                    }
                }));
            }

            //工作进度条
            int cacheIndex = i;
            int xPos = xOffset + (i * xDistance);
            addGuiElement(new GuiProgress(new IProgressInfoHandler() {
                @Override
                public double getProgress() {
                    return tileEntity.getScaledProgress(cacheIndex);
                }
            }, ProgressBar.DOWN, this, resource, xPos, 33));
        }

        int short_X_Tank = tileEntity.tier == FactoryTier.CREATIVE ? 212 : tileEntity.tier == FactoryTier.ULTIMATE ? 174 : 140;
        //添加灌注条
        if (tileEntity.getRecipeType() == RecipeType.INFUSING) {
            addGuiElement(new GuiBar(this, getGuiLocation(), 7, 77, short_X_Tank, 7));
        }

        Type type = tileEntity.tier == FactoryTier.BASIC ? Type.SLOT_BASIC : tileEntity.tier == FactoryTier.ADVANCED ? Type.SLOT_ADVANCED : tileEntity.tier == FactoryTier.ELITE ? Type.SLOT_ELITE : tileEntity.tier == FactoryTier.ULTIMATE ? Type.SLOT_ULTIMATE : Type.SLOT_CREATIVE;
        //输入气体储罐显示
        if (tileEntity.GasInputMachine()) {
            if (tileEntity.getRecipeType().getFuelType() == MachineFuelType.FARM) {
                addGuiElement(new GuiGasGauge(() -> tileEntity.gasTank, Type.SMALL, this, resource, 6, 34).withColor(TypeColor.YELLOW));
            } else if (tileEntity.getRecipeType() == RecipeType.Crystallizer || tileEntity.getRecipeType() == RecipeType.WASHER) {
                addGuiElement(new GuiGasGauge(() -> tileEntity.gasTank, type, this, resource, Slotlocation, 12).withColor(TypeColor.RED));
            } else if (tileEntity.getRecipeType() == RecipeType.PRC) {
                addGuiElement(new GuiGasGauge(() -> tileEntity.gasTank, Type.STANDARD, this, resource, 6, 34).withColor(TypeColor.YELLOW));
            } else {
                addGuiElement(new GuiGasGauge(() -> tileEntity.gasTank, Type.SLOT, this, resource, 6, 34).withColor(TypeColor.YELLOW));
            }
        }

        //输出气体储罐显示
        if (tileEntity.GasOutputMachine()) {
            int y = tileEntity.getRecipeType() == RecipeType.PRC ? 77 : 56;
            addGuiElement(new GuiGasGauge(() -> tileEntity.gasOutTank, type, this, resource, Slotlocation, y).withColor(TypeColor.BLUE));
        }
        //流体储罐
        if (tileEntity.inputFluidMachine()) {
            if (tileEntity.getRecipeType() == RecipeType.PRC) {
                addGuiElement(new GuiFluidGauge(() -> tileEntity.fluidTank, type, this, resource, Slotlocation, 98).withColor(TypeColor.RED));
            } else {
                addGuiElement(new GuiFluidGauge(() -> tileEntity.fluidTank, Type.SLOT, this, resource, 6, 34).withColor(TypeColor.YELLOW));
            }

        }
        int xPlayerOffset = tileEntity.tier == FactoryTier.CREATIVE ? 36 : tileEntity.tier == FactoryTier.ULTIMATE ? 19 : 0;
        //玩家插槽
        addGuiElement(new GuiPlayerSlot(this, resource, 7 + xPlayerOffset, 83 + ymove));
    }

    private void rebuildFactoryGui() {
        int windowId = inventorySlots.windowId;
        xSize = 176;
        ySize = 166;
        getGuiElements().clear();
        buttonList.clear();
        inventorySlots = new ContainerFactory(inventory, tileEntity);
        inventorySlots.windowId = windowId;
        addFactoryGuiElements();
        initGui();
    }

    @Override
    public void updateScreen() {
        super.updateScreen();
        if (displayedRecipeType != tileEntity.getRecipeType()) {
            displayedRecipeType = tileEntity.getRecipeType();
            rebuildFactoryGui();
        }
    }

    @Override
    public boolean getJeiRecipe(int mouseX, int mouseY) {
        int xAxis = mouseX - guiLeft;
        int yAxis = mouseY - guiTop;
        int xDistance = tileEntity.tier == FactoryTier.BASIC ? 38 : tileEntity.tier == FactoryTier.ADVANCED ? 26 : 19;
        int xOffset = tileEntity.tier == FactoryTier.BASIC ? 57 : tileEntity.tier == FactoryTier.ADVANCED ? 37 : tileEntity.tier == FactoryTier.ELITE ? 31 : 29;
        for (int i = 0; i < tileEntity.tier.processes; i++) {
            int xPos = xOffset + (i * xDistance);
            if (xAxis >= xPos && xAxis <= xPos + 12 && yAxis >= 33 && yAxis <= 33 + 22) {
                return true;
            }
        }
        return false;
    }

    public static final String SMELTING = "minecraft.smelting";

    @Override
    public List<String> getRecipe() {
        if (tileEntity.getRecipeType() == RecipeType.SMELTING) {
            return Arrays.asList(SMELTING, RecipeHandler.Recipe.ENERGIZED_SMELTER.getJEICategory());
        } else if (tileEntity.getRecipeType() == RecipeType.RECYCLER) {
            if (MekanismConfig.current().mekce.EnableRecyclerRecipeInJei.val()) {
                return Collections.singletonList(RecipeHandler.Recipe.RECYCLER.getJEICategory());
            }
        } else {
            return Collections.singletonList(tileEntity.getRecipeType().getrecipe().getJEICategory());
        }
        return new ArrayList<>();
    }

    @Override
    public void initGui() {
        super.initGui();
        int left = tileEntity.tier == FactoryTier.CREATIVE ? 220 : tileEntity.tier == FactoryTier.ULTIMATE ? 182 : 148;
        this.buttonList.add(infuserDumpButton = new GuiDisableableButton(1, guiLeft + left, guiTop + 77, 21, 10) {
            @Override
            public void drawButton(Minecraft mc, int mouseX, int mouseY, float partialTicks) {
                if (tileEntity.getRecipeType() == RecipeType.INFUSING) {
                    super.drawButton(mc, mouseX, mouseY, partialTicks);
                }
            }

            @Override
            public boolean mousePressed(Minecraft mc, int mouseX, int mouseY) {
                return (tileEntity.getRecipeType() == RecipeType.INFUSING)
                        && super.mousePressed(mc, mouseX, mouseY);
            }
        }.with(GuiDisableableButton.ImageOverlay.DUMP));
        buttonList.add(FactoryOldSortingButton = new GuiDisableableButton(2, guiLeft - 21, guiTop + 90, 18, 18).with(GuiDisableableButton.ImageOverlay.ROUND_ROBIN));
    }

    @Override
    protected void drawGuiContainerForegroundLayer(int mouseX, int mouseY) {
        fontRenderer.drawString(tileEntity.getName(), (xSize / 2) - (fontRenderer.getStringWidth(tileEntity.getName()) / 2), 4, 0x404040);
        int xOffset = tileEntity.tier == FactoryTier.CREATIVE ? 44 : tileEntity.tier == FactoryTier.ULTIMATE ? 27 : 8;
        fontRenderer.drawString(LangUtils.localize("container.inventory"), xOffset, (ySize - 93) + 2, 0x404040);
        int xAxis = mouseX - guiLeft;
        int yAxis = mouseY - guiTop;
        if (infuserDumpButton.isMouseOver()) {
            this.displayTooltip(LangUtils.localize("gui.remove"), xAxis, yAxis);
        } else if (FactoryOldSortingButton.isMouseOver()) {
            List<String> info = new ArrayList<>();
            info.add(LangUtils.localize("gui.factory.autoSort.old") + ":" + LangUtils.transOnOff(tileEntity.Factoryoldsorting));
            info.add(LangUtils.localize("gui.factory.autoSort.old.info"));
            info.add(LangUtils.localize("gui.factory.autoSort.old.info2"));
            this.displayTooltips(info, xAxis, yAxis);
        } else if (xAxis >= -21 && xAxis <= -3 && yAxis >= 116 && yAxis <= 134) {
            List<String> info = new ArrayList<>();
            boolean outslot = false;
            boolean outslot2 = false;
            boolean inputgas = false;
            boolean inputinfuse = false;
            boolean energy = tileEntity.getEnergy() < tileEntity.energyPerTick || tileEntity.getEnergy() == 0;
            for (int i = 0; i < tileEntity.tier.processes; i++) {
                if (tileEntity.inventory.get(5 + tileEntity.tier.processes + i).getCount() == tileEntity.inventory.get(5 + tileEntity.tier.processes + i).getMaxStackSize()) {
                    outslot = true;
                }
                if (tileEntity.inventory.get(5 + tileEntity.tier.processes * 2 + i).getCount() == tileEntity.inventory.get(5 + tileEntity.tier.processes * 2 + i).getMaxStackSize()) {
                    outslot2 = true;
                }
                if ((tileEntity.gasTank.getStored() == 0) && (tileEntity.inventory.get(5 + i).getCount() != 0) && tileEntity.GasAdvancedInputMachine()) {
                    inputgas = true;
                }
                if ((tileEntity.infuseStored.getAmount() == 0) && (tileEntity.inventory.get(5 + i).getCount() != 0) && tileEntity.getRecipeType() == RecipeType.INFUSING) {
                    inputinfuse = true;
                }
            }
            if (energy) {
                info.add(LangUtils.localize("gui.no_energy"));
            }
            if (inputinfuse) {
                info.add(LangUtils.localize("gui.infuse_no_item"));
            }
            if (inputgas) {
                info.add(LangUtils.localize("gui.no_gas"));
            }
            if (outslot) {
                info.add(LangUtils.localize("gui.item_no_space"));
            }
            if (outslot2) {
                info.add(LangUtils.localize("gui.item_no_space"));
            }
            if (outslot || outslot2 || energy || inputgas || inputinfuse) {
                this.displayTooltips(info, xAxis, yAxis);
            }
        }

        int short_X_Tank = tileEntity.tier == FactoryTier.CREATIVE ? 212 : tileEntity.tier == FactoryTier.ULTIMATE ? 174 : 140;

        if (tileEntity.getRecipeType() == RecipeType.INFUSING) {
            if (xAxis >= 7 && xAxis <= 7 + short_X_Tank && yAxis >= 77 && yAxis <= 77 + 7) {
                this.displayTooltip(tileEntity.infuseStored.getType() != null ? tileEntity.infuseStored.getType().getLocalizedName() + ": " + (tileEntity.infuseStored.getAmount() == Integer.MAX_VALUE ? LangUtils.localize("gui.infinite") : tileEntity.infuseStored.getAmount()) : LangUtils.localize("gui.empty"), xAxis, yAxis);
            }
        }

        super.drawGuiContainerForegroundLayer(mouseX, mouseY);
    }

    @Override
    protected void drawGuiContainerBackgroundLayer(int xAxis, int yAxis) {
        super.drawGuiContainerBackgroundLayer(xAxis, yAxis);

        int Slot_X_Distance = tileEntity.tier == FactoryTier.BASIC ? 38 : tileEntity.tier == FactoryTier.ADVANCED ? 26 : 19;
        int One_X_Slot_Location = tileEntity.tier == FactoryTier.BASIC ? 54 : tileEntity.tier == FactoryTier.ADVANCED ? 34 : tileEntity.tier == FactoryTier.ELITE ? 28 : 26;
        int short_X_Tank = tileEntity.tier == FactoryTier.CREATIVE ? 212 : tileEntity.tier == FactoryTier.ULTIMATE ? 174 : 140;

        if (tileEntity.getRecipeType() == RecipeType.INFUSING) {
            GuiUtils.drawBarSprite(guiLeft + 7, guiTop + 77, short_X_Tank, 7, (int) tileEntity.getScaledInfuseLevel(short_X_Tank - 2), tileEntity.infuseStored, false);
        }


        for (int i = 0; i < tileEntity.tier.processes; i++) {
            boolean outslot = tileEntity.inventory.get(5 + tileEntity.tier.processes + i).getCount() == tileEntity.inventory.get(5 + tileEntity.tier.processes + i).getMaxStackSize();
            boolean outslot2 = tileEntity.inventory.get(5 + tileEntity.tier.processes * 2 + i).getCount() == tileEntity.inventory.get(5 + tileEntity.tier.processes * 2 + i).getMaxStackSize();
            boolean energy = tileEntity.getEnergy() < tileEntity.energyPerTick || tileEntity.getEnergy() == 0;
            boolean inputinfuse = (tileEntity.infuseStored.getAmount() == 0) && (tileEntity.inventory.get(5 + i).getCount() != 0) && tileEntity.getRecipeType() == RecipeType.INFUSING;
            if (outslot) {
                mc.getTextureManager().bindTexture(MekanismUtils.getResource(ResourceType.SLOT, "Slot_Icon.png"));
                drawTexturedModalRect(guiLeft + (One_X_Slot_Location + (i * Slot_X_Distance)), guiTop + 56, 158, 0, 18, 18);
            }
            if (outslot2) {
                mc.getTextureManager().bindTexture(MekanismUtils.getResource(ResourceType.SLOT, "Slot_Icon.png"));
                drawTexturedModalRect(guiLeft + (One_X_Slot_Location + (i * Slot_X_Distance)), guiTop + 77, 158, 0, 18, 18);
            }
            if (inputinfuse) {
                mc.getTextureManager().bindTexture(MekanismUtils.getResource(MekanismUtils.ResourceType.GUI, "Warning_Background.png"));
                drawTexturedModalRect(guiLeft + 8, guiTop + 78, 0, 0, short_X_Tank - 2, 5);
            }
            if (outslot || outslot2 || energy || inputinfuse) {
                mc.getTextureManager().bindTexture(MekanismUtils.getResource(ResourceType.TAB, "Warning_Info.png"));
                drawTexturedModalRect(guiLeft - 26, guiTop + 112, 0, 0, 26, 26);
                addGuiElement(new GuiWarningInfo(this, getGuiLocation(), false));
            }
        }

        mc.getTextureManager().bindTexture(MekanismUtils.getResource(MekanismUtils.ResourceType.GUI, "State.png"));
        drawTexturedModalRect(guiLeft - 10, guiTop + 81, 6, 6, 8, 8);
        drawTexturedModalRect(guiLeft - 9, guiTop + 82, tileEntity.Factoryoldsorting ? 0 : 6, 0, 6, 6);
    }


    @Override
    protected void mouseClicked(int x, int y, int button) throws IOException {
        super.mouseClicked(x, y, button);
        if (button == 0 || Keyboard.isKeyDown(Keyboard.KEY_LSHIFT)) {
            int xAxis = x - guiLeft;
            int yAxis = y - guiTop;
            int xOffset = tileEntity.tier == FactoryTier.CREATIVE ? 218 : tileEntity.tier == FactoryTier.ULTIMATE ? 180 : 146;
            int isfarm = tileEntity.getRecipeType().getFuelType() == MachineFuelType.FARM ? 21 : 0;
            if (xAxis > 8 && xAxis < xOffset && yAxis > 78 + isfarm && yAxis < 83 + isfarm) {
                ItemStack stack = mc.player.inventory.getItemStack();
                if (!stack.isEmpty() && stack.getItem() instanceof ItemGaugeDropper) {
                    TileNetworkList data = TileNetworkList.withContents(1);
                    Mekanism.packetHandler.sendToServer(new TileEntityMessage(tileEntity, data));
                    SoundHandler.playSound(SoundEvents.UI_BUTTON_CLICK);
                }
            }
        }
    }


    @Override
    protected void actionPerformed(GuiButton button) throws IOException {
        super.actionPerformed(button);
        if (button == this.infuserDumpButton) {
            TileNetworkList data = TileNetworkList.withContents(1);
            Mekanism.packetHandler.sendToServer(new TileEntityMessage(tileEntity, data));
            SoundHandler.playSound(SoundEvents.UI_BUTTON_CLICK);
        } else if (button == FactoryOldSortingButton) {
            TileNetworkList data = TileNetworkList.withContents(2);
            Mekanism.packetHandler.sendToServer(new TileEntityMessage(tileEntity, data));
            SoundHandler.playSound(SoundEvents.UI_BUTTON_CLICK);
        }
    }
}
