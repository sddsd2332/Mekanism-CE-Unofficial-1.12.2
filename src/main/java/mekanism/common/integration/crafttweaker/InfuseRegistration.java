package mekanism.common.integration.crafttweaker;

import crafttweaker.CraftTweakerAPI;
import crafttweaker.IAction;
import crafttweaker.annotations.ZenRegister;
import crafttweaker.api.item.IIngredient;
import crafttweaker.api.minecraft.CraftTweakerMC;
import mekanism.api.infuse.InfuseObject;
import mekanism.api.infuse.InfuseRegistry;
import mekanism.api.infuse.InfuseType;
import net.minecraft.creativetab.CreativeTabs;
import net.minecraft.item.ItemStack;
import net.minecraft.util.NonNullList;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.items.ItemHandlerHelper;
import net.minecraftforge.oredict.OreDictionary;
import stanhebben.zenscript.annotations.ZenClass;
import stanhebben.zenscript.annotations.ZenMethod;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

@ZenClass("mods.mekanism.infuse")
@ZenRegister
public class InfuseRegistration {

    private InfuseRegistration() {
    }

    @ZenMethod
    public static String registerType(String name, String texture) {
        String normalizedName = normalizeName(name);
        return registerType(name, texture, normalizedName == null ? null : normalizedName.toLowerCase(Locale.ROOT));
    }

    @ZenMethod
    public static String registerType(String name, String texture, String translationKey) {
        RegisterInfuseTypeAction action = new RegisterInfuseTypeAction(name, texture, translationKey);
        CraftTweakerAPI.apply(action);
        return action.registered == null ? null : action.registered.name;
    }

    @ZenMethod
    public static boolean contains(String name) {
        String normalizedName = normalizeName(name);
        return normalizedName != null && InfuseRegistry.contains(normalizedName);
    }

    @ZenMethod
    public static void registerItem(IIngredient ingredient, String typeName, int amount) {
        CraftTweakerAPI.apply(new RegisterInfuseItemAction(ingredient, typeName, amount));
    }

    @ZenMethod
    public static void registerObject(IIngredient ingredient, String typeName, int amount) {
        registerItem(ingredient, typeName, amount);
    }

    private static String normalizeName(String name) {
        return name == null ? null : name.trim().toUpperCase(Locale.ROOT);
    }

    static ItemStack[] expandMatchingStacks(ItemStack[] stacks) {
        List<ItemStack> expanded = new ArrayList<>();
        for (ItemStack stack : stacks) {
            if (stack == null || stack.isEmpty()) {
                continue;
            }
            if (stack.getMetadata() != OreDictionary.WILDCARD_VALUE) {
                addUniqueStack(expanded, stack);
                continue;
            }

            NonNullList<ItemStack> subItems = NonNullList.create();
            try {
                stack.getItem().getSubItems(CreativeTabs.SEARCH, subItems);
            } catch (RuntimeException e) {
                return null;
            }

            boolean foundConcreteSubtype = false;
            for (ItemStack subItem : subItems) {
                if (subItem.isEmpty() || subItem.getItem() != stack.getItem() || subItem.getMetadata() == OreDictionary.WILDCARD_VALUE) {
                    continue;
                }
                foundConcreteSubtype = true;
                ItemStack concrete = subItem.copy();
                if (stack.hasTagCompound()) {
                    concrete.setTagCompound(stack.getTagCompound().copy());
                }
                addUniqueStack(expanded, concrete);
            }
            if (!foundConcreteSubtype) {
                return null;
            }
        }
        return expanded.toArray(new ItemStack[0]);
    }

    private static void addUniqueStack(List<ItemStack> stacks, ItemStack stack) {
        ItemStack normalized = stack.copy();
        normalized.setCount(1);
        for (ItemStack existing : stacks) {
            if (ItemHandlerHelper.canItemStacksStack(existing, normalized)) {
                return;
            }
        }
        stacks.add(normalized);
    }

    private static class RegisterInfuseTypeAction implements IAction {

        private final String name;
        private final String texture;
        private final String translationKey;
        private String invalidReason;
        private InfuseType registered;

        private RegisterInfuseTypeAction(String name, String texture, String translationKey) {
            this.name = normalizeName(name);
            this.texture = texture == null ? null : texture.trim();
            this.translationKey = translationKey == null ? null : translationKey.trim();
        }

        @Override
        public void apply() {
            registered = new InfuseType(name, new ResourceLocation(texture)).setTranslationKey(translationKey);
            InfuseRegistry.registerInfuseType(registered);
        }

        @Override
        public String describe() {
            return "Registering Mekanism infuse type '" + name + "'";
        }

        @Override
        public boolean validate() {
            invalidReason = validateParameters();
            return invalidReason == null;
        }

        @Override
        public String describeInvalid() {
            return invalidReason;
        }

        private String validateParameters() {
            if (!CrafttweakerIntegration.isRegistryRegistrationOpen()) {
                return "Mekanism infuse types must be registered from a '#loader mekanism' script";
            }
            if (name == null || name.isEmpty()) {
                return "Mekanism infuse type name must not be empty";
            }
            if (InfuseRegistry.contains(name)) {
                return "A Mekanism infuse type named '" + name + "' is already registered";
            }
            if (texture == null || texture.isEmpty()) {
                return "Mekanism infuse type texture must not be empty";
            }
            try {
                new ResourceLocation(texture);
            } catch (RuntimeException e) {
                return "Invalid Mekanism infuse type texture '" + texture + "'";
            }
            if (translationKey == null || translationKey.isEmpty()) {
                return "Mekanism infuse type translation key must not be empty";
            }
            return null;
        }
    }

    private static class RegisterInfuseItemAction implements IAction {

        private final IIngredient ingredient;
        private final String typeName;
        private final int amount;
        private InfuseType type;
        private ItemStack[] matchingStacks = new ItemStack[0];
        private String invalidReason;

        private RegisterInfuseItemAction(IIngredient ingredient, String typeName, int amount) {
            this.ingredient = ingredient;
            this.typeName = normalizeName(typeName);
            this.amount = amount;
        }

        @Override
        public void apply() {
            InfuseObject infuseObject = new InfuseObject(type, amount);
            for (ItemStack stack : matchingStacks) {
                if (InfuseRegistry.getObject(stack) == null) {
                    ItemStack registeredStack = stack.copy();
                    registeredStack.setCount(1);
                    InfuseRegistry.registerInfuseObject(registeredStack, infuseObject);
                } else {
                    CraftTweakerAPI.logWarning("Skipping Mekanism infuse item '" + stack.getDisplayName() + "': it already has an infuse mapping");
                }
            }
        }

        @Override
        public String describe() {
            return "Registering " + matchingStacks.length + " item(s) as " + amount + " units of Mekanism infuse type '" + typeName + "'";
        }

        @Override
        public boolean validate() {
            if (ingredient == null) {
                invalidReason = "Mekanism infuse item ingredient must not be null";
            } else if (typeName == null || typeName.isEmpty()) {
                invalidReason = "Mekanism infuse type name must not be empty";
            } else if ((type = InfuseRegistry.get(typeName)) == null) {
                invalidReason = "Could not find Mekanism infuse type '" + typeName + "'";
            } else if (amount <= 0) {
                invalidReason = "Mekanism infuse item amount must be greater than zero";
            } else if ((matchingStacks = CraftTweakerMC.getIngredient(ingredient).getMatchingStacks()).length == 0) {
                invalidReason = "Mekanism infuse item ingredient must match at least one item";
            } else if ((matchingStacks = expandMatchingStacks(matchingStacks)) == null) {
                matchingStacks = new ItemStack[0];
                invalidReason = "Mekanism infuse item wildcard could not be expanded to concrete metadata variants";
            } else if (matchingStacks.length == 0) {
                invalidReason = "Mekanism infuse item ingredient must match at least one non-empty item";
            } else {
                invalidReason = null;
            }
            return invalidReason == null;
        }

        @Override
        public String describeInvalid() {
            return invalidReason;
        }
    }
}
