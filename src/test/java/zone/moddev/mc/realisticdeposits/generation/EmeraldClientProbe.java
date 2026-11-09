package zone.moddev.mc.realisticdeposits.generation;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiMainMenu;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.world.GameType;
import net.minecraft.world.WorldServer;
import net.minecraft.world.WorldSettings;
import net.minecraft.world.WorldType;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.Loader;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.event.FMLInitializationEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;

import zone.moddev.mc.realisticdeposits.geometry.AbandonedMinePlan;
import zone.moddev.mc.realisticdeposits.geometry.EmeraldDistrict;
import zone.moddev.mc.realisticdeposits.geometry.EmeraldWalkProbe;
import zone.moddev.mc.realisticdeposits.geometry.MinePoint;
import zone.moddev.mc.realisticdeposits.config.RealisticDepositsRuntime;
import zone.moddev.mc.realisticdeposits.model.DepositDefinition;

/** The ordinary client and natural player-driven chunk loading in a disposable save. */
@Mod(modid = "rdemeraldclientprobe", name = "Emerald Client Checks", version = "1", clientSideOnly = true,
		dependencies = "required-after:realisticdeposits")
public final class EmeraldClientProbe {
	private static final String WORLD = "emerald-client-test-world";
	private int phase;
	private int ticks;
	private volatile boolean finished;
	private volatile List<int[]> targets;
	private volatile int index;

	@Mod.EventHandler
	public void initialize(FMLInitializationEvent event) {
		if (System.getProperty("rd.emerald.client.target") != null) MinecraftForge.EVENT_BUS.register(this);
	}

	@SubscribeEvent
	public void tick(TickEvent.ClientTickEvent event) {
		if (event.phase != TickEvent.Phase.END) return;
		Minecraft mc = Minecraft.getMinecraft();
		if (finished || ++ticks > 2400) {
			if (!finished) System.err.println("RD_EMERALD_CLIENT_TIMEOUT phase=" + phase);
			mc.shutdown();
			return;
		}
		if (phase == 0 && mc.currentScreen instanceof GuiMainMenu) {
			File game = Loader.instance().getConfigDir().getParentFile();
			if (!game.getName().startsWith("client-fixture-") || new File(game, "saves/" + WORLD).exists()) {
				throw new IllegalStateException("Use an empty, disposable emerald client fixture");
			}
			mc.gameSettings.pauseOnLostFocus = false;
			mc.gameSettings.renderDistanceChunks = 12;
			mc.launchIntegratedServer(WORLD, "Emerald Client Checks",
					new WorldSettings(3346071746553592696L, GameType.CREATIVE, true, false, WorldType.DEFAULT).enableCommands());
			phase = 1;
			ticks = 0;
		} else if (phase == 1 && mc.player != null) {
			final UUID playerId = mc.player.getUniqueID();
			mc.getIntegratedServer().addScheduledTask(() -> {
				try {
					WorldServer world = mc.getIntegratedServer().worldServerForDimension(0);
					targets = findTargets(world);
					movePlayer(mc, playerId, targets.get(0));
				} catch (Throwable failure) {
					fail(failure);
				}
			});
			phase = 2;
			ticks = 0;
		} else if (phase == 2 && targets != null && ticks >= 200) {
			final UUID playerId = mc.player.getUniqueID();
			phase = 3;
			mc.getIntegratedServer().addScheduledTask(() -> {
				try {
					WorldServer world = mc.getIntegratedServer().worldServerForDimension(0);
					int[] target = targets.get(index);
					String key = "realisticdeposits:overworld_emerald_pockets@minecraft:overworld/" + target[3] + "," + target[4];
					MineWorldData data = MineWorldData.get(world);
					AbandonedMinePlan plan = data.plan(key);
					if (plan != null) {
						DepositDefinition definition = definition();
						EmeraldDistrict body = EmeraldPattern.plan(definition, world, target[3], target[4]).get();
						int pockets = 1;
						if (body.linkedWorkings(definition)) {
							pockets = EmeraldWalkProbe.verifyLinked(world, plan, body);
						} else {
							EmeraldWalkProbe.verify(world, plan);
						}
						System.out.println("RD_EMERALD_CLIENT_PASS surface=" + plan.entrance() + " landing="
								+ plan.landing() + " walked_pockets=" + pockets + " region=" + target[3] + "," + target[4]
								+ (body.large() ? " large_zones=" + body.zones() + " planned_ore=" + body.ore().size() : ""));
						finished = true;
					} else {
						String reason = data.rejectionReason(key);
						if (reason == null && ticks < 480) {
							mc.addScheduledTask(() -> phase = 2);
							return;
						}
						DepositDefinition definition = definition();
						EmeraldDistrict district = EmeraldPattern.plan(definition, world, target[3], target[4]).get();
						AbandonedMinePlan proposed = EmeraldRuntimeProbe.minePlan(world, definition, district).get();
						if (!EmeraldRuntimeProbe.noPartialEntrance(world, proposed)) {
							throw new AssertionError("Partially carved mine: " + key);
						}
						System.out.println("RD_EMERALD_CLIENT_NO_PORTAL " + key + " reason="
								+ (reason == null ? "not resolved during client smoke" : reason));
						if (++index == targets.size()) throw new AssertionError("No walkable route among surveyed sites");
						movePlayer(mc, playerId, targets.get(index));
						mc.addScheduledTask(() -> {
							ticks = 0;
							phase = 2;
						});
					}
				} catch (Throwable failure) {
					fail(failure);
				}
			});
		}
	}

	private void fail(Throwable failure) {
		System.err.println("RD_EMERALD_CLIENT_FAILED");
		failure.printStackTrace();
		finished = true;
	}

	private static void movePlayer(Minecraft mc, UUID playerId, int[] target) {
		EntityPlayerMP player = mc.getIntegratedServer().getPlayerList().getPlayerByUUID(playerId);
		player.connection.setPlayerLocation(target[0] + 0.5D, target[1], target[2] + 0.5D, 0, 0);
	}

	private static DepositDefinition definition() {
		return RealisticDepositsRuntime.state().definitions().stream().filter(DepositDefinition::isEmerald).findFirst().get();
	}

	private static List<int[]> findTargets(WorldServer world) {
		String mode = System.getProperty("rd.emerald.client.target");
		List<int[]> targets = new ArrayList<>();
		boolean rich = "rich-survey".equals(mode);
		boolean large = "large-survey".equals(mode);
		if (!rich && !large && !"survey".equals(mode)) {
			targets.add(java.util.Arrays.stream(mode.split(",")).mapToInt(Integer::parseInt).toArray());
			return targets;
		}
		if (!rich && !large) targets.add(new int[] {24168, 80, -9862, 23, -10});
		DepositDefinition definition = definition();
		int loaded = world.getChunkProvider().getLoadedChunkCount();
		int span = rich || large ? 128 : 40;
		for (int rx = 16; rx < 16 + span && targets.size() < 8; rx++) {
			for (int rz = -16; rz < -16 + span && targets.size() < 8; rz++) {
				if (!rich && !large && rx == 23 && rz == -10) continue;
				Optional<EmeraldDistrict> body = EmeraldPattern.plan(definition, world, rx, rz);
				if (!body.isPresent() || large != body.get().large() || rich && !body.get().rich()) continue;
				Optional<AbandonedMinePlan> plan = EmeraldRuntimeProbe.minePlan(world, definition, body.get());
				if (!plan.isPresent()) continue;
				MinePoint portal = plan.get().entrance();
				targets.add(new int[] {portal.x(), portal.y() + 1, portal.z(), rx, rz});
			}
		}
		if (world.getChunkProvider().getLoadedChunkCount() != loaded) throw new AssertionError("Preview loaded chunks");
		if (targets.isEmpty()) throw new AssertionError("No geometrically safe sites in the client survey");
		return targets;
	}
}
