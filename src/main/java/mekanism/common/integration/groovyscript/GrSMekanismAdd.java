package mekanism.common.integration.groovyscript;

import com.cleanroommc.groovyscript.api.GroovyPlugin;
import com.cleanroommc.groovyscript.compat.mods.GroovyContainer;
import com.cleanroommc.groovyscript.compat.mods.GroovyPropertyContainer;
import mekanism.common.Mekanism;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Collection;


public class GrSMekanismAdd implements GroovyPlugin {

    public static MekanismAdd get() {
        return new MekanismAdd();
    }

    @Override
    public GroovyPropertyContainer createGroovyPropertyContainer() {
        return get();
    }

    @Override
    public @NotNull String getModId() {
        return Mekanism.MODID;
    }

    @Override
    public @NotNull String getContainerName() {
        return Mekanism.MOD_NAME;
    }

    @Override
    public void onCompatLoaded(GroovyContainer<?> groovyContainer) {
        // The event listener is registered from Mekanism preInit while its mod container is active.
    }

    @Override
    public @NotNull Priority getOverridePriority() {
        return Priority.OVERRIDE;
    }

    @Override
    public Collection<String> getAliases() {
        Collection<String> info = new ArrayList<>();
        info.add(Mekanism.MOD_NAME);
        info.add("mek");
        info.add("mekceu");
        info.add("MekanismCEUnofficial");
        return info;
    }
}
