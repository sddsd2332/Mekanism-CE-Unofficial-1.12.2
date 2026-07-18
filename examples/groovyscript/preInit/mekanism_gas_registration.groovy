// This file must run from GroovyScript's preInit loader.
// Keep it identical on the client and server and use a unique gas name.
def exampleGas = mods.mekanism.gas.create('grs_example_gas')
        .tint(0x4FC3F7)
        .translationKey('grs_example_gas')
        .fluid('liquid_grs_example_gas')
        .register()

// Map an existing gas to a fluid that is already registered.
// mods.mekanism.gas.setFluid('oxygen', fluid('liquidoxygen'))

// The string overload can defer resolution until the other mod's fluid exists.
// mods.mekanism.gas.setFluid('oxygen', 'other_mod_liquid_oxygen')
