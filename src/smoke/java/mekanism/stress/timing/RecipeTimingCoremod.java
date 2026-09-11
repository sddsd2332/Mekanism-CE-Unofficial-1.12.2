package mekanism.stress.timing;

import net.minecraftforge.fml.relauncher.IFMLLoadingPlugin;
import java.util.Map;

@IFMLLoadingPlugin.Name("RecipeCacheBaselineTiming")
@IFMLLoadingPlugin.MCVersion("1.12.2")
@IFMLLoadingPlugin.SortingIndex(10001)
@IFMLLoadingPlugin.TransformerExclusions("mekanism.stress.timing.")
public final class RecipeTimingCoremod implements IFMLLoadingPlugin {
    public String[] getASMTransformerClass() { return new String[]{"mekanism.stress.timing.RecipeTimingTransformer"}; }
    public String getModContainerClass() { return null; }
    public String getSetupClass() { return null; }
    public void injectData(Map<String, Object> data) { }
    public String getAccessTransformerClass() { return null; }
}
