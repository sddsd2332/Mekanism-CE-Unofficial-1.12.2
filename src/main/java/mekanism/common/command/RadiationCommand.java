package mekanism.common.command;

import java.util.*;
import mekanism.api.Coord4D;
import mekanism.api.EnumColor;
import mekanism.api.MekanismAPI;
import mekanism.api.radiation.capability.IRadiationEntity;
import mekanism.common.MekanismLang;
import mekanism.common.capabilities.Capabilities;
import mekanism.common.command.CommandMek.Cmd;
import mekanism.common.lib.radiation.RadiationManager;
import mekanism.common.util.UnitDisplayUtils;
import net.minecraft.command.*;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.text.ITextComponent;
import net.minecraft.world.World;
import net.minecraftforge.server.command.CommandTreeBase;

public class RadiationCommand extends CommandTreeBase {
    public RadiationCommand() {
        register("add", this::add);
        register("addEntity", this::addEntity);
        register("get", this::get);
        register("heal", this::heal);
        register("reduce", this::reduce);
        register("removeAll", this::removeAll);
    }

    private void register(String name, CommandMek.CmdExecute action) {
        addSubcommand(new Cmd(name, "cmd.mek.radiation." + name, action) {
            @Override public int getRequiredPermissionLevel() { return 2; }
            @Override public List<String> getTabCompletions(MinecraftServer server, ICommandSender sender, String[] args, BlockPos pos) {
                return complete(name, server, args, pos);
            }
            // Resolve multi-target selectors ourselves, once, rather than letting CommandHandler
            // expand and re-execute the entire command separately for each selected entity.
            @Override public boolean isUsernameIndex(String[] args, int index) { return false; }
        });
    }

    @Override public String getName() { return "radiation"; }
    @Override public String getUsage(ICommandSender sender) { return "cmd.mek.radiation.usage"; }
    @Override public int getRequiredPermissionLevel() { return 2; }

    public void add(MinecraftServer server, ICommandSender sender, String[] args) throws CommandException {
        if (args.length != 1 && args.length != 4 && args.length != 5) throw usage("add");
        double magnitude = magnitude(args[0]);
        Coord4D location = location(server, sender, args, 1);
        World world = targetWorld(server, sender, args.length == 5, location.dimensionId);
        if (!MekanismAPI.getRadiationManager().radiate(world, location.getPos(), magnitude)) throw new CommandException("cmd.mek.radiation.unavailable");
        sender.sendMessage(MekanismLang.COMMAND_RADIATION_ADD.translateColored(EnumColor.GREY,
              RadiationManager.RadiationScale.getSeverityColor(magnitude),
              UnitDisplayUtils.getDisplayShort(magnitude, UnitDisplayUtils.RadiationUnit.SVH, 3),
              EnumColor.INDIGO, position(location.getPos()), EnumColor.INDIGO, Integer.toString(location.dimensionId)));
        result(sender, returnLevel(magnitude));
    }

    public void get(MinecraftServer server, ICommandSender sender, String[] args) throws CommandException {
        if (args.length != 0 && args.length != 3 && args.length != 4) throw usage("get");
        Coord4D location = location(server, sender, args, 0);
        World world = targetWorld(server, sender, args.length == 4, location.dimensionId);
        double magnitude = MekanismAPI.getRadiationManager().getRadiationLevel(world, location.getPos());
        if (!Double.isFinite(magnitude)) throw new CommandException("cmd.mek.radiation.unavailable");
        sender.sendMessage(MekanismLang.COMMAND_RADIATION_GET.translateColored(EnumColor.GREY,
              EnumColor.INDIGO, position(location.getPos()), EnumColor.INDIGO, Integer.toString(location.dimensionId),
              RadiationManager.RadiationScale.getSeverityColor(magnitude),
              UnitDisplayUtils.getDisplayShort(magnitude, UnitDisplayUtils.RadiationUnit.SVH, 3)));
        result(sender, returnLevel(magnitude));
    }

    // 1.20+ syntax: magnitude for self, targets magnitude for others.
    // Retain the previous magnitude targets form when its first token is numeric.
    public void addEntity(MinecraftServer server, ICommandSender sender, String[] args) throws CommandException {
        changeEntities(server, sender, args, false);
    }

    public void reduce(MinecraftServer server, ICommandSender sender, String[] args) throws CommandException {
        changeEntities(server, sender, args, true);
    }

    private void changeEntities(MinecraftServer server, ICommandSender sender, String[] args, boolean reduce) throws CommandException {
        if (args.length < 1 || args.length > 2) throw usage(reduce ? "reduce" : "addEntity");
        boolean self = args.length == 1;
        boolean oldOrder = !self && numeric(args[0]);
        double requested = magnitude(args[self || oldOrder ? 0 : 1]);
        String target = self ? null : args[oldOrder ? 1 : 0];
        List<Entity> targets = targets(server, sender, target);
        int affected = 0;
        double actual = 0;
        for (Entity entity : targets) {
            if (!(entity instanceof EntityLivingBase)) continue;
            IRadiationEntity radiation = entity.getCapability(Capabilities.RADIATION_ENTITY_CAPABILITY, null);
            if (radiation == null) continue;
            double amount = requested;
            if (reduce) {
                double before = radiation.getRadiation();
                double updated = Math.max(RadiationManager.BASELINE, before - requested);
                amount = Math.max(0, before - updated);
                radiation.set(updated);
            } else radiation.radiate(requested);
            actual = amount;
            affected++;
            String display = UnitDisplayUtils.getDisplayShort(amount, UnitDisplayUtils.RadiationUnit.SV, 3);
            EnumColor color = RadiationManager.RadiationScale.getSeverityColor(amount);
            if (reduce) {
                sender.sendMessage(self
                      ? MekanismLang.COMMAND_RADIATION_REDUCE.translateColored(EnumColor.GREY, color, display)
                      : MekanismLang.COMMAND_RADIATION_REDUCE_TARGET.translateColored(EnumColor.GREY,
                            EnumColor.INDIGO, entity.getDisplayName(), color, display));
            } else {
                sender.sendMessage(self
                      ? MekanismLang.COMMAND_RADIATION_ADD_ENTITY.translateColored(EnumColor.GREY, color, display)
                      : MekanismLang.COMMAND_RADIATION_ADD_ENTITY_TARGET.translateColored(EnumColor.GREY,
                            color, display, EnumColor.INDIGO, entity.getDisplayName()));
            }
        }
        sender.setCommandStat(CommandResultStats.Type.AFFECTED_ENTITIES, affected);
        result(sender, self ? returnLevel(actual) : affected);
    }

    public void heal(MinecraftServer server, ICommandSender sender, String[] args) throws CommandException {
        if (args.length > 1) throw usage("heal");
        int affected = 0;
        for (Entity entity : targets(server, sender, args.length == 0 ? null : args[0])) {
            if (!(entity instanceof EntityLivingBase)) continue;
            IRadiationEntity radiation = entity.getCapability(Capabilities.RADIATION_ENTITY_CAPABILITY, null);
            if (radiation == null) continue;
            radiation.set(RadiationManager.BASELINE);
            affected++;
            sender.sendMessage(args.length == 0
                  ? MekanismLang.COMMAND_RADIATION_CLEAR.translateColored(EnumColor.GREY)
                  : MekanismLang.COMMAND_RADIATION_CLEAR_ENTITY.translateColored(EnumColor.GREY, EnumColor.INDIGO, entity.getDisplayName()));
        }
        sender.setCommandStat(CommandResultStats.Type.AFFECTED_ENTITIES, affected);
        result(sender, affected);
    }

    protected List<Entity> targets(MinecraftServer server, ICommandSender sender, String target) throws CommandException {
        if (target == null) return Collections.singletonList(getCommandSenderAsPlayer(sender));
        List<Entity> entities = getEntityList(server, sender, target);
        if (entities.isEmpty()) throw new EntityNotFoundException("commands.generic.selector.notFound", target);
        return entities;
    }

    public void removeAll(MinecraftServer server, ICommandSender sender, String[] args) throws CommandException {
        if (args.length != 0) throw usage("removeAll");
        try { RadiationManager.INSTANCE.clearSources(server.worlds); }
        catch (IllegalStateException error) { throw new CommandException("cmd.mek.radiation.unavailable"); }
        sender.sendMessage(MekanismLang.COMMAND_RADIATION_REMOVE_ALL.translateColored(EnumColor.GREY));
        result(sender, 1);
    }

    private static World targetWorld(MinecraftServer server, ICommandSender sender, boolean explicit, int dimension) throws CommandException {
        if (!explicit) return sender.getEntityWorld();
        for (World world : server.worlds) if (world != null && world.provider.getDimension() == dimension) return world;
        throw new CommandException("cmd.mek.radiation.dimension.invalid", dimension);
    }

    static double magnitude(String value) throws NumberInvalidException {
        return parseDouble(value, Double.MIN_VALUE, 10_000);
    }

    private static boolean numeric(String value) {
        try { Double.parseDouble(value); return true; }
        catch (NumberFormatException ignored) { return false; }
    }

    static Coord4D location(MinecraftServer server, ICommandSender sender, String[] args, int offset) throws CommandException {
        World world = sender.getEntityWorld();
        Vec3d base = sender.getPositionVector();
        if (args.length == offset) return new Coord4D(base.x, base.y, base.z, world.provider.getDimension());
        int dim = args.length == offset + 4 ? parseInt(args[offset + 3]) : world.provider.getDimension();
        if (args.length == offset + 4) {
            boolean found = false;
            if (server != null && server.worlds != null) for (World candidate : server.worlds) {
                if (candidate != null && candidate.provider.getDimension() == dim) { found = true; break; }
            }
            if (!found) throw new CommandException("cmd.mek.radiation.dimension.invalid", dim);
        }
        if (args[offset].startsWith("^") || args[offset + 1].startsWith("^") || args[offset + 2].startsWith("^")) {
            double[] local = new double[3];
            for (int i = 0; i < 3; i++) {
                String token = args[offset + i];
                if (!token.startsWith("^")) throw new NumberInvalidException("commands.generic.num.invalid", token);
                local[i] = token.length() == 1 ? 0 : parseDouble(token.substring(1));
            }
            Entity entity = sender.getCommandSenderEntity();
            double yaw = Math.toRadians(entity == null ? 0 : entity.rotationYaw);
            double pitch = Math.toRadians(entity == null ? 0 : entity.rotationPitch);
            Vec3d forward = new Vec3d(-Math.sin(yaw) * Math.cos(pitch), -Math.sin(pitch), Math.cos(yaw) * Math.cos(pitch));
            Vec3d up = new Vec3d(-Math.sin(yaw) * Math.sin(pitch), Math.cos(pitch), Math.cos(yaw) * Math.sin(pitch));
            Vec3d left = up.crossProduct(forward);
            Vec3d resolved = base.add(left.scale(local[0])).add(up.scale(local[1])).add(forward.scale(local[2]));
            if (!Double.isFinite(resolved.x) || !Double.isFinite(resolved.y) || !Double.isFinite(resolved.z) ||
                  Math.abs(resolved.x) > 30_000_000 || Math.abs(resolved.y) > 30_000_000 || Math.abs(resolved.z) > 30_000_000) {
                throw new NumberInvalidException("commands.generic.num.invalid", resolved.toString());
            }
            return new Coord4D(resolved.x, resolved.y, resolved.z, dim);
        }
        // Vec3 semantics retain fractional sender position for relative coordinates; no y=0..256 clamp.
        return new Coord4D(parseDouble(base.x, args[offset], true),
              parseDouble(base.y, args[offset + 1], false), parseDouble(base.z, args[offset + 2], true), dim);
    }

    static int returnLevel(double magnitude) {
        if (!(magnitude >= RadiationManager.MIN_MAGNITUDE)) return 0;
        return (int) Math.min(Integer.MAX_VALUE, magnitude * 1_000_000);
    }

    private static void result(ICommandSender sender, int value) {
        sender.setCommandStat(CommandResultStats.Type.QUERY_RESULT, value);
    }

    private static WrongUsageException usage(String command) {
        return new WrongUsageException("cmd.mek.radiation." + command + ".usage");
    }

    private static List<String> complete(String command, MinecraftServer server, String[] args, BlockPos pos) {
        if ("add".equals(command) || "get".equals(command)) {
            int offset = "add".equals(command) ? 1 : 0;
            if (args.length > offset && args.length <= offset + 3) return getTabCompletionCoordinate(args, offset, pos);
            if (args.length == offset + 4) {
                List<String> dimensions = new ArrayList<>();
                if (server.worlds != null) for (World world : server.worlds) {
                    if (world != null) dimensions.add(Integer.toString(world.provider.getDimension()));
                }
                return getListOfStringsMatchingLastWord(args, dimensions);
            }
        } else if ("heal".equals(command) || "addEntity".equals(command) || "reduce".equals(command)) {
            if (args.length == 1 || (args.length == 2 && !"heal".equals(command) && numeric(args[0]))) {
                List<String> names = new ArrayList<>(Arrays.asList(server.getOnlinePlayerNames()));
                names.addAll(Arrays.asList("@a", "@e", "@p", "@r", "@s"));
                return getListOfStringsMatchingLastWord(args, names);
            }
        }
        return Collections.emptyList();
    }

    private static ITextComponent position(BlockPos pos) {
        return MekanismLang.GENERIC_BLOCK_POS.translate(pos.getX(), pos.getY(), pos.getZ());
    }
}
