package mekanism.common.command;

import mekanism.common.Mekanism;
import mekanism.qioprocessing.common.planning.QIOClaimOrderRecovery;
import mekanism.qioprocessing.common.workbench.WorkbenchRuntime;
import net.minecraft.command.CommandException;
import net.minecraft.command.ICommandSender;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.text.TextComponentString;
import net.minecraftforge.server.command.CommandTreeBase;

import java.io.IOException;
import java.util.UUID;

public final class CommandQIOClaim extends CommandTreeBase {
    public CommandQIOClaim() {
        addSubcommand(new CommandMek.Cmd("status", "/mek qio_claim status <order UUID>", this::status) {
            @Override
            public int getRequiredPermissionLevel() {
                return 4;
            }
        });
        addSubcommand(new CommandMek.Cmd("retry", "/mek qio_claim retry <order UUID>", this::retry) {
            @Override
            public int getRequiredPermissionLevel() {
                return 4;
            }
        });
    }

    @Override
    public String getName() {
        return "qio_claim";
    }

    @Override
    public String getUsage(ICommandSender sender) {
        return "/mek qio_claim <status|retry> <order UUID>";
    }

    @Override
    public int getRequiredPermissionLevel() {
        return 4;
    }

    private void status(MinecraftServer server, ICommandSender sender, String[] args) throws CommandException {
        UUID orderId = orderId(args);
        try {
            sender.sendMessage(new TextComponentString(WorkbenchRuntime.claimRecoveryStatus(server, orderId)));
        } catch (IOException | IllegalStateException error) {
            throw new CommandException("QIO claim status failed: %s", error.getMessage());
        }
    }

    private void retry(MinecraftServer server, ICommandSender sender, String[] args) throws CommandException {
        UUID orderId = orderId(args);
        Mekanism.logger.warn("[QIO Workbench] Claim recovery retry requested by {} for order {}", sender.getName(), orderId);
        try {
            QIOClaimOrderRecovery.Status result = WorkbenchRuntime.retryClaimRecovery(server, orderId);
            Mekanism.logger.warn("[QIO Workbench] Claim recovery retry for order {}: {}", orderId, result);
            sender.sendMessage(new TextComponentString("QIO claim recovery " + orderId + ": " + result));
        } catch (IOException | IllegalStateException error) {
            Mekanism.logger.warn("[QIO Workbench] Claim recovery retry failed for order {}", orderId, error);
            throw new CommandException("QIO claim recovery retry failed: %s", error.getMessage());
        }
    }

    private static UUID orderId(String[] args) throws CommandException {
        if (args.length != 1) throw new CommandException("Expected exactly one claim order UUID");
        try {
            UUID orderId = UUID.fromString(args[0]);
            if (orderId.toString().equals(args[0])) return orderId;
        } catch (IllegalArgumentException error) {
            throw new CommandException("Invalid claim order UUID: %s", args[0]);
        }
        throw new CommandException("Invalid claim order UUID: %s", args[0]);
    }
}

