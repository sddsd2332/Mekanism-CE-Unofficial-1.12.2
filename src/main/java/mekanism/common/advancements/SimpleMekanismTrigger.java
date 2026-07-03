package mekanism.common.advancements;

import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonObject;
import java.util.List;
import java.util.Map;
import java.util.Set;
import mekanism.common.Mekanism;
import net.minecraft.advancements.ICriterionTrigger;
import net.minecraft.advancements.PlayerAdvancements;
import net.minecraft.advancements.critereon.AbstractCriterionInstance;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.util.ResourceLocation;

public class SimpleMekanismTrigger implements ICriterionTrigger<SimpleMekanismTrigger.Instance> {

    private final ResourceLocation id;
    private final Map<PlayerAdvancements, Listeners> listeners = Maps.newHashMap();

    public SimpleMekanismTrigger(String name) {
        id = new ResourceLocation(Mekanism.MODID, name);
    }

    @Override
    public ResourceLocation getId() {
        return id;
    }

    @Override
    public void addListener(PlayerAdvancements playerAdvancements, Listener<Instance> listener) {
        Listeners listenerSet = listeners.get(playerAdvancements);
        if (listenerSet == null) {
            listenerSet = new Listeners(playerAdvancements);
            listeners.put(playerAdvancements, listenerSet);
        }
        listenerSet.add(listener);
    }

    @Override
    public void removeListener(PlayerAdvancements playerAdvancements, Listener<Instance> listener) {
        Listeners listenerSet = listeners.get(playerAdvancements);
        if (listenerSet != null) {
            listenerSet.remove(listener);
            if (listenerSet.isEmpty()) {
                listeners.remove(playerAdvancements);
            }
        }
    }

    @Override
    public void removeAllListeners(PlayerAdvancements playerAdvancements) {
        listeners.remove(playerAdvancements);
    }

    @Override
    public Instance deserializeInstance(JsonObject json, JsonDeserializationContext context) {
        return new Instance(id);
    }

    public void trigger(EntityPlayerMP player) {
        Listeners listenerSet = listeners.get(player.getAdvancements());
        if (listenerSet != null) {
            listenerSet.trigger();
        }
    }

    public static class Instance extends AbstractCriterionInstance {

        public Instance(ResourceLocation id) {
            super(id);
        }
    }

    private static class Listeners {

        private final PlayerAdvancements playerAdvancements;
        private final Set<Listener<Instance>> listeners = Sets.newHashSet();

        private Listeners(PlayerAdvancements playerAdvancements) {
            this.playerAdvancements = playerAdvancements;
        }

        private boolean isEmpty() {
            return listeners.isEmpty();
        }

        private void add(Listener<Instance> listener) {
            listeners.add(listener);
        }

        private void remove(Listener<Instance> listener) {
            listeners.remove(listener);
        }

        private void trigger() {
            List<Listener<Instance>> toGrant = Lists.newArrayList(listeners);
            for (Listener<Instance> listener : toGrant) {
                listener.grantCriterion(playerAdvancements);
            }
        }
    }
}
