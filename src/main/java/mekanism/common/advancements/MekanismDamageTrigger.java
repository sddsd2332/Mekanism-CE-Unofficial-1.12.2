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
import net.minecraft.util.DamageSource;
import net.minecraft.util.JsonUtils;
import net.minecraft.util.ResourceLocation;

public class MekanismDamageTrigger implements ICriterionTrigger<MekanismDamageTrigger.Instance> {

    private static final ResourceLocation ID = new ResourceLocation(Mekanism.MODID, "damage");
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
        ResourceLocation damage = json.has("damage") ? new ResourceLocation(JsonUtils.getString(json, "damage")) : null;
        boolean killed = JsonUtils.getBoolean(json, "killed", false);
        return new Instance(damage, killed);
    }

    public void trigger(EntityPlayerMP player, DamageSource source, boolean killed) {
        Listeners listenerSet = listeners.get(player.getAdvancements());
        if (listenerSet != null) {
            listenerSet.trigger(source, killed);
        }
    }

    public static class Instance extends AbstractCriterionInstance {

        private final ResourceLocation damage;
        private final boolean killed;

        public Instance(ResourceLocation damage, boolean killed) {
            super(ID);
            this.damage = damage;
            this.killed = killed;
        }

        public boolean test(DamageSource source, boolean wasKilled) {
            if (killed != wasKilled) {
                return false;
            }
            if (damage == null) {
                return true;
            }
            return (damage.getNamespace() + "." + damage.getPath()).equals(source.getDamageType());
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

        private void trigger(DamageSource source, boolean killed) {
            List<Listener<Instance>> toGrant = null;
            for (Listener<Instance> listener : listeners) {
                if (listener.getCriterionInstance().test(source, killed)) {
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
