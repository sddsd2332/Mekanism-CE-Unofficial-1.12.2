package mekanism.client.gui.element.gauge;

import mekanism.api.transmitters.TransmissionType;
import mekanism.client.gui.IGuiWrapper;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;

import javax.annotation.Nullable;
import java.util.Collections;
import java.util.List;

public class GuiNumberGauge extends GuiGauge<Void> {

    private final INumberInfoHandler infoHandler;

    public GuiNumberGauge(INumberInfoHandler infoHandler, GaugeType type, IGuiWrapper gui, int x, int y) {
        super(type, gui, x, y);
        this.infoHandler = infoHandler;
    }

    @Override
    @Nullable
    public TransmissionType getTransmission() {
        return null;
    }

    @Override
    public int getScaledLevel() {
        return (int) ((height - 2) * infoHandler.getScaledLevel());
    }

    @Override
    public TextureAtlasSprite getIcon() {
        return infoHandler.getIcon();
    }

    @Nullable
    @Override
    public net.minecraft.util.text.ITextComponent getLabel() {
        return null;
    }

    @Override
    public List<String> getTooltipText() {
        return Collections.singletonList(infoHandler.getText());
    }

    public interface INumberInfoHandler {

        TextureAtlasSprite getIcon();

        double getLevel();

        double getScaledLevel();

        String getText();
    }
}
