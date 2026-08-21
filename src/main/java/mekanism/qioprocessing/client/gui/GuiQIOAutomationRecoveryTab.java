package mekanism.qioprocessing.client.gui;

import mekanism.client.SpecialColors;
import mekanism.client.gui.IGuiWrapper;
import mekanism.client.gui.element.GuiInsetElement;
import mekanism.client.render.MekanismRenderer;
import mekanism.api.processing.QIOAutomationMode;
import mekanism.common.util.MekanismUtils;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.text.TextComponentTranslation;

import java.util.Arrays;
import java.util.Objects;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;

/** Recovery-only side tab that requests a server-audited QIO automation force clear. */
/**
 * QIO 处理模块中的 GuiQIOAutomationRecoveryTab 类型。
 *
 * <p>该类型封装本层的数据、状态或服务职责；调用方应遵守其公开方法的输入约束，
 * 实现负责保持状态与持久化表示的一致。</p>
 */
public final class GuiQIOAutomationRecoveryTab extends GuiInsetElement<Void> {

    private static final ResourceLocation ICON = MekanismUtils.getResource(
          MekanismUtils.ResourceType.GUI_BUTTON, "repair.png");

    private final BooleanSupplier errorVisible;
    private final Supplier<QIOAutomationMode> modeSupplier;
    private final Supplier<String> diagnosticSupplier;
    private final Runnable recoveryAction;

    public GuiQIOAutomationRecoveryTab(IGuiWrapper gui, int x, int y,
          BooleanSupplier errorVisible, Runnable recoveryAction) {
        this(gui, x, y, errorVisible, () -> null, () -> null, recoveryAction);
    }

    public GuiQIOAutomationRecoveryTab(IGuiWrapper gui, int x, int y,
          BooleanSupplier errorVisible, Supplier<QIOAutomationMode> modeSupplier,
          Runnable recoveryAction) {
        this(gui, x, y, errorVisible, modeSupplier, () -> null, recoveryAction);
    }

    public GuiQIOAutomationRecoveryTab(IGuiWrapper gui, int x, int y,
          BooleanSupplier errorVisible, Supplier<QIOAutomationMode> modeSupplier,
          Supplier<String> diagnosticSupplier, Runnable recoveryAction) {
        this(gui, x, y, errorVisible, modeSupplier, diagnosticSupplier, recoveryAction, false);
    }

    /**
     * 创建恢复 Tab。
     *
     * @param gui 所属 GUI
     * @param x Tab 的 X 坐标
     * @param y Tab 的 Y 坐标
     * @param errorVisible 是否显示 Tab
     * @param modeSupplier 当前自动化模式
     * @param diagnosticSupplier 当前恢复诊断
     * @param recoveryAction 点击后的服务端恢复动作
     * @param left 是否使用左侧 Tab 外观和交互偏移
     */
    public GuiQIOAutomationRecoveryTab(IGuiWrapper gui, int x, int y,
          BooleanSupplier errorVisible, Supplier<QIOAutomationMode> modeSupplier,
          Supplier<String> diagnosticSupplier, Runnable recoveryAction, boolean left) {
        super(ICON, gui, null, x, y, 26, 18, left);
        this.errorVisible = Objects.requireNonNull(errorVisible, "Error visibility supplier cannot be null");
        this.modeSupplier = Objects.requireNonNull(modeSupplier, "Mode supplier cannot be null");
        this.diagnosticSupplier = Objects.requireNonNull(diagnosticSupplier,
              "Diagnostic supplier cannot be null");
        this.recoveryAction = Objects.requireNonNull(recoveryAction, "Recovery action cannot be null");
        updateState();
    }

    @Override
    public void tick() {
        super.tick();
        updateState();
    }

    private void updateState() {
        visible = errorVisible.getAsBoolean();
        active = visible;
    }

    @Override
    protected void colorTab() {
        MekanismRenderer.color(SpecialColors.TAB_SECURITY.argb());
    }

    @Override
    public void renderToolTip(int mouseX, int mouseY) {
        super.renderToolTip(mouseX, mouseY);
        java.util.List<String> lines = new java.util.ArrayList<>(Arrays.asList(
              new TextComponentTranslation(titleKey()).getFormattedText(),
              new TextComponentTranslation(tooltipKey()).getFormattedText()));
        String diagnostic = diagnosticSupplier.get();
        if (diagnostic != null && !diagnostic.isEmpty()) {
            lines.add(diagnostic);
        }
        displayTooltips(lines, mouseX, mouseY);
    }

    private String titleKey() {
        QIOAutomationMode mode = modeSupplier.get();
        return mode == null ? "gui.mekanismqioprocessing.force_recovery" :
              "gui.mekanismqioprocessing.force_recovery_" + mode.name().toLowerCase();
    }

    private String tooltipKey() {
        QIOAutomationMode mode = modeSupplier.get();
        return mode == null ? "gui.mekanismqioprocessing.force_recovery_tooltip" :
              "gui.mekanismqioprocessing.force_recovery_tooltip_" +
                    mode.name().toLowerCase();
    }

    @Override
    public void onClick(double mouseX, double mouseY, int button) {
        if (visible && button == 0) {
            active = false;
            recoveryAction.run();
        }
    }
}
