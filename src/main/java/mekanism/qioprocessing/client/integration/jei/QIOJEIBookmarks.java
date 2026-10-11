package mekanism.qioprocessing.client.integration.jei;

import mekanism.qioprocessing.common.util.QIORecipeStackUtils;
import net.minecraft.item.ItemStack;

import javax.annotation.Nullable;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.function.Predicate;

/**
 * Read-only compatibility boundary: JEI exposes hover, but not the full bookmark list, in its API.
 */
final class QIOJEIBookmarks {
    private static final Method BOOKMARK_LIST = method("mezz.jei.Internal", "getBookmarkList");
    private static final Method INGREDIENT_LIST = BOOKMARK_LIST == null ? null :
            method(BOOKMARK_LIST.getReturnType(), "getIngredientList");
    private static final Method ELEMENT_INGREDIENT = method("mezz.jei.gui.ingredients.IIngredientListElement", "getIngredient");
    private static final Field BOOKMARK_INGREDIENT = bookmarkIngredient();

    private QIOJEIBookmarks() {
    }

    static boolean supportsImport() {
        return BOOKMARK_LIST != null && INGREDIENT_LIST != null;
    }

    /**
     * Reads all pages/groups without changing the user's bookmarks. -1 means an unavailable adapter.
     */
    static int importBookmarks(Predicate<ItemStack> addTarget, int remaining) {
        if (remaining <= 0) return 0;
        if (!supportsImport()) return -1;
        Object entries;
        try {
            Object bookmarks = BOOKMARK_LIST.invoke(null);
            entries = bookmarks == null ? null : INGREDIENT_LIST.invoke(bookmarks);
        } catch (ReflectiveOperationException | RuntimeException | LinkageError unavailable) {
            return -1;
        }
        return entries instanceof Iterable<?> values ? importEntries(values, addTarget, remaining) : -1;
    }

    static int importEntries(Iterable<?> entries, Predicate<ItemStack> addTarget, int remaining) {
        if (remaining <= 0) return 0;
        int added = 0;
        for (Object entry : entries) {
            ItemStack stack = copyTarget(entry);
            // The existing target list owns deduplication and its capacity; duplicates must
            // not consume this import's allowance or hide later bookmarks on another page.
            if (!stack.isEmpty() && addTarget.test(stack) && ++added >= remaining) break;
        }
        return added;
    }

    static ItemStack copyTarget(@Nullable Object ingredient) {
        try {
            // JEI returns raw stacks; newer HEI returns BookmarkItem (including recipe and
            // dummy bookmarks). List entries introduce one further ingredient-element layer.
            for (int depth = 0; ingredient != null && depth < 8; depth++) {
                if (ingredient instanceof ItemStack stack) {
                    return stack.isEmpty() ? ItemStack.EMPTY : QIORecipeStackUtils.copyComplete(stack, 1);
                }
                Object next;
                if (BOOKMARK_INGREDIENT != null && BOOKMARK_INGREDIENT.getDeclaringClass().isInstance(ingredient)) {
                    next = BOOKMARK_INGREDIENT.get(ingredient);
                } else if (ELEMENT_INGREDIENT != null && ELEMENT_INGREDIENT.getDeclaringClass().isInstance(ingredient)) {
                    next = ELEMENT_INGREDIENT.invoke(ingredient);
                } else return ItemStack.EMPTY;
                if (next == ingredient) return ItemStack.EMPTY;
                ingredient = next;
            }
        } catch (ReflectiveOperationException | RuntimeException | LinkageError unavailable) {
            // One unsupported ingredient must not prevent importing the other bookmarks.
        }
        return ItemStack.EMPTY;
    }

    @Nullable
    private static Method method(String type, String name) {
        try {
            return method(Class.forName(type, false, QIOJEIBookmarks.class.getClassLoader()), name);
        } catch (ClassNotFoundException | RuntimeException | LinkageError unavailable) {
            return null;
        }
    }

    @Nullable
    private static Method method(Class<?> type, String name) {
        try {
            return type.getMethod(name);
        } catch (NoSuchMethodException | RuntimeException | LinkageError unavailable) {
            return null;
        }
    }

    @Nullable
    private static Field bookmarkIngredient() {
        try {
            return Class.forName("mezz.jei.bookmarks.BookmarkItem", false,
                    QIOJEIBookmarks.class.getClassLoader()).getField("ingredient");
        } catch (ClassNotFoundException | NoSuchFieldException | RuntimeException | LinkageError unavailable) {
            return null;
        }
    }
}
