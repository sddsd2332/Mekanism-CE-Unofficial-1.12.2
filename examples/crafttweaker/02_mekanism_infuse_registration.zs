// This file runs in the normal CraftTweaker script loader.
// The CRT_EXAMPLE_EMERALD type is registered by 01_mekanism_gas_registration.zs.
mods.mekanism.infuse.registerItem(<minecraft:emerald>, "CRT_EXAMPLE_EMERALD", 10);

// Wildcard metadata is expanded to concrete subtypes by the integration.
// Uncomment only if every matching plank subtype should be registered.
// mods.mekanism.infuse.registerItem(<minecraft:planks:*>, "CRT_EXAMPLE_EMERALD", 10);
