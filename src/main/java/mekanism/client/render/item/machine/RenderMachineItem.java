package mekanism.client.render.item.machine;

import mekanism.client.render.item.ItemLayerWrapper;
import mekanism.client.render.item.SubTypeItemRenderer;
import mekanism.common.block.states.BlockStateMachine.MachineType;
import net.minecraft.client.renderer.block.model.ItemCameraTransforms.TransformType;
import net.minecraft.item.ItemStack;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.EnumMap;
import java.util.Map;

@SideOnly(Side.CLIENT)
public class RenderMachineItem extends SubTypeItemRenderer<MachineType> {

    public static Map<MachineType, ItemLayerWrapper> modelMap = new EnumMap<>(MachineType.class);

    @Override
    protected boolean earlyExit() {
        return true;
    }

    @Override
    protected void renderBlockSpecific(@Nonnull ItemStack stack, TransformType transformType) {
        MachineType machineType = MachineType.get(stack);

        if (machineType != null) {
            if (machineType == MachineType.FLUID_TANK) {
                RenderFluidTankItem.renderStack(stack, transformType);
            } else if (machineType == MachineType.PERSONAL_CHEST) {
                RenderPersonalChestItem.renderStack(stack, transformType);
            } else if (machineType == MachineType.SOLAR_NEUTRON_ACTIVATOR) {
                RenderSolarNeutronActivatorItem.renderStack(stack, transformType);
            } else if (machineType == MachineType.SEISMIC_VIBRATOR) {
                RenderSeismicVibratorItem.renderStack(stack, transformType);
            } else if (machineType == MachineType.CHEMICAL_CRYSTALLIZER) {
                RenderChemicalCrystallizerItem.renderStack(stack, transformType);
            } else if (machineType == MachineType.CHEMICAL_DISSOLUTION_CHAMBER) {
                RenderChemicalDissolutionChamberItem.renderStack(stack, transformType);
            } else if (machineType == MachineType.QUANTUM_ENTANGLOPORTER) {
                RenderQuantumEntangloporterItem.renderStack(stack, transformType);
            } else if (machineType == MachineType.RESISTIVE_HEATER) {
                RenderResistiveHeaterItem.renderStack(stack, transformType);
            } else if (machineType == MachineType.DIGITAL_MINER) {
                RenderDigitalMinerItem.renderStack(stack, transformType);
            }
            /**
             * ADD START
             */
            else if (machineType == MachineType.ISOTOPIC_CENTRIFUGE) {
                RenderIsotopicCentrifugeItem.renderStack(stack, transformType);
            } else if (machineType == MachineType.NUTRITIONAL_LIQUIFIER) {
                RenderNutritionalLiquifierItem.renderStack(stack, transformType);
            } else if (machineType == MachineType.ANTIPROTONIC_NUCLEOSYNTHESIZER) {
                RenderAntiprotonicNucleosynthesizerItem.renderStack(stack, transformType);
            }
            /**
             * ADD END
             */
        }
    }

    @Override
    protected void renderItemSpecific(@Nonnull ItemStack stack, TransformType transformType) {

    }

    @Nullable
    @Override
    protected ItemLayerWrapper getModel(MachineType machineType) {
        return modelMap.get(machineType);
    }

    @Nullable
    @Override
    protected MachineType getType(@Nonnull ItemStack stack) {
        return MachineType.get(stack);
    }
}
