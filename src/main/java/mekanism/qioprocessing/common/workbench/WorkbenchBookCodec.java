package mekanism.qioprocessing.common.workbench;

import mekanism.qioprocessing.common.pattern.VerifiedWorkbenchPattern;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.nbt.NBTTagString;

import java.util.*;

/**
 * User configuration persistence, not a recipe-directory disk cache. No ItemStack or registry access.
 */
public final class WorkbenchBookCodec {
    private static final String FORMAT = "mekanismqioprocessing:workbench_book";
    private static final int VERSION = 2;
    private static final java.util.regex.Pattern HASH = java.util.regex.Pattern.compile("[a-f0-9]{64}");

    private WorkbenchBookCodec() {
    }

    public static NBTTagCompound write(WorkbenchSnapshot snapshot) {
        NBTTagCompound root = new NBTTagCompound();
        root.setString("format", FORMAT);
        root.setInteger("version", VERSION);
        root.setUniqueId("id", snapshot.id());
        root.setLong("revision", snapshot.revision());
        NBTTagList routes = new NBTTagList();
        for (WorkbenchRoute route : snapshot.routes().values()) {
            NBTTagCompound tag = new NBTTagCompound();
            tag.setString("recipe", route.recipeId());
            tag.setString("signature", route.signature());
            tag.setBoolean("enabled", route.enabled());
            NBTTagList choices = new NBTTagList();
            for (Map.Entry<Integer, List<String>> entry : route.choices().entrySet()) {
                NBTTagCompound choice = new NBTTagCompound();
                choice.setInteger("slot", entry.getKey());
                NBTTagList keys = new NBTTagList();
                for (String key : entry.getValue()) keys.appendTag(new NBTTagString(key));
                choice.setTag("keys", keys);
                choices.appendTag(choice);
            }
            tag.setTag("choices", choices);
            NBTTagList patterns = new NBTTagList();
            for (VerifiedWorkbenchPattern pattern : route.patterns().values()) patterns.appendTag(pattern.write());
            tag.setTag("patterns", patterns);
            routes.appendTag(tag);
        }
        root.setTag("routes", routes);
        NBTTagList preferences = new NBTTagList();
        snapshot.preferences().forEach((family, recipe) -> {
            NBTTagCompound tag = new NBTTagCompound();
            tag.setString("family", family);
            tag.setString("recipe", recipe);
            preferences.appendTag(tag);
        });
        root.setTag("preferences", preferences);
        return root;
    }

    public static WorkbenchSnapshot read(NBTTagCompound root) {
        if (!FORMAT.equals(string(root, "format")) || !root.hasKey("version", 3)
                || (root.getInteger("version") != 1 && root.getInteger("version") != VERSION)) {
            throw invalid("Unsupported workbench format/version");
        }
        if (!root.hasKey("idMost", 4) || !root.hasKey("idLeast", 4) || !root.hasKey("revision", 4))
            throw invalid("Missing identity/revision");
        UUID id = root.getUniqueId("id");
        long revision = root.getLong("revision");
        if (revision < 0) throw invalid("Negative revision");
        Map<String, WorkbenchRoute> routes = new LinkedHashMap<>();
        NBTTagList routeTags = list(root, "routes", 10);
        int totalPatterns = 0;
        long totalPatternBytes = 0;
        for (int i = 0; i < routeTags.tagCount(); i++) {
            NBTTagCompound tag = routeTags.getCompoundTagAt(i);
            String recipe = recipeId(string(tag, "recipe")), signature = fingerprint(string(tag, "signature"));
            if (!tag.hasKey("enabled", 1) || (tag.getByte("enabled") != 0 && tag.getByte("enabled") != 1))
                throw invalid("Invalid enabled flag");
            Map<Integer, List<String>> choices = new LinkedHashMap<>();
            NBTTagList choiceTags = list(tag, "choices", 10);
            for (int j = 0; j < choiceTags.tagCount(); j++) {
                NBTTagCompound choice = choiceTags.getCompoundTagAt(j);
                if (!choice.hasKey("slot", 3)) throw invalid("Missing slot");
                int slot = choice.getInteger("slot");
                if (slot < 0 || slot >= 9) throw invalid("Invalid slot");
                NBTTagList keyTags = list(choice, "keys", 8);
                if (keyTags.isEmpty()) throw invalid("No enabled candidates");
                List<String> keys = new ArrayList<>();
                HashSet<String> seen = new HashSet<>();
                for (int k = 0; k < keyTags.tagCount(); k++) {
                    String key = fingerprint(keyTags.getStringTagAt(k));
                    if (!seen.add(key)) throw invalid("Duplicate candidate");
                    keys.add(key);
                }
                if (choices.put(slot, keys) != null) throw invalid("Duplicate slot");
            }
            Map<UUID, VerifiedWorkbenchPattern> patterns = new LinkedHashMap<>();
            if (root.getInteger("version") >= 2) {
                NBTTagList patternTags = list(tag, "patterns", 10);
                if (patternTags.tagCount() > WorkbenchSnapshot.MAX_PATTERNS_PER_ROUTE
                        || (totalPatterns += patternTags.tagCount()) > WorkbenchSnapshot.MAX_PATTERNS_PER_BOOK)
                    throw invalid("Stored pattern count quota exceeded");
                for (int j = 0; j < patternTags.tagCount(); j++) {
                    VerifiedWorkbenchPattern pattern;
                    try {
                        pattern = VerifiedWorkbenchPattern.read(patternTags.getCompoundTagAt(j));
                    } catch (RuntimeException error) {
                        throw invalid("Invalid verified pattern");
                    }
                    if (!recipe.equals(pattern.recipeId()) || !signature.equals(pattern.declarationSignature())
                            || patterns.put(pattern.id(), pattern) != null)
                        throw invalid("Pattern route mismatch/duplicate");
                    totalPatternBytes += pattern.retainedBytes();
                    if (totalPatternBytes > WorkbenchSnapshot.MAX_PATTERN_BYTES_PER_BOOK)
                        throw invalid("Stored pattern memory quota exceeded");
                }
            }
            if (routes.put(recipe, new WorkbenchRoute(recipe, signature, tag.getBoolean("enabled"), choices, patterns)) != null)
                throw invalid("Duplicate recipe");
        }
        Map<String, String> preferences = new LinkedHashMap<>();
        NBTTagList preferenceTags = list(root, "preferences", 10);
        for (int i = 0; i < preferenceTags.tagCount(); i++) {
            NBTTagCompound tag = preferenceTags.getCompoundTagAt(i);
            String family = string(tag, "family"), recipe = recipeId(string(tag, "recipe"));
            int separator = family.lastIndexOf('@');
            if (separator < 0) throw invalid("Invalid output family");
            recipeId(family.substring(0, separator));
            try {
                Integer.parseInt(family.substring(separator + 1));
            } catch (NumberFormatException error) {
                throw invalid("Invalid metadata");
            }
            if (preferences.put(family, recipe) != null) throw invalid("Duplicate preference");
        }
        return new WorkbenchSnapshot(id, revision, routes, preferences);
    }

    private static String string(NBTTagCompound tag, String key) {
        if (!tag.hasKey(key, 8)) throw invalid("Missing string: " + key);
        return tag.getString(key);
    }

    private static NBTTagList list(NBTTagCompound tag, String key, int type) {
        if (!tag.hasKey(key, 9)) throw invalid("Missing list: " + key);
        NBTTagList list = (NBTTagList) tag.getTag(key);
        if (!list.isEmpty() && list.getTagType() != type) throw invalid("Incorrect list element type: " + key);
        return list;
    }

    private static String recipeId(String id) {
        // 1.12 ResourceLocation accepts legacy path characters that modern versions reject.
        // These IDs are map keys, never filesystem paths. Preserve anything the registry can round-trip.
        int colon = id.indexOf(':');
        if (colon <= 0 || colon == id.length() - 1 || !new net.minecraft.util.ResourceLocation(id).toString().equals(id)) {
            throw invalid("Invalid registry name");
        }
        return id;
    }

    private static String fingerprint(String value) {
        if (!HASH.matcher(value).matches()) throw invalid("Invalid signature");
        return value;
    }

    private static IllegalArgumentException invalid(String message) {
        return new IllegalArgumentException(message);
    }
}

