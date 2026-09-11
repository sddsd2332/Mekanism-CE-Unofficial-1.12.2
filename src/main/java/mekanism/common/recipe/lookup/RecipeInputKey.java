package mekanism.common.recipe.lookup;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/** Detached input shape, original map hash and full values used after candidate location. */
public final class RecipeInputKey {
    public enum Shape {
        ITEM(1), ADVANCED(2), DOUBLE_ITEM(2), INFUSION(2), GAS(1), FLUID(1), GAS_FLUID(2),
        CHEMICAL_PAIR(2), PRESSURIZED(3), NUCLEOSYNTHESIZER(2), CHEMICAL_TEMPLATE(2),
        FARM_GAS(2), FARM_FLUID(2), ROTARY(2), INTEGER(0);

        private final int arity;
        Shape(int arity) { this.arity = arity; }
    }

    private final Shape shape;
    private final List<ResourceIdentity> resources;
    private final int scalar;
    private final int mapHash;
    private final int ignoreItemNbtMask;
    private final boolean valid;
    private final int hash;

    public RecipeInputKey(Shape shape, List<ResourceIdentity> resources, int scalar,
          int mapHash, int ignoreItemNbtMask, boolean valid) {
        this.shape = Objects.requireNonNull(shape, "Recipe input shape");
        if (resources.size() != shape.arity || ignoreItemNbtMask < 0 || (ignoreItemNbtMask >>> shape.arity) != 0) {
            throw new IllegalArgumentException("Invalid typed input arity or NBT rule");
        }
        List<ResourceIdentity> copy = new ArrayList<>(resources.size());
        for (ResourceIdentity resource : resources) copy.add(Objects.requireNonNull(resource, "Recipe input identity"));
        this.resources = Collections.unmodifiableList(copy);
        this.scalar = scalar;
        this.mapHash = mapHash;
        this.ignoreItemNbtMask = ignoreItemNbtMask;
        this.valid = valid;
        hash = Objects.hash(shape, this.resources, scalar, mapHash, ignoreItemNbtMask, valid);
    }

    public Shape getShape() { return shape; }
    public List<ResourceIdentity> getResources() { return resources; }
    public int getScalar() { return scalar; }
    public int getMapHash() { return mapHash; }
    public boolean isValid() { return valid; }

    /** Mirrors query.testEquality(mapKey); hash equality alone never proves a match. */
    public boolean matches(RecipeInputKey definition) {
        if (definition == null || !valid || !definition.valid || shape != definition.shape) return false;
        if (shape == Shape.INTEGER) return scalar == definition.scalar;
        if (matchesInOrder(definition)) return true;
        return shape == Shape.CHEMICAL_PAIR &&
              resources.get(0).matchesIngredient(definition.resources.get(1), false) &&
              resources.get(1).matchesIngredient(definition.resources.get(0), false);
    }

    private boolean matchesInOrder(RecipeInputKey definition) {
        for (int i = 0; i < resources.size(); i++) {
            if (!resources.get(i).matchesIngredient(definition.resources.get(i), (ignoreItemNbtMask & (1 << i)) != 0)) return false;
        }
        return true;
    }

    @Override public int hashCode() { return hash; }
    @Override public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof RecipeInputKey)) return false;
        RecipeInputKey input = (RecipeInputKey) other;
        return shape == input.shape && mapHash == input.mapHash && scalar == input.scalar && valid == input.valid &&
              ignoreItemNbtMask == input.ignoreItemNbtMask && resources.equals(input.resources);
    }
}
