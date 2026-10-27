package zone.moddev.mc.realisticdeposits;

import zone.moddev.mc.realisticdeposits.command.RealisticDepositsCommand;
import zone.moddev.mc.realisticdeposits.config.RealisticDepositsRuntime;
import zone.moddev.mc.realisticdeposits.generation.StratiformSeamPattern;
import zone.moddev.mc.realisticdeposits.integration.OreSpawnIntegration;
import zone.moddev.mc.realisticdeposits.proxy.CommonProxy;

import zone.moddev.mc.orespawn.api.OrePatternType;

import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.SidedProxy;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.event.FMLPreInitializationEvent;
import net.minecraftforge.fml.common.event.FMLInitializationEvent;
import net.minecraftforge.fml.common.event.FMLServerStartingEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.event.RegistryEvent;

/**
 * Forge entry point for Realistic Deposits.
 *
 * <p>Definitions are registered as deterministic custom OreSpawn patterns.
 * Each chunk renders only its own slice of every intersecting region-scale
 * deposit, so generation order cannot change the result.</p>
 */
@Mod(modid = RealisticDeposits.MOD_ID, name = RealisticDeposits.NAME,
		version = RealisticDeposits.VERSION, acceptedMinecraftVersions = "[1.10.2]",
		dependencies = "required-after:orespawn@[4.1.3.110021,5.0.0)",
		guiFactory = "zone.moddev.mc.realisticdeposits.client.RealisticDepositsGuiFactory")
public final class RealisticDeposits {
	public static final String MOD_ID = "realisticdeposits";
	public static final String NAME = "Realistic Deposits";
	public static final String VERSION = "0.1.1.110021";
	private static final OrePatternType STRATIFORM_SEAM = StratiformSeamPattern.type()
			.setRegistryName(OreSpawnIntegration.PATTERN_ID);

	@SidedProxy(clientSide = "zone.moddev.mc.realisticdeposits.proxy.ClientProxy",
			serverSide = "zone.moddev.mc.realisticdeposits.proxy.CommonProxy")
	public static CommonProxy proxy;

	public RealisticDeposits() {
		MinecraftForge.EVENT_BUS.register(this);
	}

	@SubscribeEvent
	public void registerPatterns(RegistryEvent.Register<OrePatternType> event) {
		event.getRegistry().register(STRATIFORM_SEAM);
	}

	@Mod.EventHandler
	public void preInitialize(FMLPreInitializationEvent event) {
		RealisticDepositsRuntime.initialize(event.getModConfigurationDirectory());
	}

	@Mod.EventHandler
	public void initialize(FMLInitializationEvent event) {
		if (!OreSpawnIntegration.enqueue(RealisticDepositsRuntime.state().definitions())) {
			throw new IllegalStateException("OreSpawn rejected the Realistic Deposits provider");
		}
		proxy.initializeClient();
	}

	@Mod.EventHandler
	public void serverStarting(FMLServerStartingEvent event) {
		event.registerServerCommand(new RealisticDepositsCommand());
	}
}
