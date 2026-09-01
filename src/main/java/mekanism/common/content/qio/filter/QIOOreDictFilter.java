package mekanism.common.content.qio.filter;

import mekanism.common.content.filter.IOreDictFilter;
import mekanism.common.content.qio.QIOResourceEntry;
import mekanism.common.content.qio.QIOResourceKind;
import mekanism.api.qio.resource.QIOResourceCodecs;
import mekanism.api.qio.resource.QIOResourceFamilyMatcher;
import mekanism.common.content.transporter.TransporterFilter;
import mekanism.common.lib.inventory.Finder;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;

/** Item filter using the 1.12 Ore Dictionary as the tag-system adapter. */
public class QIOOreDictFilter extends QIOFilter implements IOreDictFilter {

    public static final String TYPE = "oredict";
    private String oreDictName = "";

    public QIOOreDictFilter() {
    }

    public QIOOreDictFilter(String oreDictName) {
        setOreDictName(oreDictName);
    }

    @Override
    public QIOResourceFamilyMatcher getMatcher() {
        return QIOResourceFamilyMatcher.family(QIOResourceCodecs.ITEM_FAMILY);
    }

    @Override
    public boolean matches(QIOResourceEntry entry) {
        return matches(entry.getItem());
    }

    @Override
    public boolean matches(ItemStack stack) {
        return hasFilter() && stack != null && Finder.oreDict(oreDictName).modifies(stack);
    }

    @Override
    public String getType() {
        return TYPE;
    }

    @Override
    public boolean hasFilter() {
        return !oreDictName.isEmpty() && oreDictName.length() <= TransporterFilter.MAX_LENGTH;
    }

    @Override
    public void writePayload(NBTTagCompound data) {
        data.setString("oreDictName", oreDictName);
    }

    @Override
    protected void readPayload(NBTTagCompound data) {
        setOreDictName(data.getString("oreDictName"));
    }

    @Override
    public void setOreDictName(String name) {
        oreDictName = normalize(name);
    }

    @Override
    public String getOreDictName() {
        return oreDictName;
    }

    private static String normalize(String value) {
        if (value == null) {
            return "";
        }
        String trimmed = value.trim();
        return trimmed.length() > TransporterFilter.MAX_LENGTH ? "" : trimmed;
    }
}
