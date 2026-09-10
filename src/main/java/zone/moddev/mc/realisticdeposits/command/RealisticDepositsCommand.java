package zone.moddev.mc.realisticdeposits.command;

import java.util.Optional;

import zone.moddev.mc.realisticdeposits.RealisticDeposits;
import zone.moddev.mc.realisticdeposits.config.RealisticDepositsRuntime;
import zone.moddev.mc.realisticdeposits.config.RuntimeState;
import zone.moddev.mc.realisticdeposits.geometry.BlockBounds;
import zone.moddev.mc.realisticdeposits.geometry.RegionDepositPlanner;
import zone.moddev.mc.realisticdeposits.geometry.StratiformSeamBody;
import zone.moddev.mc.realisticdeposits.model.DepositDefinition;

import net.minecraft.command.CommandBase;
import net.minecraft.command.CommandException;
import net.minecraft.command.ICommandSender;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.text.TextComponentString;
import net.minecraft.world.World;

/** Read-only alpha status, reload and deterministic planning commands. */
public final class RealisticDepositsCommand extends CommandBase {
	@Override public String getName() { return "realisticdeposits"; }

	@Override
	public String getUsage(ICommandSender sender) {
		return "/realisticdeposits <status|reload|preview <regionX> <regionZ> [deposit-id]>";
	}

	@Override public int getRequiredPermissionLevel() { return 0; }

	@Override
	public void execute(MinecraftServer server, ICommandSender sender, String[] args)
			throws CommandException {
		String action = args.length == 0 ? "status" : args[0];
		if ("status".equals(action)) {
			status(sender);
		} else if ("reload".equals(action)) {
			if (!sender.canUseCommand(2, getName())) throw new CommandException("commands.generic.permission");
			try {
				RealisticDepositsRuntime.reload();
				status(sender);
			} catch (RuntimeException e) {
				throw new CommandException("Realistic Deposits reload failed: " + e.getMessage());
			}
		} else if ("preview".equals(action)) {
			preview(sender, args);
		} else {
			throw new CommandException(getUsage(sender));
		}
	}

	private static void status(ICommandSender sender) {
		RuntimeState state = RealisticDepositsRuntime.state();
		sender.sendMessage(new TextComponentString("Realistic Deposits " + RealisticDeposits.VERSION
				+ ": catalogs=" + state.catalogs().size() + ", definitions="
				+ state.definitions().size() + ", generation="
				+ state.integrationStatus().name().toLowerCase()));
	}

	private static void preview(ICommandSender sender, String[] args) throws CommandException {
		if (args.length < 3 || args.length > 4) throw new CommandException(
				"/realisticdeposits preview <regionX> <regionZ> [deposit-id]");
		int regionX = parseInt(args[1]);
		int regionZ = parseInt(args[2]);
		RuntimeState state = RealisticDepositsRuntime.state();
		DepositDefinition definition = selectDefinition(state, args.length == 4 ? args[3] : null);
		World world = sender.getEntityWorld();
		String dimension = dimensionId(world.provider.getDimension());
		Optional<StratiformSeamBody> planned = RegionDepositPlanner.plan(definition,
				world.getSeed(), dimension, regionX, regionZ);
		if (!planned.isPresent()) {
			sender.sendMessage(new TextComponentString("No " + definition.id()
					+ " candidate is selected in region " + regionX + "," + regionZ + "."));
			return;
		}
		StratiformSeamBody body = planned.get();
		BlockBounds bounds = body.bounds();
		sender.sendMessage(new TextComponentString("Planned " + definition.id() + " at "
				+ body.centerX() + "," + body.centerY() + "," + body.centerZ()
				+ "; strike=" + body.strikeLength() + ", down-dip=" + body.downDipWidth()
				+ "; bounds=" + bounds.minimumX() + "," + bounds.minimumY() + ","
				+ bounds.minimumZ() + " to " + bounds.maximumX() + "," + bounds.maximumY()
				+ "," + bounds.maximumZ() + ". Preview only; no blocks were changed."));
	}

	private static DepositDefinition selectDefinition(RuntimeState state, String id)
			throws CommandException {
		for (DepositDefinition definition : state.definitions()) {
			if (id == null || definition.id().equals(id)) return definition;
		}
		throw new CommandException(id == null ? "No deposit definitions are loaded"
				: "Unknown deposit definition: " + id);
	}

	private static String dimensionId(int dimension) {
		if (dimension == 0) return "minecraft:overworld";
		if (dimension == -1) return "minecraft:the_nether";
		if (dimension == 1) return "minecraft:the_end";
		return "minecraft:dimension/" + dimension;
	}
}
