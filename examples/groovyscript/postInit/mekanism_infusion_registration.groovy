// GroovyScript's Mekanism infusion registry runs in postInit by default.
// Use a unique type name and an item that is not already an infusion object.
mods.mekanism.infusion.addType(
        'grs_example_infusion',
        resource('mekanism:blocks/infuse/diamond')
)

mods.mekanism.infusion.add(
        'grs_example_infusion',
        10,
        item('minecraft:ice')
)

// Existing infusion types can be used without addType.
// mods.mekanism.infusion.add('DIAMOND', 10, item('minecraft:packed_ice'))
