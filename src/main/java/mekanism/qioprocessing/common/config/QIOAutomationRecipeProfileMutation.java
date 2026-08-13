package mekanism.qioprocessing.common.config;

import mekanism.qioprocessing.common.content.QIOProcessingDataException;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraftforge.common.util.Constants.NBT;

import javax.annotation.Nonnull;
import java.util.Objects;

/** One validated client request against the active QIO machine recipe profile. */
public final class QIOAutomationRecipeProfileMutation {

    private static final int SCHEMA_VERSION = 1;
    private static final int MAX_KEY_LENGTH = 8_192;

    public enum Action {
        TOGGLE_ROUTE,
        TOGGLE_PRODUCT,
        DISABLE_PRODUCT,
        MOVE_PRODUCT,
        MOVE_ROUTE,
        MOVE_PRODUCT_TO_TOP,
        MOVE_PRODUCT_TO_BOTTOM,
        MOVE_ROUTE_TO_TOP,
        MOVE_ROUTE_TO_BOTTOM,
        SET_GLOBAL_CRAFT_AMOUNT,
        SET_ROUTE_CRAFT_AMOUNT,
        CLEAR_ROUTE_CRAFT_AMOUNT,
        RESET_PRODUCT,
        RESET_ALL,
        TOGGLE_PROFILE_MODE,
        CYCLE_GLOBAL_PROFILE,
        TOGGLE_ROUTE_FILTER_MODE,
        SET_ALL_ROUTES_ENABLED
    }

    private final Action action;
    private final String productKey;
    private final String routeKey;
    private final int direction;
    private final long amount;

    public QIOAutomationRecipeProfileMutation(@Nonnull Action action,
          String productKey, String routeKey, int direction, long amount) {
        this.action = Objects.requireNonNull(action, "action");
        this.productKey = checkedKey(productKey);
        this.routeKey = checkedKey(routeKey);
        this.direction = direction;
        this.amount = amount;
        if (!isStructurallyValid()) {
            throw new IllegalArgumentException("Invalid QIO automation profile mutation shape");
        }
    }

    @Nonnull
    public static QIOAutomationRecipeProfileMutation simple(@Nonnull Action action) {
        return new QIOAutomationRecipeProfileMutation(action, "", "", 0, 0);
    }

    @Nonnull
    public static QIOAutomationRecipeProfileMutation product(@Nonnull Action action,
          @Nonnull String productKey) {
        return new QIOAutomationRecipeProfileMutation(action, productKey, "", 0, 0);
    }

    @Nonnull
    public static QIOAutomationRecipeProfileMutation route(@Nonnull Action action,
          @Nonnull String productKey, @Nonnull String routeKey) {
        return new QIOAutomationRecipeProfileMutation(action, productKey, routeKey, 0, 0);
    }

    @Nonnull
    public static QIOAutomationRecipeProfileMutation moveProduct(@Nonnull String productKey,
          int direction) {
        return new QIOAutomationRecipeProfileMutation(Action.MOVE_PRODUCT, productKey, "",
              direction, 0);
    }

    @Nonnull
    public static QIOAutomationRecipeProfileMutation moveRoute(@Nonnull String productKey,
          @Nonnull String routeKey, int direction) {
        return new QIOAutomationRecipeProfileMutation(Action.MOVE_ROUTE, productKey, routeKey,
              direction, 0);
    }

    @Nonnull
    public static QIOAutomationRecipeProfileMutation amount(@Nonnull Action action,
          String routeKey, long amount) {
        return new QIOAutomationRecipeProfileMutation(action, "", routeKey, 0, amount);
    }

    @Nonnull
    public static QIOAutomationRecipeProfileMutation cycleGlobalProfile(int direction) {
        return new QIOAutomationRecipeProfileMutation(Action.CYCLE_GLOBAL_PROFILE, "", "",
              direction, 0);
    }

    @Nonnull
    public static QIOAutomationRecipeProfileMutation setAllRoutesEnabled(boolean enabled) {
        return new QIOAutomationRecipeProfileMutation(Action.SET_ALL_ROUTES_ENABLED, "", "",
              enabled ? 1 : -1, 0);
    }

    @Nonnull
    public Action getAction() {
        return action;
    }

    @Nonnull
    public String getProductKey() {
        return productKey;
    }

    @Nonnull
    public String getRouteKey() {
        return routeKey;
    }

    public int getDirection() {
        return direction;
    }

    public long getAmount() {
        return amount;
    }

    @Nonnull
    public NBTTagCompound write() {
        NBTTagCompound data = new NBTTagCompound();
        data.setInteger("schema", SCHEMA_VERSION);
        data.setString("action", action.name());
        data.setString("productKey", productKey);
        data.setString("routeKey", routeKey);
        data.setInteger("direction", direction);
        data.setLong("amount", amount);
        return data;
    }

    @Nonnull
    public static QIOAutomationRecipeProfileMutation read(@Nonnull NBTTagCompound data)
          throws QIOProcessingDataException {
        try {
            if (data.getInteger("schema") != SCHEMA_VERSION ||
                !data.hasKey("action", NBT.TAG_STRING) ||
                !data.hasKey("productKey", NBT.TAG_STRING) ||
                !data.hasKey("routeKey", NBT.TAG_STRING) ||
                !data.hasKey("direction", NBT.TAG_INT) ||
                !data.hasKey("amount", NBT.TAG_LONG)) {
                throw new QIOProcessingDataException("QIO automation profile mutation is incomplete");
            }
            return new QIOAutomationRecipeProfileMutation(Action.valueOf(
                  data.getString("action")), data.getString("productKey"),
                  data.getString("routeKey"), data.getInteger("direction"),
                  data.getLong("amount"));
        } catch (QIOProcessingDataException e) {
            throw e;
        } catch (RuntimeException e) {
            throw new QIOProcessingDataException("Invalid QIO automation profile mutation", e);
        }
    }

    private boolean isStructurallyValid() {
        boolean product = !productKey.isEmpty();
        boolean route = !routeKey.isEmpty();
        return switch (action) {
            case TOGGLE_ROUTE, MOVE_ROUTE_TO_TOP, MOVE_ROUTE_TO_BOTTOM ->
                  product && route && direction == 0 && amount == 0;
            case TOGGLE_PRODUCT, DISABLE_PRODUCT, MOVE_PRODUCT_TO_TOP,
                 MOVE_PRODUCT_TO_BOTTOM, RESET_PRODUCT ->
                  product && !route && direction == 0 && amount == 0;
            case MOVE_PRODUCT -> product && !route && direction != 0 && amount == 0;
            case MOVE_ROUTE -> product && route && direction != 0 && amount == 0;
            case SET_GLOBAL_CRAFT_AMOUNT -> !product && !route && direction == 0 && amount > 0;
            case SET_ROUTE_CRAFT_AMOUNT -> !product && route && direction == 0 && amount > 0;
            case CLEAR_ROUTE_CRAFT_AMOUNT -> !product && route && direction == 0 && amount == 0;
            case CYCLE_GLOBAL_PROFILE, SET_ALL_ROUTES_ENABLED ->
                  !product && !route && direction != 0 && amount == 0;
            case RESET_ALL, TOGGLE_PROFILE_MODE, TOGGLE_ROUTE_FILTER_MODE ->
                  !product && !route && direction == 0 && amount == 0;
        };
    }

    private static String checkedKey(String value) {
        String checked = value == null ? "" : value;
        if (checked.length() > MAX_KEY_LENGTH) {
            throw new IllegalArgumentException("QIO profile mutation key is too long");
        }
        return checked;
    }
}
