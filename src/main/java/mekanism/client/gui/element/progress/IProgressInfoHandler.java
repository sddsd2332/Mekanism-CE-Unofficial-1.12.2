package mekanism.client.gui.element.progress;

public interface IProgressInfoHandler {

    double getProgress();

    default boolean isActive() {
        return true;
    }

    default boolean isGuiInJei() {
        return false;
    }

    interface IBooleanProgressInfoHandler extends IProgressInfoHandler {

        boolean fillProgressBar();

        @Override
        default double getProgress() {
            return fillProgressBar() ? 1 : 0;
        }
    }
}