package mekanism.client.gui.element;

import mekanism.client.gui.GuiUtils;
import mekanism.client.gui.IGuiWrapper;
import mekanism.client.render.MekanismRenderer;
import mekanism.common.MekanismSounds;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.text.ITextComponent;

import javax.annotation.Nonnull;
import java.util.function.Consumer;
import java.util.function.Supplier;

/** Digital icon button that cycles through enum values. */
public class GuiDigitalIconToggle<TYPE extends Enum<TYPE> & GuiDigitalIconToggle.IIconToggleOption> extends GuiInnerScreen {

    private final Supplier<TYPE> typeSupplier;
    private final Consumer<TYPE> typeSetter;
    private final TYPE[] options;

    public GuiDigitalIconToggle(IGuiWrapper gui, int x, int y, int width, int height, Class<TYPE> enumClass,
          Supplier<TYPE> typeSupplier, Consumer<TYPE> typeSetter) {
        super(gui, x, y, width, height);
        this.typeSupplier = typeSupplier;
        this.typeSetter = typeSetter;
        options = enumClass.getEnumConstants();
        active = true;
        customClickSound = () -> MekanismSounds.BEEP;
        clickSoundVolume = 1;
    }

    @Override
    public void drawBackground(int mouseX, int mouseY, float partialTicks) {
        super.drawBackground(mouseX, mouseY, partialTicks);
        MekanismRenderer.bindTexture(typeSupplier.get().getIcon());
        GuiUtils.blit(relativeX + 3, relativeY + 3, 0, 0, width - 6, height - 6, width - 6, height - 6);
        MekanismRenderer.resetColor();
    }

    @Override
    public void onClick(double mouseX, double mouseY) {
        TYPE current = typeSupplier.get();
        typeSetter.accept(options[(current.ordinal() + 1) % options.length]);
    }

    @Override
    public void renderToolTip(int mouseX, int mouseY) {
        super.renderToolTip(mouseX, mouseY);
        displayTooltip(typeSupplier.get().getTooltip(), mouseX, mouseY);
    }

    public interface IIconToggleOption {

        @Nonnull
        ResourceLocation getIcon();

        @Nonnull
        ITextComponent getTooltip();
    }
}
