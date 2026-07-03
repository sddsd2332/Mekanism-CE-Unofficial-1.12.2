package mekanism.common.advancements;

import com.google.gson.JsonObject;
import mekanism.api.gas.GasStack;
import mekanism.common.MekanismFluids;
import mekanism.common.item.ItemCanteen;
import net.minecraft.advancements.critereon.ItemPredicate;
import net.minecraft.item.ItemStack;

public class FullCanteenItemPredicate extends ItemPredicate {

    public FullCanteenItemPredicate(JsonObject json) {
    }

    @Override
    public boolean test(ItemStack stack) {
        if (!(stack.getItem() instanceof ItemCanteen canteen)) {
            return false;
        }
        GasStack gas = canteen.getGas(stack);
        return gas != null && gas.getGas() == MekanismFluids.NutritionalPaste && gas.amount >= canteen.getMaxGas(stack);
    }
}
