package mekanism.common.command;

import mekanism.api.Coord4D;
import mekanism.api.EnumColor;
import mekanism.api.MekanismAPI;
import mekanism.api.radiation.capability.IRadiationEntity;
import mekanism.common.MekanismLang;
import mekanism.common.capabilities.Capabilities;
import mekanism.common.command.CommandMek.Cmd;
import mekanism.common.lib.radiation.RadiationManager;
import mekanism.common.util.UnitDisplayUtils;
import net.minecraft.command.CommandException;
import net.minecraft.command.ICommandSender;
import net.minecraft.command.NumberInvalidException;
import net.minecraft.command.PlayerNotFoundException;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.text.ITextComponent;
import net.minecraft.world.World;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.server.command.CommandTreeBase;

public class RadiationCommand extends CommandTreeBase {

    public RadiationCommand() {
        addSubcommand(new Cmd("add", "cmd.mek.radiation.add", this::add));
        addSubcommand(new Cmd("addEntity", "cmd.mek.radiation.addEntity", this::addEntity));
        addSubcommand(new Cmd("get", "cmd.mek.radiation.get", this::get));
        addSubcommand(new Cmd("heal", "cmd.mek.radiation.heal", this::heal));
        addSubcommand(new Cmd("reduce", "cmd.mek.radiation.reduce", this::reduce));
        addSubcommand(new Cmd("removeAll", "cmd.mek.radiation.removeAll", this::removeAll));
        MinecraftForge.EVENT_BUS.register(this);
    }


    @Override
    public String getName() {
        return "radiation";
    }

    @Override
    public String getUsage(ICommandSender sender) {
        return "cmd.mek.radiation.usage";
    }

    /**
     * Return the required permission level for this command.
     */
    @Override
    public int getRequiredPermissionLevel() {
        return 2;
    }

    public void add(MinecraftServer server, ICommandSender sender, String[] args) throws NumberInvalidException {
        // args:  <magnitude> [<dim> <x> <y> <z>]
        if (args.length == 1) {
            double magnitude = parseDouble(args[0], 0, 10000);
            addRadiation(sender, sender.getPositionVector(), sender.getEntityWorld(), magnitude);
        } else if (args.length == 5) {
            double magnitude = parseDouble(args[0], 0, 10000);
            int dim = parseInt(args[1]);
            BlockPos pos = parseBlockPos(sender, args, 2, false);
            addRadiation(sender, new Coord4D(pos, dim), magnitude);
        }
    }


    public void get(MinecraftServer server, ICommandSender sender, String[] args) throws NumberInvalidException {
        if (args.length == 0) {
            getRadiationLevel(sender, sender.getEntityWorld().provider.getDimension());
        } else if (args.length == 4) {
            // args:  <dim> <x> <y> <z>
            int dim = parseInt(args[0]);
            BlockPos pos = parseBlockPos(sender, args, 1, false);
            getRadiationLevel(sender, new Coord4D(pos, dim));
        }
    }

    public void heal(MinecraftServer server, ICommandSender sender, String[] args) throws CommandException {
        if (args.length == 0) {
            try {
                EntityPlayer player = getCommandSenderAsPlayer(sender);
                if (player != null && player.hasCapability(Capabilities.RADIATION_ENTITY_CAPABILITY, null)) {
                    IRadiationEntity entity = player.getCapability(Capabilities.RADIATION_ENTITY_CAPABILITY, null);
                    if (entity != null) {
                        entity.set(0);
                        sender.sendMessage(MekanismLang.COMMAND_RADIATION_CLEAR.translateColored(EnumColor.GREY));
                    }
                }
            } catch (PlayerNotFoundException ignored) {
            }
        } else {
            Entity entity = getEntity(server, sender, args[0]);
            if (entity instanceof EntityLivingBase base) {
                IRadiationEntity rad = base.getCapability(Capabilities.RADIATION_ENTITY_CAPABILITY, null);
                if (rad != null) {
                    rad.set(0);
                    sender.sendMessage(MekanismLang.COMMAND_RADIATION_CLEAR_ENTITY.translateColored(
                          EnumColor.GREY, EnumColor.INDIGO, entity.getDisplayName()));
                }
            }
        }

    }

    public void addEntity(MinecraftServer server, ICommandSender sender, String[] args) throws CommandException {
        if (args.length < 1 || args.length > 2) {
            throw new net.minecraft.command.WrongUsageException("cmd.mek.radiation.addEntity.usage");
        }
        double magnitude = parseDouble(args[0], 0, 10_000);
        EntityLivingBase target = getLivingTarget(server, sender, args.length == 2 ? args[1] : null);
        IRadiationEntity radiation = target.getCapability(Capabilities.RADIATION_ENTITY_CAPABILITY, null);
        if (radiation != null) {
            radiation.radiate(magnitude);
            MekanismLang message = args.length == 2 ? MekanismLang.COMMAND_RADIATION_ADD_ENTITY_TARGET : MekanismLang.COMMAND_RADIATION_ADD_ENTITY;
            String display = UnitDisplayUtils.getDisplayShort(magnitude, UnitDisplayUtils.RadiationUnit.SV, 3);
            if (args.length == 2) {
                sender.sendMessage(message.translateColored(EnumColor.GREY,
                      RadiationManager.RadiationScale.getSeverityColor(magnitude), display,
                      EnumColor.INDIGO, target.getDisplayName()));
            } else {
                sender.sendMessage(message.translateColored(EnumColor.GREY,
                      RadiationManager.RadiationScale.getSeverityColor(magnitude), display));
            }
        }
    }

    public void reduce(MinecraftServer server, ICommandSender sender, String[] args) throws CommandException {
        if (args.length < 1 || args.length > 2) {
            throw new net.minecraft.command.WrongUsageException("cmd.mek.radiation.reduce.usage");
        }
        double requested = parseDouble(args[0], 0, 10_000);
        EntityLivingBase target = getLivingTarget(server, sender, args.length == 2 ? args[1] : null);
        IRadiationEntity radiation = target.getCapability(Capabilities.RADIATION_ENTITY_CAPABILITY, null);
        if (radiation != null) {
            double previous = radiation.getRadiation();
            double updated = Math.max(RadiationManager.BASELINE, previous - requested);
            double reduced = previous - updated;
            radiation.set(updated);
            MekanismLang message = args.length == 2 ? MekanismLang.COMMAND_RADIATION_REDUCE_TARGET : MekanismLang.COMMAND_RADIATION_REDUCE;
            String display = UnitDisplayUtils.getDisplayShort(reduced, UnitDisplayUtils.RadiationUnit.SV, 3);
            if (args.length == 2) {
                sender.sendMessage(message.translateColored(EnumColor.GREY,
                      EnumColor.INDIGO, target.getDisplayName(),
                      RadiationManager.RadiationScale.getSeverityColor(reduced), display));
            } else {
                sender.sendMessage(message.translateColored(EnumColor.GREY,
                      RadiationManager.RadiationScale.getSeverityColor(reduced), display));
            }
        }
    }

    private static EntityLivingBase getLivingTarget(MinecraftServer server, ICommandSender sender, String name) throws CommandException {
        Entity target = name == null ? getCommandSenderAsPlayer(sender) : getEntity(server, sender, name);
        if (!(target instanceof EntityLivingBase living)) {
            throw new CommandException("commands.generic.entity.invalidType", target == null ? "" : target.getName());
        }
        return living;
    }

    public void removeAll(MinecraftServer server, ICommandSender sender, String[] args) {
        RadiationManager.INSTANCE.clearSources();
        sender.sendMessage(MekanismLang.COMMAND_RADIATION_REMOVE_ALL.translateColored(EnumColor.GREY));
    }


    //TODO
    private static int addRadiation(ICommandSender source, World world, double magnitude) {
        return addRadiation(source, source.getPositionVector(), world, magnitude);
    }

    private static int addRadiation(ICommandSender source, Coord4D location, double magnitude) {
        MekanismAPI.getRadiationManager().radiate(location, magnitude);
        source.sendMessage(MekanismLang.COMMAND_RADIATION_ADD.translateColored(EnumColor.GREY,
              RadiationManager.RadiationScale.getSeverityColor(magnitude),
              UnitDisplayUtils.getDisplayShort(magnitude, UnitDisplayUtils.RadiationUnit.SVH, 3),
              EnumColor.INDIGO, getPosition(location.getPos()),
              EnumColor.INDIGO, Integer.toString(location.dimensionId)));
        return 0;
    }

    private static int addRadiation(ICommandSender source, Vec3d pos, World world, double magnitude) {
        return addRadiation(source, new Coord4D(pos.x, pos.y, pos.z, world.provider.getDimension()), magnitude);
    }

    private static int getRadiationLevel(ICommandSender source, int world) {
        return getRadiationLevel(source, source.getPositionVector(), world);
    }

    private static int getRadiationLevel(ICommandSender source, Vec3d pos, int world) {
        return getRadiationLevel(source, new Coord4D(pos.x, pos.y, pos.z, world));
    }


    private static int getRadiationLevel(ICommandSender source, Coord4D location) {
        double magnitude = MekanismAPI.getRadiationManager().getRadiationLevel(location);
        source.sendMessage(MekanismLang.COMMAND_RADIATION_GET.translateColored(EnumColor.GREY,
              EnumColor.INDIGO, getPosition(location.getPos()),
              EnumColor.INDIGO, Integer.toString(location.dimensionId),
              RadiationManager.RadiationScale.getSeverityColor(magnitude),
              UnitDisplayUtils.getDisplayShort(magnitude, UnitDisplayUtils.RadiationUnit.SVH, 3)));
        return 0;
    }


    private static ITextComponent getPosition(BlockPos pos) {
        return MekanismLang.GENERIC_BLOCK_POS.translate(pos.getX(), pos.getY(), pos.getZ());
    }
}
