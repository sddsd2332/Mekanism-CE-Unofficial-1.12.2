package mekanism.client.gui.element.custom.module;

abstract class MiniElement {

    protected final GuiModuleScreen parent;
    protected final int xPos;
    protected final int yPos;
    protected final int dataIndex;

    MiniElement(GuiModuleScreen parent, int xPos, int yPos, int dataIndex) {
        this.parent = parent;
        this.xPos = xPos;
        this.yPos = yPos;
        this.dataIndex = dataIndex;
    }

    abstract void renderBackground(int mouseX, int mouseY);

    abstract void renderForeground(int mouseX, int mouseY);

    abstract void click(double mouseX, double mouseY);

    void release(double mouseX, double mouseY) {
    }

    void onDrag(double mouseX, double mouseY, double mouseXOld, double mouseYOld) {
    }

    abstract int getNeededHeight();

    int getRelativeX() {
        return parent.getRelativeX() + xPos;
    }

    int getRelativeY() {
        return parent.getRelativeY() + yPos;
    }

    int getX() {
        return parent.getX() + xPos;
    }

    int getY() {
        return parent.getY() + yPos;
    }

    boolean mouseOver(double mouseX, double mouseY, int relativeX, int relativeY, int width, int height) {
        int x = getX();
        int y = getY();
        return mouseX >= x + relativeX && mouseX < x + relativeX + width && mouseY >= y + relativeY && mouseY < y + relativeY + height;
    }
}
