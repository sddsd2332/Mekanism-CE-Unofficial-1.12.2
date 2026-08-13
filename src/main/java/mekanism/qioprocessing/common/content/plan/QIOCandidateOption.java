package mekanism.qioprocessing.common.content.plan;

import mekanism.qioprocessing.api.resource.PortableResourceDescriptor;
import mekanism.qioprocessing.common.content.QIOProcessingDataException;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraftforge.common.util.Constants.NBT;

import javax.annotation.Nonnull;
import java.util.Locale;
import java.util.Objects;

/** One enabled exact resource that can satisfy one logical workbench ingredient unit. */
public final class QIOCandidateOption {

    private static final int HASH_LENGTH = 64;
    private final String candidateId;
    private final PortableResourceDescriptor resource;
    private final long amountPerUnit;
    private final boolean virtualFluid;

    public QIOCandidateOption(@Nonnull String candidateId,
          @Nonnull PortableResourceDescriptor resource, long amountPerUnit,
          boolean virtualFluid) {
        String checkedId = Objects.requireNonNull(candidateId, "candidateId").trim()
              .toLowerCase(Locale.ROOT);
        if (checkedId.length() != HASH_LENGTH || !checkedId.chars().allMatch(character ->
              character >= '0' && character <= '9' || character >= 'a' && character <= 'f')) {
            throw new IllegalArgumentException("Invalid QIO candidate option identity");
        }
        if (amountPerUnit <= 0) {
            throw new IllegalArgumentException("QIO candidate option amount must be positive");
        }
        this.candidateId = checkedId;
        this.resource = Objects.requireNonNull(resource, "resource");
        this.amountPerUnit = amountPerUnit;
        this.virtualFluid = virtualFluid;
    }

    @Nonnull public String getCandidateId() { return candidateId; }
    @Nonnull public PortableResourceDescriptor getResource() { return resource; }
    public long getAmountPerUnit() { return amountPerUnit; }
    public boolean isVirtualFluid() { return virtualFluid; }

    @Nonnull
    public NBTTagCompound write() {
        NBTTagCompound data = new NBTTagCompound();
        data.setString("candidateId", candidateId);
        data.setTag("resource", resource.write());
        data.setLong("amountPerUnit", amountPerUnit);
        data.setBoolean("virtualFluid", virtualFluid);
        return data;
    }

    @Nonnull
    public static QIOCandidateOption read(@Nonnull NBTTagCompound data)
          throws QIOProcessingDataException {
        try {
            if (!data.hasKey("candidateId", NBT.TAG_STRING) ||
                !data.hasKey("resource", NBT.TAG_COMPOUND) ||
                !data.hasKey("amountPerUnit", NBT.TAG_LONG) ||
                !data.hasKey("virtualFluid", NBT.TAG_BYTE)) {
                throw new QIOProcessingDataException("Incomplete QIO candidate option");
            }
            return new QIOCandidateOption(data.getString("candidateId"),
                  PortableResourceDescriptor.read(data.getCompoundTag("resource")),
                  data.getLong("amountPerUnit"), data.getBoolean("virtualFluid"));
        } catch (QIOProcessingDataException e) {
            throw e;
        } catch (RuntimeException e) {
            throw new QIOProcessingDataException("Invalid QIO candidate option", e);
        }
    }
}
