package mekanism.common.command;


import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import mekanism.api.MekanismAPI;
import mekanism.common.multiblock.MultiblockManager;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.command.CommandBase;
import net.minecraft.command.CommandException;
import net.minecraft.command.CommandGameRule;
import net.minecraft.command.ICommandSender;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.text.TextComponentString;
import net.minecraft.util.text.TextComponentTranslation;
import net.minecraft.util.text.TextFormatting;
import net.minecraft.world.GameRules;
import net.minecraftforge.fml.common.event.FMLServerStartingEvent;
import net.minecraftforge.server.command.CommandTreeBase;

import javax.annotation.Nonnull;
import java.text.DecimalFormat;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.UUID;

import static mekanism.common.concurrent.TaskExecutor.*;

public class CommandMek extends CommandTreeBase {
    // Let level-2 radiation commands reach their own checks; other children retain their levels.
    @Override
    public int getRequiredPermissionLevel() {
        return 2;
    }

    private static final DecimalFormat DECIMAL_FORMAT = new DecimalFormat("#,###.##");

    private final Map<UUID, Deque<BlockPos>> tpStack = new Object2ObjectOpenHashMap<>();

    public CommandMek() {
        addSubcommand(new Cmd("debug", "cmd.mek.debug", this::toggleDebug));
        addSubcommand(new Cmd("testrules", "cmd.mek.testrules", this::setupTestRules));
        addSubcommand(new Cmd("tp", "cmd.mek.tp", this::teleportPush));
        addSubcommand(new Cmd("tpop", "cmd.mek.tpop", this::teleportPop));
        addSubcommand(new Cmd("performance_report", "cmd.mekceu.performance_report", CommandMek::performanceReport));
        addSubcommand(new Cmd("multiblock_retry_history", "/mek multiblock_retry_history", (server, sender, args) ->
              sender.sendMessage(new TextComponentString("Retried multiblock history loads in this world: " +
                    MultiblockManager.retryLegacyHistory(sender.getEntityWorld())))));
        addSubcommand(new Cmd("multiblock_recover", "/mek multiblock_recover", (server, sender, args) -> {
            try {
                sender.sendMessage(new TextComponentString("Recovering multiblock stores in this world: " + MultiblockManager.recoverStores(sender.getEntityWorld())));
            } catch (java.io.IOException error) {
                throw new CommandException("Multiblock recovery could not start: %s", error.getMessage());
            }
        }));
        addSubcommand(new CommandChunk());
        addSubcommand(new Cmd("multiblock_status", "/mek multiblock_status", (server, sender, args) -> {
            for (String line : MultiblockManager.persistenceStatus(sender.getEntityWorld())) sender.sendMessage(new TextComponentString(line));
        }));
        addSubcommand(new RadiationCommand());
    }

    public static void register(FMLServerStartingEvent event) {
        CommandMek cmd = new CommandMek();
        event.registerServerCommand(cmd);
        event.registerServerCommand(new Cmd("mtp", "cmd.mek.tp", cmd::teleportPush));
        event.registerServerCommand(new Cmd("mtpop", "cmd.mek.tpop", cmd::teleportPop));
    }

    @Nonnull
    @Override
    public String getName() {
        return "mek";
    }

    @Nonnull
    @Override
    public String getUsage(@Nonnull ICommandSender sender) {
        return "cmd.mek.usage";
    }


    public void toggleDebug(MinecraftServer server, ICommandSender sender, String[] args) {
        MekanismAPI.debug = !MekanismAPI.debug;
        CommandBase.notifyCommandListener(sender, this, "cmd.mek.debug", MekanismAPI.debug);
    }

    public void setupTestRules(MinecraftServer server, ICommandSender sender, String[] args) {
        GameRules rules = server.getEntityWorld().getGameRules();
        rules.setOrCreateGameRule("doMobSpawning", "false");
        rules.setOrCreateGameRule("doDaylightCycle", "false");
        rules.setOrCreateGameRule("doWeatherCycle", "false");
        rules.setOrCreateGameRule("keepInventory", "true");
        server.getEntityWorld().setWorldTime(2000);
        CommandGameRule.notifyGameRuleChange(rules, "", server);
        CommandBase.notifyCommandListener(sender, this, "cmd.mek.testrules");
    }

    public void teleportPush(MinecraftServer server, ICommandSender sender, String[] args) throws CommandException {
        if (args.length < 3) {
            notifyCommandListener(sender, this, "cmd.mek.tp.missing.args");
            return;
        }

        // Parse the target arguments from command line
        CoordinateArg xArg = parseCoordinate(10, args[0], true);
        CoordinateArg yArg = parseCoordinate(10, args[1], true);
        CoordinateArg zArg = parseCoordinate(10, args[2], true);

        // Save the current location on the stack
        UUID player = sender.getCommandSenderEntity().getUniqueID();
        Deque<BlockPos> playerLocations = tpStack.getOrDefault(player, new ArrayDeque<>());
        playerLocations.push(sender.getPosition());
        tpStack.put(player, playerLocations);

        // Teleport user to new location
        teleport(sender.getCommandSenderEntity(), xArg.getResult(), yArg.getResult(), zArg.getResult());
        notifyCommandListener(sender, this, "cmd.mek.tp", args[0], args[1], args[2]);
    }

    public void teleportPop(MinecraftServer server, ICommandSender sender, String[] args) {
        UUID player = sender.getCommandSenderEntity().getUniqueID();

        // Get stack of locations for the user; if there's at least one entry, pop it off
        // and send the user back there
        Deque<BlockPos> playerLocations = tpStack.getOrDefault(player, new ArrayDeque<>());
        if (!playerLocations.isEmpty()) {
            BlockPos lastPos = playerLocations.pop();
            tpStack.put(player, playerLocations);
            teleport(sender.getCommandSenderEntity(), lastPos.getX(), lastPos.getY(), lastPos.getZ());
            notifyCommandListener(sender, this, "cmd.mek.tpop", lastPos.getX(), lastPos.getY(), lastPos.getZ(), playerLocations.size());
        } else {
            notifyCommandListener(sender, this, "cmd.mek.tpop.empty.stack");
        }
    }

    private static void teleport(Entity player, double x, double y, double z) {
        if (player instanceof final EntityPlayerMP mp) {
            mp.connection.setPlayerLocation(x, y, z, mp.rotationYaw, mp.rotationPitch);
        } else {
            EntityPlayerSP sp = (EntityPlayerSP) player;
            sp.setLocationAndAngles(x, y, z, sp.rotationYaw, sp.rotationPitch);
        }
    }

    public static void performanceReport(@Nonnull MinecraftServer server, @Nonnull ICommandSender sender, String[] args) {
        String langKey = "cmd.mekceu.performance_report";

        if (args.length > 0 && args[0].equals("reset")) {
            executedCount = 0;
            totalExecuted = 0;
            totalUsedTime = 0;
            taskUsedTime = 0;
            sender.sendMessage(new TextComponentTranslation(langKey + ".reset"));
            return;
        }

        long executedAvgPerExecution = executedCount == 0 ? 0 : totalExecuted / executedCount;
        double usedTimeAvgPerExecution = executedCount == 0 ? 0 : (double) (totalUsedTime / executedCount) / 1000;
        double taskUsedTimeAvg = totalExecuted == 0 ? 0 : (double) (taskUsedTime / executedCount) / 1000;
        long usedTimeAvg = totalExecuted == 0 ? 0 : taskUsedTime / totalExecuted;

        sender.sendMessage(new TextComponentTranslation(langKey + ".title",
                TextFormatting.GREEN + formatDecimal(executedCount) + TextFormatting.RESET));
        sender.sendMessage(new TextComponentString(""));

        sender.sendMessage(new TextComponentTranslation(langKey + ".total_executed",
                TextFormatting.BLUE + formatDecimal(totalExecuted) + TextFormatting.RESET));
        sender.sendMessage(new TextComponentTranslation(langKey + ".tasks_avg_per_execution",
                TextFormatting.BLUE + String.valueOf(executedAvgPerExecution) + TextFormatting.RESET));
        sender.sendMessage(new TextComponentString(""));

        sender.sendMessage(new TextComponentTranslation(langKey + ".total_used_time",
                TextFormatting.BLUE + String.valueOf(totalUsedTime / 1000) + TextFormatting.RESET));
        sender.sendMessage(new TextComponentTranslation(langKey + ".used_time_avg_per_execution",
                TextFormatting.YELLOW + String.format("%.2f", usedTimeAvgPerExecution) + TextFormatting.RESET));
        sender.sendMessage(new TextComponentString(""));

        sender.sendMessage(new TextComponentTranslation(langKey + ".task_used_time",
                TextFormatting.BLUE + formatDecimal(((double) taskUsedTime / 1000L)) + TextFormatting.RESET));
        sender.sendMessage(new TextComponentString(""));

        sender.sendMessage(new TextComponentTranslation(langKey + ".task_used_time_avg",
                TextFormatting.YELLOW + String.format("%.2f", taskUsedTimeAvg) + TextFormatting.RESET));
        sender.sendMessage(new TextComponentTranslation(langKey + ".used_time_avg",
                TextFormatting.BLUE + String.valueOf(usedTimeAvg) + TextFormatting.RESET));
    }

    public static String formatDecimal(double value) {
        return DECIMAL_FORMAT.format(value);
    }

    // Wrapper class that makes it easier to create single method commands
    public static class Cmd extends CommandBase {

        private final String name;
        private final String usage;
        private final CmdExecute ex;

        Cmd(String name, String usage, CmdExecute ex) {
            this.name = name;
            this.usage = usage;
            this.ex = ex;
        }

        @Nonnull
        @Override
        public String getName() {
            return name;
        }

        @Nonnull
        @Override
        public String getUsage(@Nonnull ICommandSender sender) {
            return usage + ".usage";
        }

        @Override
        public void execute(@Nonnull MinecraftServer server, @Nonnull ICommandSender sender, @Nonnull String[] args) throws CommandException {
            ex.execute(server, sender, args);
        }
    }

    interface CmdExecute {
        void execute(MinecraftServer server, ICommandSender sender, String[] args) throws CommandException;
    }


}
