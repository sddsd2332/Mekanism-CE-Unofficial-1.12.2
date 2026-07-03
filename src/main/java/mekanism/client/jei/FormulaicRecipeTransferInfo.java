package mekanism.client.jei;

import mekanism.common.inventory.container.ContainerFormulaicAssemblicator;
import mezz.jei.api.recipe.VanillaRecipeCategoryUid;
import mezz.jei.api.recipe.transfer.IRecipeTransferInfo;
import net.minecraft.inventory.Slot;

import java.util.List;

public class FormulaicRecipeTransferInfo implements IRecipeTransferInfo<ContainerFormulaicAssemblicator> {

    @Override
    public Class<ContainerFormulaicAssemblicator> getContainerClass() {
        return ContainerFormulaicAssemblicator.class;
    }

    @Override
    public String getRecipeCategoryUid() {
        return VanillaRecipeCategoryUid.CRAFTING;
    }

    @Override
    public boolean canHandle(ContainerFormulaicAssemblicator container) {
        return true;
    }

    @Override
    public List<Slot> getRecipeSlots(ContainerFormulaicAssemblicator container) {
        return RVTransferUtils.getFormulaicCraftingSlots(container);
    }

    @Override
    public List<Slot> getInventorySlots(ContainerFormulaicAssemblicator container) {
        return RVTransferUtils.getFormulaicInputSlots(container);
    }
}
