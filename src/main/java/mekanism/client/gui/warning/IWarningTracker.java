package mekanism.client.gui.warning;

import mekanism.client.gui.warning.WarningTracker.WarningType;
import net.minecraft.util.text.ITextComponent;

import javax.annotation.Nonnull;
import java.util.List;
import java.util.function.BooleanSupplier;

public interface IWarningTracker {

    BooleanSupplier trackWarning(@Nonnull WarningType type, @Nonnull BooleanSupplier warningSupplier);

    boolean hasWarning();

    List<ITextComponent> getWarnings();

    void clearTrackedWarnings();
}
