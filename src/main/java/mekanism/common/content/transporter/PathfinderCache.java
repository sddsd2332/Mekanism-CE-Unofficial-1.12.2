package mekanism.common.content.transporter;

import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import mekanism.api.Coord4D;
import net.minecraft.util.EnumFacing;

import java.util.List;
import java.util.Map;
import java.util.Set;

public class PathfinderCache {

    private static Map<PathData, List<Coord4D>> cachedPaths = new Object2ObjectOpenHashMap<>();
    private static long revision;

    public static long getRevision() {
        return revision;
    }

    public static void onChanged(Coord4D location) {
        reset();
    }

    public static void addCachedPath(PathData data, List<Coord4D> coords) {
        cachedPaths.put(data, coords);
    }

    public static List<Coord4D> getCache(Coord4D start, Coord4D end, Set<EnumFacing> sides) {
        List<Coord4D> ret = null;
        for (EnumFacing side : sides) {
            List<Coord4D> test = cachedPaths.get(new PathData(start, end, side));
            if (ret == null || (test != null && test.size() < ret.size())) {
                ret = test;
            }
        }
        return ret;
    }

    public static void reset() {
        revision++;
        cachedPaths.clear();
    }

    public static List<Coord4D> getSingleCache(Coord4D start, Coord4D end, EnumFacing side) {
        return cachedPaths.get(new PathData(start, end, side));
    }

    public static void removeCachedPath(Coord4D start, Coord4D end, EnumFacing side) {
        revision++;
        cachedPaths.remove(new PathData(start, end, side));
    }

    public static class PathData {

        private final Coord4D startTransporter;
        private final Coord4D end;
        private final EnumFacing endSide;
        private final int hash;

        public PathData(Coord4D s, Coord4D e, EnumFacing es) {
            startTransporter = s;
            end = e;
            endSide = es;
            int code = 1;
            code = 31 * code + startTransporter.hashCode();
            code = 31 * code + end.hashCode();
            code = 31 * code + endSide.hashCode();
            hash = code;
        }

        @Override
        public boolean equals(Object obj) {
            if (obj instanceof PathData data) {
                return data.startTransporter.equals(startTransporter) && data.end.equals(end) && data.endSide.equals(endSide);
            }
            return false;
        }

        @Override
        public int hashCode() {
            return hash;
        }
    }
}
