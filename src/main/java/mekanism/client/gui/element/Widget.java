package mekanism.client.gui.element;

import net.minecraft.client.Minecraft;
import net.minecraft.client.audio.PositionedSoundRecord;
import net.minecraft.client.audio.SoundHandler;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.init.SoundEvents;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.text.ITextComponent;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import java.util.Objects;
import java.util.function.LongSupplier;

@SideOnly(Side.CLIENT)
public class Widget extends Gui implements IGuiEventListener {

    public static final ResourceLocation WIDGETS_LOCATION = new ResourceLocation("textures/gui/widgets.png");
    protected int width;
    protected int height;
    public int x;
    public int y;
    private ITextComponent message;
    protected boolean wasHovered;
    protected boolean isHovered;
    public boolean active = true;
    public boolean visible = true;
    protected float alpha = 1.0F;
    protected long nextNarration = Long.MAX_VALUE;
    private boolean focused;
    public static LongSupplier timeSource = System::nanoTime;

    public Widget(int pX, int pY, int pWidth, int pHeight, ITextComponent pMessage) {
        this.x = pX;
        this.y = pY;
        this.width = pWidth;
        this.height = pHeight;
        this.message = pMessage;
    }

    public int getHeight() {
        return this.height;
    }

    public int getX() {
        return this.x;
    }

    public int getY() {
        return this.y;
    }

    public int getRight() {
        return this.x + this.width;
    }

    public int getBottom() {
        return this.y + this.height;
    }

    protected int getYImage(boolean pIsHovered) {
        int i = 1;
        if (!this.active) {
            i = 0;
        } else if (pIsHovered) {
            i = 2;
        }

        return i;
    }


    public void render(int pMouseX, int pMouseY, float pPartialTicks) {
        if (this.visible) {
            this.isHovered = pMouseX >= this.x && pMouseY >= this.y && pMouseX < this.x + this.width && pMouseY < this.y + this.height;
            if (this.wasHovered != this.isHovered()) {
                if (this.isHovered()) {
                    if (this.focused) {
                        this.queueNarration(200);
                    } else {
                        this.queueNarration(750);
                    }
                } else {
                    this.nextNarration = Long.MAX_VALUE;
                }
            }

            if (this.visible) {
                this.renderButton(pMouseX, pMouseY, pPartialTicks);
            }


            this.wasHovered = this.isHovered();
        }
    }


    public void renderButton(int pMouseX, int pMouseY, float pPartialTicks) {
        Minecraft minecraft = Minecraft.getMinecraft();
        FontRenderer fontrenderer = minecraft.fontRenderer;
        minecraft.getTextureManager().bindTexture(WIDGETS_LOCATION);
        GlStateManager.color(1.0F, 1.0F, 1.0F, this.alpha);
        int i = this.getYImage(this.isHovered());
        GlStateManager.enableBlend();
        GlStateManager.tryBlendFuncSeparate(GlStateManager.SourceFactor.SRC_ALPHA, GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA, GlStateManager.SourceFactor.ONE, GlStateManager.DestFactor.ZERO);
        GlStateManager.enableDepth();
        this.drawTexturedModalRect(this.x, this.y, 0, 46 + i * 20, this.width / 2, this.height);
        this.drawTexturedModalRect(this.x + this.width / 2, this.y, 200 - this.width / 2, 46 + i * 20, this.width / 2, this.height);
        this.renderBg(minecraft, pMouseX, pMouseY);
        int j = getFGColor();
        drawCenteredString(fontrenderer, this.getMessage().getFormattedText(), this.x + this.width / 2, this.y + (this.height - 8) / 2, j | MathHelper.ceil(this.alpha * 255.0F) << 24);
    }

    protected void renderBg(Minecraft pMinecraft, int pMouseX, int pMouseY) {
    }

    public void onClick(double pMouseX, double pMouseY) {
    }

    public void onClick(double pMouseX, double pMouseY, int pButton) {
        onClick(pMouseX, pMouseY);
    }

    public void onRelease(double pMouseX, double pMouseY) {
    }

    protected void onDrag(double pMouseX, double pMouseY, double pDragX, double pDragY) {
    }


    public boolean mouseClicked(double pMouseX, double pMouseY, int pButton) {
        if (this.active && this.visible) {
            if (this.isValidClickButton(pButton)) {
                boolean flag = this.clicked(pMouseX, pMouseY);
                if (flag) {
                    this.playDownSound(Minecraft.getMinecraft().getSoundHandler());
                    this.onClick(pMouseX, pMouseY, pButton);
                    return true;
                }
            }

            return false;
        } else {
            return false;
        }
    }

    public boolean mouseReleased(double pMouseX, double pMouseY, int pButton) {
        if (this.isValidClickButton(pButton)) {
            this.onRelease(pMouseX, pMouseY);
            return true;
        } else {
            return false;
        }
    }

    protected boolean isValidClickButton(int pButton) {
        return pButton == 0;
    }

    public boolean mouseDragged(double pMouseX, double pMouseY, int pButton, double pDragX, double pDragY) {
        if (this.isValidClickButton(pButton)) {
            this.onDrag(pMouseX, pMouseY, pDragX, pDragY);
            return true;
        } else {
            return false;
        }
    }

    protected boolean clicked(double pMouseX, double pMouseY) {
        return this.active && this.visible && pMouseX >= (double) this.x && pMouseY >= (double) this.y && pMouseX < (double) (this.x + this.width) && pMouseY < (double) (this.y + this.height);
    }

    public boolean isHovered() {
        return this.isHovered || this.focused;
    }

    public boolean changeFocus(boolean pFocus) {
        if (this.active && this.visible) {
            this.focused = !this.focused;
            this.onFocusedChanged(this.focused);
            return this.focused;
        } else {
            return false;
        }
    }

    protected void onFocusedChanged(boolean pFocused) {
    }


    public boolean isMouseOver(double pMouseX, double pMouseY) {
        return this.active && this.visible && pMouseX >= (double) this.x && pMouseY >= (double) this.y && pMouseX < (double) (this.x + this.width) && pMouseY < (double) (this.y + this.height);
    }

    public void renderToolTip(int pMouseX, int pMouseY) {
    }

    public void playDownSound(SoundHandler pHandler) {
        pHandler.playSound(PositionedSoundRecord.getMasterRecord(SoundEvents.UI_BUTTON_CLICK, 1.0F));
    }

    public int getWidth() {
        return this.width;
    }

    public void setWidth(int pWidth) {
        this.width = pWidth;
    }

    public void setHeight(int value) {
        this.height = value;
    }

    public void setAlpha(float pAlpha) {
        this.alpha = pAlpha;
    }

    public void setMessage(ITextComponent pMessage) {
        if (!Objects.equals(pMessage.getFormattedText(), this.message.getFormattedText())) {
            this.queueNarration(250);
        }

        this.message = pMessage;
    }

    public static long getMillis() {
        return getNanos() / 1000000L;
    }

    public static long getNanos() {
        return timeSource.getAsLong();
    }

    public void queueNarration(int p_230994_1_) {
        this.nextNarration = getMillis() + (long) p_230994_1_;
    }

    public ITextComponent getMessage() {
        return this.message;
    }

    public boolean isFocused() {
        return this.focused;
    }

    protected void setFocused(boolean pFocused) {
        this.focused = pFocused;
    }

    public static final int UNSET_FG_COLOR = -1;
    protected int packedFGColor = UNSET_FG_COLOR;

    public int getFGColor() {
        if (packedFGColor != UNSET_FG_COLOR) return packedFGColor;
        return this.active ? 16777215 : 10526880; // White : Light Grey
    }

    public void setFGColor(int color) {
        this.packedFGColor = color;
    }

    public void clearFGColor() {
        this.packedFGColor = UNSET_FG_COLOR;
    }


}
