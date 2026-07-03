package mekanism.common.base;

import mekanism.api.IIncrementalEnum;
import mekanism.api.math.MathUtils;
import mekanism.common.util.LangUtils;

import javax.annotation.Nonnull;

public interface IFluidContainerManager {

    ContainerEditMode getContainerEditMode();

    void setContainerEditMode(ContainerEditMode mode);

    default void nextContainerEditMode() {
        setContainerEditMode(getContainerEditMode().getNext());
    }

    default void previousContainerEditMode() {
        setContainerEditMode(getContainerEditMode().getPrevious());
    }

    enum ContainerEditMode implements IIncrementalEnum<ContainerEditMode> {
        BOTH("fluidedit.both"),
        FILL("fluidedit.fill"),
        EMPTY("fluidedit.empty");

        private static final ContainerEditMode[] MODES = values();
        private final String display;

        ContainerEditMode(String display) {
            this.display = display;
        }

        public String getDisplay() {
            return LangUtils.localize(display);
        }

        @Nonnull
        @Override
        public ContainerEditMode byIndex(int index) {
            return byIndexStatic(index);
        }

        public static ContainerEditMode byIndexStatic(int index) {
            return MathUtils.getByIndexMod(MODES, index);
        }
    }
}
