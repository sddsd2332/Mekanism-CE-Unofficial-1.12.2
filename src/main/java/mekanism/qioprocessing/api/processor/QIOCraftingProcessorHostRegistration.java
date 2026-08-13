package mekanism.qioprocessing.api.processor;

import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.ResourceLocation;

import javax.annotation.Nonnull;
import java.util.Locale;
import java.util.Objects;
import java.util.function.Function;

/**
 * Immutable registration describing one concrete family of QIO crafting processor hosts.
 * The resolver may select between registered definitions, but cannot create definitions at runtime.
 */
public final class QIOCraftingProcessorHostRegistration<T extends TileEntity> {

    private final ResourceLocation hostId;
    private final String ownerModId;
    private final Class<T> tileClass;
    private final Function<? super T, ResourceLocation> definitionResolver;

    public QIOCraftingProcessorHostRegistration(@Nonnull ResourceLocation hostId,
          @Nonnull String ownerModId, @Nonnull Class<T> tileClass,
          @Nonnull Function<? super T, ResourceLocation> definitionResolver) {
        this.hostId = Objects.requireNonNull(hostId, "hostId");
        this.ownerModId = validateOwnerModId(ownerModId);
        if (!hostId.getNamespace().equals(this.ownerModId)) {
            throw new IllegalArgumentException("QIO processor host namespace must match its owner mod id");
        }
        this.tileClass = Objects.requireNonNull(tileClass, "tileClass");
        this.definitionResolver = Objects.requireNonNull(definitionResolver, "definitionResolver");
    }

    @Nonnull
    public static <T extends TileEntity> QIOCraftingProcessorHostRegistration<T> fixed(
          @Nonnull ResourceLocation hostId, @Nonnull String ownerModId,
          @Nonnull Class<T> tileClass, @Nonnull ResourceLocation definitionId) {
        Objects.requireNonNull(definitionId, "definitionId");
        return new QIOCraftingProcessorHostRegistration<>(hostId, ownerModId, tileClass,
              ignored -> definitionId);
    }

    @Nonnull
    public ResourceLocation getHostId() {
        return hostId;
    }

    @Nonnull
    public String getOwnerModId() {
        return ownerModId;
    }

    @Nonnull
    public Class<T> getTileClass() {
        return tileClass;
    }

    @Nonnull
    ResourceLocation resolveDefinitionId(@Nonnull TileEntity tile) {
        if (!tileClass.isInstance(tile)) {
            throw new IllegalArgumentException("Tile does not match QIO processor host " + hostId);
        }
        ResourceLocation definitionId = definitionResolver.apply(tileClass.cast(tile));
        return Objects.requireNonNull(definitionId,
              "QIO processor definition resolver returned null for " + hostId);
    }

    private static String validateOwnerModId(String ownerModId) {
        String checked = Objects.requireNonNull(ownerModId, "ownerModId").toLowerCase(Locale.ROOT);
        if (!checked.equals(ownerModId) || checked.isEmpty()) {
            throw new IllegalArgumentException("QIO processor owner mod id must be canonical lowercase");
        }
        // ResourceLocation performs Forge/Minecraft's namespace validation.
        new ResourceLocation(checked, "processor_host_validation");
        return checked;
    }
}
