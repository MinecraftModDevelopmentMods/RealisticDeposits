package zone.moddev.mc.realisticdeposits;

import zone.moddev.mc.realisticdeposits.command.RealisticDepositsCommand;
import zone.moddev.mc.realisticdeposits.config.RealisticDepositsRuntime;

import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.event.FMLPreInitializationEvent;
import net.minecraftforge.fml.common.event.FMLServerStartingEvent;

/**
 * Forge entry point for Realistic Deposits.
 *
 * <p>The alpha loads and validates deposit definitions and exposes the
 * deterministic geological-body planner. World writes remain disabled until
 * OreSpawn provides stable world, dimension and current-chunk identity plus
 * geology sampling to its placement context.</p>
 */
@Mod(modid = RealisticDeposits.MOD_ID, name = RealisticDeposits.NAME,
		version = RealisticDeposits.VERSION, acceptedMinecraftVersions = "[1.10.2]",
		dependencies = "required-after:orespawn@[4.0.16,5.0.0)",
		guiFactory = "zone.moddev.mc.realisticdeposits.client.RealisticDepositsGuiFactory")
public final class RealisticDeposits {
	public static final String MOD_ID = "realisticdeposits";
	public static final String NAME = "Realistic Deposits";
	public static final String VERSION = "0.1.0.110021";

	@Mod.EventHandler
	public void preInitialize(FMLPreInitializationEvent event) {
		RealisticDepositsRuntime.initialize(event.getModConfigurationDirectory());
	}

	@Mod.EventHandler
	public void serverStarting(FMLServerStartingEvent event) {
		event.registerServerCommand(new RealisticDepositsCommand());
	}
}
