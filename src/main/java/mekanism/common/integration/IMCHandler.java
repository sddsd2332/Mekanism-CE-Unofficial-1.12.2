package mekanism.common.integration;

import mekanism.common.Mekanism;
import mekanism.common.recipe.RecipeHandler;
import mekanism.common.recipe.RecipeHandler.Recipe;
import mekanism.common.recipe.inputs.MachineInput;
import mekanism.common.recipe.machines.MachineRecipe;
import mekanism.common.recipe.outputs.MachineOutput;
import net.minecraftforge.fml.common.event.FMLInterModComms.IMCMessage;

import java.util.List;

public class IMCHandler {

    public void onIMCEvent(List<IMCMessage> messages) {
        for (IMCMessage msg : messages) {
            if (msg.isNBTMessage()) {
                boolean found = false;
                boolean delete = false;

                String message = msg.key;

                if (message.equals("ShapedMekanismRecipe") || message.equals("ShapelessMekanismRecipe") || message.equals("DeleteMekanismRecipes") ||
                        message.equals("RemoveMekanismRecipes")) {
                    Mekanism.logger.warn(msg.getSender() + " tried to send IMC " + message + " which has been deleted. Please notify the mod developer to use JSON recipes.");
                    found = true;
                }

                if (message.startsWith("Delete") || message.startsWith("Remove")) {
                    message = message.replace("Delete", "").replace("Remove", "");
                    delete = true;
                }

                for (Recipe<?, ?, ?> type : Recipe.values()) {
                    if (message.equalsIgnoreCase(type.getRecipeName() + "Recipe")) {
                        handleRecipe(type, msg, delete);
                        found = true;
                        break;
                    }
                }
                if (!found) {
                    Mekanism.logger.error(msg.getSender() + " sent unknown IMC message with key '" + msg.key + ".'");
                }
            }
        }
    }

    private <INPUT extends MachineInput<INPUT>, OUTPUT extends MachineOutput<OUTPUT>, RECIPE extends MachineRecipe<INPUT, OUTPUT, RECIPE>>
    void handleRecipe(Recipe<INPUT, OUTPUT, RECIPE> type, IMCMessage msg, boolean delete) {
        INPUT input = type.createInput(msg.getNBTValue());
        if (input != null && input.isValid()) {
            RECIPE recipe = type.createRecipe(input, msg.getNBTValue());
            if (recipe != null && recipe.recipeOutput != null) {
                if (delete) {
                    RecipeHandler.removeRecipe(type, recipe);
                    Mekanism.logger.info(msg.getSender() + " removed recipe of type " + type.getRecipeName() + " from the recipe list.");
                } else {
                    RecipeHandler.addRecipe(type, recipe);
                    Mekanism.logger.info(msg.getSender() + " added recipe of type " + type.getRecipeName() + " to the recipe list.");
                }
            } else {
                Mekanism.logger.error(msg.getSender() + " attempted to " + (delete ? "remove" : "add") + " recipe of type " + type.getRecipeName() + " with an invalid output.");
            }
        } else {
            Mekanism.logger.error(msg.getSender() + " attempted to " + (delete ? "remove" : "add") + " recipe of type " + type.getRecipeName() + " with an invalid input.");
        }
    }
}
