package mekanism.common.tile.component.config.slot;

public class BaseSlotInfo implements ISlotInfo {

    private final boolean canInput;
    private final boolean canOutput;

    public BaseSlotInfo(boolean canInput, boolean canOutput) {
        this.canInput = canInput;
        this.canOutput = canOutput;
    }

    @Override
    public boolean canInput() {
        return canInput;
    }

    @Override
    public boolean canOutput() {
        return canOutput;
    }
}
