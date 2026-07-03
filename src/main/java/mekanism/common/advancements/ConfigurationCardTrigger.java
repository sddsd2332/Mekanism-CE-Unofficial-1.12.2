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
import net.minecraft.util.JsonUtils;
import net.minecraft.util.ResourceLocation;

public class ConfigurationCardTrigger implements ICriterionTrigger<ConfigurationCardTrigger.Instance> {

    private static final ResourceLocation ID = new ResourceLocation(Mekanism.MODID, "configuration_card");
    private final Map<PlayerAdvancements, Listeners> listeners = Maps.newHashMap();

    @Override
    public ResourceLocation getId() {
        return ID;
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
        Boolean copy = json.has("copy") ? JsonUtils.getBoolean(json, "copy") : null;
        return new Instance(copy);
    }

    public void trigger(EntityPlayerMP player, boolean copy) {
        Listeners listenerSet = listeners.get(player.getAdvancements());
        if (listenerSet != null) {
            listenerSet.trigger(copy);
        }
    }

    public static class Instance extends AbstractCriterionInstance {

        private final Boolean copy;

        public Instance(Boolean copy) {
            super(ID);
            this.copy = copy;
        }

        public boolean test(boolean isCopy) {
            return copy == null || copy == isCopy;
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

        private void trigger(boolean copy) {
            List<Listener<Instance>> toGrant = null;
            for (Listener<Instance> listener : listeners) {
                if (listener.getCriterionInstance().test(copy)) {
                    if (toGrant == null) {
                        toGrant = Lists.newArrayList();
                    }
                    toGrant.add(listener);
                }
            }
            if (toGrant != null) {
                for (Listener<Instance> listener : toGrant) {
                    listener.grantCriterion(playerAdvancements);
                }
            }
        }
    }
}
