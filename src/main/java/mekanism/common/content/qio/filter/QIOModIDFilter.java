package mekanism.common.content.qio.filter;

import mekanism.common.content.filter.IModIDFilter;
import mekanism.common.content.qio.QIOResourceEntry;
import mekanism.common.content.qio.QIOResourceKind;
import mekanism.common.content.transporter.TransporterFilter;
import mekanism.common.lib.WildcardMatcher;
import mekanism.common.util.MekanismUtils;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;

/** Item filter matching the registry namespace with 1.12 wildcard rules. */
public class QIOModIDFilter extends QIOFilter implements IModIDFilter {

    public static final String TYPE = "modid";
    private String modID = "";

    public QIOModIDFilter() {
    }

    public QIOModIDFilter(String modID) {
        setModID(modID);
    }

    @Override
    public QIOResourceKind getKind() {
        return QIOResourceKind.ITEM;
    }

    @Override
    public boolean matches(QIOResourceEntry entry) {
        return matches(entry.getItem());
    }

    @Override
    public boolean matches(ItemStack stack) {
        return hasFilter() && stack != null && !stack.isEmpty() &&
              WildcardMatcher.matches(modID, MekanismUtils.getModId(stack));
    }

    @Override
    public String getType() {
        return TYPE;
    }

    @Override
    public boolean hasFilter() {
        return !modID.isEmpty() && modID.length() <= TransporterFilter.MAX_LENGTH;
    }

    @Override
    public void writePayload(NBTTagCompound data) {
        data.setString("modID", modID);
    }

    @Override
    protected void readPayload(NBTTagCompound data) {
        setModID(data.getString("modID"));
    }

    @Override
    public void setModID(String id) {
        modID = normalize(id);
    }

    @Override
    public String getModID() {
        return modID;
    }

    private static String normalize(String value) {
        if (value == null) {
            return "";
        }
        String trimmed = value.trim().toLowerCase(java.util.Locale.ROOT);
        return trimmed.length() > TransporterFilter.MAX_LENGTH ? "" : trimmed;
    }
}
