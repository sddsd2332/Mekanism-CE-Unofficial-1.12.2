package mekanism.common.integration.crafttweaker.gas;

import crafttweaker.api.item.*;
import crafttweaker.api.liquid.ILiquidStack;
import crafttweaker.api.player.IPlayer;
import mekanism.api.gas.GasStack;
import mekanism.common.integration.crafttweaker.helpers.GasHelper;

import java.util.Collections;
import java.util.List;

public class CraftTweakerGasStack implements IGasStack {

    private final GasStack stack;

    public CraftTweakerGasStack(GasStack stack) {
        this.stack = stack;
    }

    @Override
    public IGasDefinition getDefinition() {
        return new CraftTweakerGasDefinition(stack.getGas());
    }

    @Override
    public String getName() {
        return stack.getGas().getName();
    }

    @Override
    public String getDisplayName() {
        return stack.getGas().getLocalizedName();
    }

    @Override
    public String getMark() {
        return null;
    }

    @Override
    public int getAmount() {
        return stack.amount;
    }

    @Override
    public List<IItemStack> getItems() {
        return Collections.emptyList();
    }

    @Override
    public IItemStack[] getItemArray() {
        return new IItemStack[0];
    }

    @Override
    public List<ILiquidStack> getLiquids() {
        return Collections.emptyList();
    }

    @Override
    public IIngredient amount(int amount) {
        return withAmount(amount);
    }

    @Override
    public IIngredient or(IIngredient iIngredient) {
        return new IngredientOr(this, iIngredient);
    }

    @Override
    public IIngredient transformNew(IItemTransformerNew transformer) {
        throw new UnsupportedOperationException("Gas stacks cannot have item transformers");
    }

    @Override
    public IIngredient transform(IItemTransformer iItemTransformer) {
        throw new UnsupportedOperationException("Gas stacks cannot have item transformers");
    }

    @Override
    public IIngredient only(IItemCondition iItemCondition) {
        throw new UnsupportedOperationException("Gas stacks cannot have item conditions");
    }

    @Override
    public IIngredient marked(String s) {
        throw new UnsupportedOperationException("Gas stacks cannot be marked");
    }

    @Override
    public boolean matches(IItemStack iItemStack) {
        return false;
    }

    @Override
    public boolean matchesExact(IItemStack iItemStack) {
        return false;
    }

    @Override
    public boolean matches(ILiquidStack iLiquidStack) {
        return false;
    }

    @Override
    public boolean contains(IIngredient iIngredient) {
        if (!(iIngredient instanceof IGasStack gasStack)) {
            return false;
        }
        GasStack other = GasHelper.toGas(gasStack);
        return other != null && stack.isGasEqual(other) && stack.amount <= other.amount;
    }

    @Override
    public IItemStack applyTransform(IItemStack iItemStack, IPlayer iPlayer) {
        return iItemStack;
    }

    @Override
    public IItemStack applyNewTransform(IItemStack item) {
        return item;
    }

    @Override
    public boolean hasNewTransformers() {
        return false;
    }

    @Override
    public boolean hasTransformers() {
        return false;
    }

    @Override
    public IGasStack withAmount(int amount) {
        return new CraftTweakerGasStack(new GasStack(stack.getGas(), amount));
    }

    @Override
    public Object getInternal() {
        return stack;
    }

    @Override
    public String toCommandString() {
        return stack.amount > 1 ? String.format("<gas:%s> * %s", stack.getGas().getName(), stack.amount) : String.format("<gas:%s>", stack.getGas().getName());
    }

    @Override
    public String toString() {
        return toCommandString();
    }
}
