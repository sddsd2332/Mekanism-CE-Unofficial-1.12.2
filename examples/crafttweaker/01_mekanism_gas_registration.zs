#loader mekanism

// Registry scripts must be identical on the client and server.
// Gas IDs depend on registration order, so use a unique gas name.
val gasBuilder = mods.mekanism.gas.create("crt_example_gas");
gasBuilder.tint(0x4FC3F7);
gasBuilder.translationKey("crt_example_gas");
gasBuilder.fluid("liquid_crt_example_gas");
val exampleGas = gasBuilder.register();

// The short overload is also available. Use a different name if enabled.
// mods.mekanism.gas.register("crt_example_gas_short", 0xFFAA33);

// Map an existing gas to a fluid that already exists.
// mods.mekanism.gas.setFluid("oxygen", <liquid:liquidoxygen>);

// The string overload is useful when the other mod registers its fluid later.
// The mapping is resolved after all fluids have been registered.
// mods.mekanism.gas.setFluid("oxygen", "other_mod_liquid_oxygen");

// Infuse types are also registry entries and must be declared in this loader.
mods.mekanism.infuse.registerType(
    "CRT_EXAMPLE_EMERALD",
    "mekanism:blocks/infuse/diamond",
    "crt_example_emerald"
);
