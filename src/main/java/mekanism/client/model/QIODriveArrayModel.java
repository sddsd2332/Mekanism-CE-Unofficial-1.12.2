package mekanism.client.model;

import mekanism.api.NBTConstants;
import mekanism.common.Mekanism;
import mekanism.common.block.property.PropertyDriveStatus;
import mekanism.common.block.states.BlockStateFacing;
import mekanism.common.content.qio.IQIODriveItem;
import mekanism.common.content.qio.IQIODriveItem.DriveMetadata;
import mekanism.common.frequency.IFrequencyItem;
import mekanism.common.item.interfaces.IItemSustainedInventory;
import mekanism.common.tile.qio.TileEntityQIODriveArray;
import mekanism.common.tile.qio.TileEntityQIODriveArray.DriveStatus;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.block.model.IBakedModel;
import net.minecraft.client.renderer.block.model.ItemCameraTransforms;
import net.minecraft.client.renderer.block.model.ItemOverrideList;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.renderer.vertex.VertexFormat;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.ResourceLocation;
import net.minecraft.world.World;
import net.minecraftforge.client.model.IModel;
import net.minecraftforge.client.model.ModelLoader;
import net.minecraftforge.client.model.ModelLoaderRegistry;
import net.minecraftforge.common.property.IExtendedBlockState;
import net.minecraftforge.common.util.Constants.NBT;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;
import org.apache.commons.lang3.tuple.Pair;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import javax.vecmath.Matrix4f;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Adds the twelve stateful QIO drive bodies to the static drive-array model.
 * The block model receives its compact status value through an extended state;
 * the item override derives the same value from the sustained inventory.
 */
@SideOnly(Side.CLIENT)
public class QIODriveArrayModel implements IBakedModel {

    private static final float[][] DRIVE_PLACEMENTS = {
          {0, 6F / 16}, {-2F / 16, 6F / 16}, {-4F / 16, 6F / 16}, {-7F / 16, 6F / 16}, {-9F / 16, 6F / 16}, {-11F / 16, 6F / 16},
          {0, 0}, {-2F / 16, 0}, {-4F / 16, 0}, {-7F / 16, 0}, {-9F / 16, 0}, {-11F / 16, 0}
    };

    private final IBakedModel baseModel;
    private final IBakedModel[] driveModels;
    @Nullable
    private final Long fixedStatus;
    private final ItemOverrideList overrides = new DriveArrayOverrideList();

    public QIODriveArrayModel(IBakedModel baseModel, IBakedModel[] driveModels) {
        this(baseModel, driveModels, null);
    }

    private QIODriveArrayModel(IBakedModel baseModel, IBakedModel[] driveModels, @Nullable Long fixedStatus) {
        this.baseModel = baseModel;
        this.driveModels = driveModels;
        this.fixedStatus = fixedStatus;
    }

    /** Bakes the four reusable drive-state models in the requested vertex format. */
    @Nonnull
    public static IBakedModel[] bakeDriveModels(VertexFormat format) {
        IBakedModel[] models = new IBakedModel[DriveStatus.values().length];
        for (DriveStatus status : DriveStatus.values()) {
            ResourceLocation location = status.getModel();
            if (location == null) {
                continue;
            }
            try {
                IModel model = ModelLoaderRegistry.getModel(location);
                models[status.ordinal()] = model.bake(model.getDefaultState(), format, ModelLoader.defaultTextureGetter());
            } catch (Exception e) {
                Mekanism.logger.error("Unable to bake QIO drive state model {}", location, e);
            }
        }
        return models;
    }

    @Nonnull
    @Override
    public List<BakedQuad> getQuads(@Nullable IBlockState state, @Nullable EnumFacing side, long rand) {
        List<BakedQuad> baseQuads = baseModel.getQuads(state, side, rand);
        if (side != null || driveModels == null) {
            return baseQuads;
        }

        long statusData = fixedStatus == null ? getStatusData(state) : fixedStatus;
        if (statusData == 0) {
            return baseQuads;
        }

        EnumFacing facing = state == null ? EnumFacing.NORTH : getFacing(state);
        List<BakedQuad> quads = new ArrayList<>(baseQuads);
        for (int slot = 0; slot < TileEntityQIODriveArray.DRIVE_SLOTS; slot++) {
            DriveStatus status = TileEntityQIODriveArray.getStatus(slot, statusData);
            if (status == DriveStatus.NONE || status.ordinal() >= driveModels.length) {
                continue;
            }
            IBakedModel driveModel = driveModels[status.ordinal()];
            if (driveModel == null) {
                continue;
            }
            float[] placement = DRIVE_PLACEMENTS[slot];
            for (BakedQuad quad : driveModel.getQuads(null, null, rand)) {
                quads.add(transform(quad, placement[0], placement[1], facing));
            }
        }
        return quads;
    }

    private long getStatusData(@Nullable IBlockState state) {
        if (state instanceof IExtendedBlockState extended) {
            Long value = extended.getValue(PropertyDriveStatus.INSTANCE);
            return value == null ? 0 : value;
        }
        return 0;
    }

    private EnumFacing getFacing(IBlockState state) {
        try {
            EnumFacing facing = state.getValue(BlockStateFacing.facingProperty);
            return facing == null ? EnumFacing.NORTH : facing;
        } catch (RuntimeException ignored) {
            return EnumFacing.NORTH;
        }
    }

    private static BakedQuad transform(BakedQuad source, float tx, float ty, EnumFacing facing) {
        int[] data = source.getVertexData().clone();
        VertexFormat format = source.getFormat();
        int stride = format.getIntegerSize();
        int positionOffset = format.getOffset(0) / 4;
        int normalOffset = format.hasNormal() ? format.getNormalOffset() / 4 : -1;
        for (int vertex = 0; vertex < 4; vertex++) {
            int index = vertex * stride + positionOffset;
            float x = Float.intBitsToFloat(data[index]) + tx;
            float y = Float.intBitsToFloat(data[index + 1]) + ty;
            float z = Float.intBitsToFloat(data[index + 2]);
            float[] position = rotatePosition(x, y, z, facing);
            data[index] = Float.floatToRawIntBits(position[0]);
            data[index + 1] = Float.floatToRawIntBits(position[1]);
            data[index + 2] = Float.floatToRawIntBits(position[2]);
            if (normalOffset >= 0) {
                int normalIndex = vertex * stride + normalOffset;
                int packed = data[normalIndex];
                float nx = (byte) (packed & 0xFF) / 127F;
                float ny = (byte) ((packed >> 8) & 0xFF) / 127F;
                float nz = (byte) ((packed >> 16) & 0xFF) / 127F;
                float[] normal = rotateVector(nx, ny, nz, facing);
                int px = ((byte) (normal[0] * 127F)) & 0xFF;
                int py = ((byte) (normal[1] * 127F)) & 0xFF;
                int pz = ((byte) (normal[2] * 127F)) & 0xFF;
                data[normalIndex] = px | py << 8 | pz << 16 | packed & 0xFF000000;
            }
        }
        return new BakedQuad(data, source.getTintIndex(), rotateFace(source.getFace(), facing), source.getSprite(),
              source.shouldApplyDiffuseLighting(), format);
    }

    static float[] rotatePosition(float x, float y, float z, EnumFacing facing) {
        switch (facing) {
            case SOUTH:
                return new float[]{1 - x, y, 1 - z};
            case EAST:
                return new float[]{1 - z, y, x};
            case WEST:
                return new float[]{z, y, 1 - x};
            case UP:
                return new float[]{x, 1 - z, y};
            case DOWN:
                return new float[]{x, z, 1 - y};
            default:
                return new float[]{x, y, z};
        }
    }

    static float[] rotateVector(float x, float y, float z, EnumFacing facing) {
        switch (facing) {
            case SOUTH:
                return new float[]{-x, y, -z};
            case EAST:
                return new float[]{-z, y, x};
            case WEST:
                return new float[]{z, y, -x};
            case UP:
                return new float[]{x, -z, y};
            case DOWN:
                return new float[]{x, z, -y};
            default:
                return new float[]{x, y, z};
        }
    }

    private static EnumFacing rotateFace(@Nullable EnumFacing face, EnumFacing facing) {
        if (face == null) {
            return null;
        }
        EnumFacing rotated = EnumFacing.getFacingFromVector(
              rotateVector(face.getXOffset(), face.getYOffset(), face.getZOffset(), facing)[0],
              rotateVector(face.getXOffset(), face.getYOffset(), face.getZOffset(), facing)[1],
              rotateVector(face.getXOffset(), face.getYOffset(), face.getZOffset(), facing)[2]);
        return rotated == null ? face : rotated;
    }

    @Override
    public boolean isAmbientOcclusion() {
        return baseModel.isAmbientOcclusion();
    }

    @Override
    public boolean isGui3d() {
        return baseModel.isGui3d();
    }

    @Override
    public boolean isBuiltInRenderer() {
        return baseModel.isBuiltInRenderer();
    }

    @Nonnull
    @Override
    public TextureAtlasSprite getParticleTexture() {
        return baseModel.getParticleTexture();
    }

    @Nonnull
    @Override
    @SuppressWarnings("deprecation")
    public ItemCameraTransforms getItemCameraTransforms() {
        return baseModel.getItemCameraTransforms();
    }

    @Nonnull
    @Override
    public ItemOverrideList getOverrides() {
        return overrides;
    }

    @Nonnull
    @Override
    public Pair<? extends IBakedModel, Matrix4f> handlePerspective(ItemCameraTransforms.TransformType cameraTransformType) {
        return baseModel.handlePerspective(cameraTransformType);
    }

    private class DriveArrayOverrideList extends ItemOverrideList {

        private DriveArrayOverrideList() {
            super(Collections.emptyList());
        }

        @Nonnull
        @Override
        public IBakedModel handleItemState(@Nonnull IBakedModel originalModel, @Nonnull ItemStack stack, @Nullable World world,
              @Nullable EntityLivingBase entity) {
            long statusData = getItemStatusData(stack);
            return statusData == 0 ? originalModel : new QIODriveArrayModel(baseModel, driveModels, statusData);
        }
    }

    private static long getItemStatusData(ItemStack stack) {
        if (!(stack.getItem() instanceof IItemSustainedInventory sustained)) {
            return 0;
        }
        NBTTagList stored = sustained.getSustainedInventory(stack);
        if (stored == null || stored.isEmpty()) {
            return 0;
        }
        boolean hasFrequency = stack.getItem() instanceof IFrequencyItem frequencyItem && frequencyItem.getFrequency(stack) != null;
        long statusData = 0;
        for (int index = 0; index < stored.tagCount(); index++) {
            NBTTagCompound slotTag = stored.getCompoundTagAt(index);
            int slot = slotTag.getInteger(NBTConstants.SLOT);
            if (slot < 0 || slot >= TileEntityQIODriveArray.DRIVE_SLOTS || !slotTag.hasKey(NBTConstants.ITEM, NBT.TAG_COMPOUND)) {
                continue;
            }
            ItemStack drive = new ItemStack(slotTag.getCompoundTag(NBTConstants.ITEM));
            DriveStatus status;
            if (drive.isEmpty()) {
                status = DriveStatus.NONE;
            } else if (!(drive.getItem() instanceof IQIODriveItem driveItem)) {
                status = DriveStatus.INVALID;
            } else if (!hasFrequency) {
                status = DriveStatus.OFFLINE;
            } else {
                DriveMetadata metadata = driveItem.getDriveMetadata(drive);
                long capacity = driveItem.getStorageCapacity(drive);
                int typeCapacity = driveItem.getTypeCapacity(drive);
                if (capacity <= 0 || metadata.getStorageUnits() >= capacity) {
                    status = DriveStatus.FULL;
                } else if (metadata.getTypes() >= typeCapacity || metadata.getStorageUnits() >= capacity - capacity / 4) {
                    status = DriveStatus.NEAR_FULL;
                } else {
                    status = DriveStatus.READY;
                }
            }
            statusData = TileEntityQIODriveArray.updateStatus(slot, status, statusData);
        }
        return statusData;
    }
}
