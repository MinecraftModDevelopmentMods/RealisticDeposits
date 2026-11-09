package zone.moddev.mc.realisticdeposits.generation;

import java.io.FileReader;
import java.io.FileWriter;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Random;
import java.util.Set;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.item.EntityItem;
import net.minecraft.init.Blocks;
import net.minecraft.init.Enchantments;
import net.minecraft.init.Items;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.world.WorldServer;
import net.minecraft.world.biome.BiomeHills;
import net.minecraft.world.gen.ChunkProviderServer;
import net.minecraft.world.gen.structure.MapGenStructureData;
import net.minecraftforge.fml.common.FMLCommonHandler;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.common.util.FakePlayerFactory;
import net.minecraftforge.event.terraingen.OreGenEvent;
import net.minecraftforge.fml.common.event.FMLInitializationEvent;
import net.minecraftforge.fml.common.eventhandler.Event;
import net.minecraftforge.fml.common.eventhandler.EventPriority;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;

import zone.moddev.mc.orespawn.api.GeologySampler;
import zone.moddev.mc.orespawn.api.OreSpawnApi;
import zone.moddev.mc.realisticdeposits.config.DepositCatalogLoader;
import zone.moddev.mc.realisticdeposits.config.RealisticDepositsRuntime;
import zone.moddev.mc.realisticdeposits.geometry.AbandonedMinePlan;
import zone.moddev.mc.realisticdeposits.geometry.EmeraldDistrict;
import zone.moddev.mc.realisticdeposits.geometry.EmeraldMinePlanner;
import zone.moddev.mc.realisticdeposits.geometry.EmeraldWalkProbe;
import zone.moddev.mc.realisticdeposits.geometry.RichEmeraldMinePlanner;
import zone.moddev.mc.realisticdeposits.geometry.MineBlock;
import zone.moddev.mc.realisticdeposits.geometry.MinePoint;
import zone.moddev.mc.realisticdeposits.geometry.RegionDepositPlanner;
import zone.moddev.mc.realisticdeposits.geometry.StratiformSeamBody;
import zone.moddev.mc.realisticdeposits.model.DepositDefinition;

/** Packaged only in the opt-in local qualification mod. */
@Mod(modid = "rdemeraldprobe", name = "Emerald Qualification", version = "1",
		dependencies = "required-after:realisticdeposits")
public final class EmeraldRuntimeProbe {
	private static final String OVERWORLD = "minecraft:overworld";
	private static final int[][] SIDES = {{1,0,0}, {-1,0,0}, {0,1,0}, {0,-1,0}, {0,0,1}, {0,0,-1}};
	private boolean observingBackground;
	private int backgroundEvents;
	private int deniedBackgroundEvents;

	@Mod.EventHandler
	public void initialize(FMLInitializationEvent event) {
		if (Boolean.getBoolean("rd.emerald.background")) MinecraftForge.ORE_GEN_BUS.register(this);
	}

	@SubscribeEvent(priority = EventPriority.LOWEST, receiveCanceled = true)
	public void observeEmerald(OreGenEvent.GenerateMinable event) {
		if (!observingBackground || event.getType() != OreGenEvent.GenerateMinable.EventType.EMERALD) return;
		backgroundEvents++;
		if (event.getResult() == Event.Result.DENY) deniedBackgroundEvents++;
	}

	@Mod.EventHandler
	public void started(net.minecraftforge.fml.common.event.FMLServerStartedEvent event) throws Exception {
		String phase = System.getProperty("rd.emerald.phase");
		if (phase == null) return;
		MinecraftServer server = FMLCommonHandler.instance().getMinecraftServerInstance();
		try {
			WorldServer world = server.worldServerForDimension(0);
			require("emerald-test-world".equals(world.getSaveHandler().getWorldDirectory().getName()),
					"Disposable emerald world required");
			world.getGameRules().setOrCreateGameRule("randomTickSpeed", "0");
			JsonObject profile = OreSpawnApi.getActiveProfile(server).get().toJson();
			DepositDefinition local = RealisticDepositsRuntime.state().definitions().stream()
					.filter(DepositDefinition::isEmerald).findFirst().get();
			JsonObject enabled = local.toJson();
			enabled.addProperty("enabled", true);
			DepositDefinition definition = DepositCatalogLoader.loadDefinition(enabled, "emerald fixture");
			boolean physical = LapisGeology.physicalStrata(profile);
			Set<IBlockState> hosts = EmeraldPattern.exactHosts(definition, profile, physical);
			GeologySampler sampler = OreSpawnApi.createSampler(world).get();
			if ("rarity".equals(phase)) {
				write("emerald-rarity.json", rarity(world, definition, sampler, hosts, physical));
				System.out.println("RD_EMERALD_PASS " + phase);
				return;
			}
			JsonObject report = new JsonObject();
			String workings = System.getProperty("rd.emerald.workings", "off");
			report.add("profile", profile);
			report.addProperty("physical", physical);
			report.addProperty("eligible_states", hosts.size());
			if ("first".equals(phase)) {
				JsonObject candidates = targets(world, definition, sampler, hosts, physical);
				write("emerald-targets.json", candidates);
				if (surveyWorkings(workings)) {
					selectWalkableSite(world, definition, sampler, hosts, physical, candidates);
					write("emerald-targets.json", candidates);
				}
				Random random = new Random(44123L);
				int normal = 0, fortune = 0;
				for (int i = 0; i < 4096; i++) {
					normal += Blocks.EMERALD_ORE.quantityDropped(random);
					fortune += Blocks.EMERALD_ORE.quantityDroppedWithBonus(3, random);
				}
				require(normal == 4096 && fortune > normal, "Unexpected native emerald drops");
				report.addProperty("fortune3_mean", fortune / 4096.0);
			}
			JsonObject targets;
			try (FileReader reader = new FileReader("emerald-targets.json")) {
				targets = new JsonParser().parse(reader).getAsJsonObject();
			}
			report.add("targets", targets);
			if (targets.has("first_rx")) {
				EmeraldDistrict first = plan(world, definition, sampler, hosts, physical,
						targets.get("first_rx").getAsInt(), targets.get("first_rz").getAsInt()).get();
				BlockPos obstruction = null;
				if ("first".equals(phase)) {
					if (workings.endsWith("wet") || "infested".equals(workings) || "structure".equals(workings)) {
						obstruction = obstruct(world, definition, first, workings);
					}
					if (!surveyWorkings(workings)) populate(world, first);
				} else if ("existing".equals(phase)) {
					populateOutside(world, first);
				}
				report.add("first", sample(world, first, sampler, hosts, physical));
				if ("first".equals(phase) && !"off".equals(workings) && MineWorldData.get(world).plan(first.identity().toString()) == null) {
					AbandonedMinePlan proposed = minePlan(world, definition, first).get();
					report.addProperty("no_partial_entrance", noPartialEntrance(world, proposed));
				}
				if (obstruction != null) {
					boolean preserved = "structure".equals(workings)
							? world.getMapStorage().getOrLoadData(MapGenStructureData.class, "Mineshaft") != null
							: world.getBlockState(obstruction).getBlock() == (workings.endsWith("wet")
									? Blocks.WATER : Blocks.MONSTER_EGG);
					report.addProperty("obstruction_preserved", preserved);
				}
			}
			if ("later".equals(phase) && targets.has("later_rx")) {
				EmeraldDistrict later = plan(world, definition, sampler, hosts, physical,
						targets.get("later_rx").getAsInt(), targets.get("later_rz").getAsInt()).get();
				populate(world, later);
				report.add("later", sample(world, later, sampler, hosts, physical));
			}
			if ("first".equals(phase) && Boolean.getBoolean("rd.emerald.background")) {
				observingBackground = true;
				report.add("background", background(world));
				observingBackground = false;
				report.addProperty("background_events", backgroundEvents);
				report.addProperty("background_denied", deniedBackgroundEvents);
			}
			if ("first".equals(phase)) {
				checkSilkTouch(world);
				report.addProperty("silk_touch", true);
			}
			write("emerald-" + phase + ".json", report);
			System.out.println("RD_EMERALD_PASS " + phase);
		} finally {
			server.initiateShutdown();
		}
	}

	private static JsonObject rarity(WorldServer world, DepositDefinition definition, GeologySampler sampler,
			Set<IBlockState> hosts, boolean physical) {
		double[] chances = {0.008, 0.04, 0.05, 0.06, 0.08, 0.10, 0.25};
		DepositDefinition[] variants = new DepositDefinition[chances.length];
		int[] counts = new int[chances.length];
		for (int i = 0; i < chances.length; i++) {
			JsonObject json = definition.toJson();
			json.getAsJsonObject("shape").getAsJsonObject("large_mine").addProperty("chance", chances[i]);
			variants[i] = DepositCatalogLoader.loadDefinition(json, "emerald rarity survey");
		}
		JsonObject ironJson = RealisticDepositsRuntime.state().definitions().stream()
				.filter(d -> "realisticdeposits:overworld_iron_formation".equals(d.id())).findFirst().get().toJson();
		ironJson.addProperty("enabled", true);
		DepositDefinition iron = DepositCatalogLoader.loadDefinition(ironJson, "iron rarity survey");
		require(definition.regionSpacingChunks() == iron.regionSpacingChunks(), "Use equal survey areas");
		int small = 0, rich = 0, ironSelected = 0, ironBelt = 0;
		int loaded = world.getChunkProvider().getLoadedChunkCount();
		for (int x = 16; x < 144; x++) for (int z = -16; z < 112; z++) {
			Optional<StratiformSeamBody> ironBody = RegionDepositPlanner.plan(iron, world.getSeed(), OVERWORLD, x, z);
			if (ironBody.isPresent()) {
				ironSelected++;
				StratiformSeamBody body = ironBody.get();
				MineTerrainSampler terrain = new MineTerrainSampler(world, world.getChunkProvider().chunkGenerator);
				if (EmeraldPattern.site(definition, terrain, sampler, hosts, physical)
						.acceptsGeome(body.centerX(), body.centerZ())) ironBelt++;
			}
			int max = chances.length - 1;
			Optional<EmeraldDistrict> body = plan(world, variants[max], sampler, hosts, physical, x, z);
			if (!body.isPresent()) continue;
			if (body.get().large()) {
				counts[max]++;
				for (int i = max - 1; i >= 0; i--) {
					if (!plan(world, variants[i], sampler, hosts, physical, x, z).isPresent()) break;
					counts[i]++;
				}
			} else if (body.get().rich()) rich++;
			else small++;
		}
		require(loaded == world.getChunkProvider().getLoadedChunkCount(), "Rarity survey loaded additional chunks");
		JsonObject result = new JsonObject(), variantsReport = new JsonObject();
		result.addProperty("seed", world.getSeed());
		result.addProperty("regions", 128 * 128);
		result.addProperty("iron_nominated", ironSelected);
		result.addProperty("iron_with_mountain_belt_anchor", ironBelt);
		result.addProperty("emerald_small_fitted", small);
		result.addProperty("emerald_rich_fitted", rich);
		for (int i = 0; i < chances.length; i++) variantsReport.addProperty(Double.toString(chances[i]), counts[i]);
		result.add("emerald_large_fitted", variantsReport);
		return result;
	}

	private static void checkSilkTouch(WorldServer world) {
		BlockPos point = new BlockPos(8, 200, 8);
		world.getChunkProvider().provideChunk(point.getX() >> 4, point.getZ() >> 4);
		ItemStack tool = new ItemStack(Items.IRON_PICKAXE);
		tool.addEnchantment(Enchantments.SILK_TOUCH, 1);
		Blocks.EMERALD_ORE.harvestBlock(world, FakePlayerFactory.getMinecraft(world), point,
				Blocks.EMERALD_ORE.getDefaultState(), null, tool);
		int count = 0;
		for (EntityItem entity : world.getEntitiesWithinAABB(EntityItem.class,
				new AxisAlignedBB(7, 199, 7, 10, 203, 10), item -> !item.isDead)) {
			ItemStack drop = entity.getEntityItem();
			require(drop.getItem() == Item.getItemFromBlock(Blocks.EMERALD_ORE), "Silk Touch returned an emerald item");
			count += drop.stackSize;
			entity.setDead();
		}
		require(count == 1, "Silk Touch must drop one vanilla emerald ore block");
	}

	private static JsonObject targets(WorldServer world, DepositDefinition definition, GeologySampler sampler,
			Set<IBlockState> hosts, boolean physical) {
		JsonObject result = new JsonObject();
		String workings = System.getProperty("rd.emerald.workings", "off");
		boolean manual = !"off".equals(workings);
		String find = System.getProperty("rd.emerald.find", "");
		int range = Integer.getInteger("rd.emerald.regions", "large".equals(find) ? 128 : manual && "rich".equals(find) ? 64
				: manual || !find.isEmpty() ? 40 : physical ? 24 : 12);
		int eligible = 0, planned = 0, largeCount = 0, mediumCount = 0;
		JsonArray mineCandidates = new JsonArray();
		boolean trySites = surveyWorkings(workings);
		int loaded = world.getChunkProvider().getLoadedChunkCount();
		for (int rx = 16; rx < 16 + range && !hosts.isEmpty(); rx++) {
			for (int rz = -16; rz < -16 + range; rz++) {
				Optional<EmeraldDistrict> option = plan(world, definition, sampler, hosts, physical, rx, rz);
				if (!option.isPresent()) continue;
				eligible++;
				EmeraldDistrict body = option.get();
				planned += body.ore().size();
				// Use the already qualified ordinary trace as the later-chunk
				// positive control; a geometry-only pick can be wholly clipped.
				if ("large-mine".equals(workings) && rx == 32 && rz == 12) {
					result.addProperty("later_rx", rx);
					result.addProperty("later_rz", rz);
				}
				if (body.large()) largeCount++;
				if (body.rich()) mediumCount++;
				boolean requested = "large".equals(find) ? body.large() : "rich".equals(find) ? body.rich() : !body.large();
				// Keep qualifying the same known deposit when the default
				// nomination rate rises and other sites appear earlier.
				if ("large-mine".equals(workings)) requested = body.large() && rx == 36 && rz == 34;
				if ("trace".equals(find)) requested = rx == 32 && rz == 12;
				// Inject into the same site proved walkable without the obstruction.
				if ("rich-wet".equals(workings)) requested = body.rich() && rx == 64 && rz == -15;
				boolean candidate = requested && (trySites ? mineCandidates.size() < 12 : !result.has("first_rx"))
						&& (!manual || safeMine(world, definition, body));
				if (candidate && trySites) {
					JsonObject point = new JsonObject();
					point.addProperty("rx", rx);
					point.addProperty("rz", rz);
					mineCandidates.add(point);
				}
				if (candidate && !result.has("first_rx")) {
					result.addProperty("first_rx", rx);
					result.addProperty("first_rz", rz);
				} else if (result.has("first_rx") && !result.has("later_rx") && !body.large() && (!trySites || !body.rich())
						&& rx != result.get("first_rx").getAsInt()) {
					result.addProperty("later_rx", rx);
					result.addProperty("later_rz", rz);
				}
			}
		}
		require(loaded == world.getChunkProvider().getLoadedChunkCount(), "Planning loaded neighbouring chunks");
		if (manual) require(result.has("first_rx"), "No safe mine fixture in the fixed search window");
		if (!find.isEmpty()) require(result.has("first_rx"), "No matching find in the fixed search window");
		result.addProperty("regions", range * range);
		result.addProperty("accepted", eligible);
		result.addProperty("large_count", largeCount);
		result.addProperty("medium_count", mediumCount);
		result.addProperty("planned_total", planned);
		if (trySites) result.add("mine_candidates", mineCandidates);
		return result;
	}

	private static boolean surveyWorkings(String workings) {
		return "rich-mine".equals(workings) || "large-mine".equals(workings);
	}

	private static void selectWalkableSite(WorldServer world, DepositDefinition definition, GeologySampler sampler,
			Set<IBlockState> hosts, boolean physical, JsonObject targets) throws Exception {
		MineWorldData data = MineWorldData.get(world);
		for (com.google.gson.JsonElement candidate : targets.getAsJsonArray("mine_candidates")) {
			JsonObject site = candidate.getAsJsonObject();
			int rx = site.get("rx").getAsInt(), rz = site.get("rz").getAsInt();
			EmeraldDistrict body = plan(world, definition, sampler, hosts, physical, rx, rz).get();
			populate(world, body);
			String key = body.identity().toString();
			if (data.plan(key) != null) {
				targets.addProperty("first_rx", rx);
				targets.addProperty("first_rz", rz);
				return;
			}
			site.addProperty("reason", data.rejectionReason(key));
			write("emerald-targets.json", targets);
		}
		throw new AssertionError("No walkable linked mine among the bounded natural candidates; see emerald-targets.json");
	}

	private static boolean safeMine(WorldServer world, DepositDefinition definition, EmeraldDistrict body) {
		return minePlan(world, definition, body).isPresent();
	}

	static Optional<AbandonedMinePlan> minePlan(WorldServer world, DepositDefinition definition, EmeraldDistrict body) {
		MineTerrainSampler terrain = new MineTerrainSampler(world, world.getChunkProvider().chunkGenerator);
		java.util.function.Predicate<AbandonedMinePlan> safe = plan -> terrain.surfaceY(plan.entrance()) == plan.entrance().y()
				&& terrain.dry(plan) && terrain.covered(plan) && MineWorkingsGenerator.biomesAllowed(plan, definition, world);
		return body.linkedWorkings(definition)
				? RichEmeraldMinePlanner.plan(body, definition.abandonedMine(),
						(x, z) -> terrain.surfaceY(new MinePoint(x, 0, z)), safe)
				: EmeraldMinePlanner.plan(body, definition.abandonedMine(), terrain::naturalSurfaceY, safe);
	}

	static boolean noPartialEntrance(WorldServer world, AbandonedMinePlan proposed) {
		return proposed.blocks().entrySet().stream().filter(cell -> cell.getValue().name().startsWith("STAIRS_"))
				.map(cell -> MinePoint.unpack(cell.getKey()))
				.noneMatch(p -> world.getBlockState(new BlockPos(p.x(), p.y(), p.z())).getBlock() == Blocks.OAK_STAIRS);
	}

	private static BlockPos obstruct(WorldServer world, DepositDefinition definition, EmeraldDistrict body,
			String scenario) {
		AbandonedMinePlan plan = minePlan(world, definition, body).get();
		if ("structure".equals(scenario)) {
			MinePoint p = plan.entrance();
			MapGenStructureData structures = new MapGenStructureData("Mineshaft");
			NBTTagCompound marker = new NBTTagCompound();
			marker.setIntArray("BB", new int[] {p.x(), p.y(), p.z(), p.x(), p.y() + 1, p.z()});
			structures.writeInstance(marker, p.x() >> 4, p.z() >> 4);
			world.getMapStorage().setData("Mineshaft", structures);
			return new BlockPos(p.x(), p.y(), p.z());
		}
		MinePoint p = plan.landing();
		if ("rich-wet".equals(scenario)) {
			// Linked galleries are appended after the compact shaft and crosscut.
			p = plan.blocks().entrySet().stream().filter(cell -> cell.getValue() == MineBlock.AIR)
					.map(cell -> MinePoint.unpack(cell.getKey())).filter(cell -> cell.y() == plan.landing().y())
					.reduce((a, b) -> b).get();
			p = new MinePoint(p.x(), p.y() - 1, p.z());
		}
		BlockPos pos = new BlockPos(p.x(), p.y() + 1, p.z());
		net.minecraft.world.chunk.Chunk chunk = world.getChunkProvider().provideChunk(p.x() >> 4, p.z() >> 4);
		require(!chunk.isTerrainPopulated(), "Inject obstruction before the mine pass");
		chunk.setBlockState(pos, (scenario.endsWith("wet") ? Blocks.WATER : Blocks.MONSTER_EGG).getDefaultState());
		return pos;
	}

	private static void populateOutside(WorldServer world, EmeraldDistrict body) throws Exception {
		ChunkProviderServer provider = world.getChunkProvider();
		ExistingMineTerrain existing = ExistingMineTerrain.read(new java.io.File(
				world.getSaveHandler().getWorldDirectory(), "region"));
		require(existing.intersects(body.bounds()), "The first pass must leave populated pocket chunks");
		MineWorldData data = MineWorldData.get(world);
		int cx = body.centerX() >> 4, cz = body.centerZ() >> 4;
		for (int radius = 1; radius <= 5; radius++) {
			for (int dx = -radius; dx <= radius; dx++) for (int dz = -radius; dz <= radius; dz++) {
				if (Math.max(Math.abs(dx), Math.abs(dz)) != radius || existing.contains(cx + dx, cz + dz)) continue;
				provider.provideChunk(cx + dx, cz + dz).populateChunk(provider, provider.chunkGenerator);
				if (data.decided(body.identity().toString())) return;
			}
		}
	}

	private static Optional<EmeraldDistrict> plan(WorldServer world, DepositDefinition definition,
			GeologySampler sampler, Set<IBlockState> hosts, boolean physical, int rx, int rz) {
		MineTerrainSampler terrain = new MineTerrainSampler(world, world.getChunkProvider().chunkGenerator);
		return EmeraldDistrict.plan(definition, world.getSeed(), OVERWORLD, rx, rz,
				EmeraldPattern.site(definition, terrain, sampler, hosts, physical));
	}

	private static void populate(WorldServer world, EmeraldDistrict body) {
		ChunkProviderServer provider = world.getChunkProvider();
		List<Long> cells = new ArrayList<>();
		int minX = (body.bounds().minimumX() >> 4) - 2, maxX = (body.bounds().maximumX() >> 4) + 2;
		int minZ = (body.bounds().minimumZ() >> 4) - 2, maxZ = (body.bounds().maximumZ() >> 4) + 2;
		for (int x = minX; x <= maxX; x++) for (int z = minZ; z <= maxZ; z++) cells.add(MinePoint.chunkKey(x, z));
		if (Boolean.getBoolean("rd.emerald.reverse")) Collections.reverse(cells);
		for (long cell : cells) provider.provideChunk((int) (cell >> 32), (int) cell);
		for (long cell : cells) provider.provideChunk((int) (cell >> 32), (int) cell)
				.populateChunk(provider, provider.chunkGenerator);

		AbandonedMinePlan mine = MineWorldData.get(world).plan(body.identity().toString());
		if (mine == null) return;
		for (long packed : mine.blocks().keySet()) {
			MinePoint point = MinePoint.unpack(packed);
			provider.provideChunk(point.x() >> 4, point.z() >> 4).populateChunk(provider, provider.chunkGenerator);
		}
	}

	private static JsonObject sample(WorldServer world, EmeraldDistrict body, GeologySampler sampler,
			Set<IBlockState> hosts, boolean physical) {
		JsonObject result = new JsonObject();
		JsonArray positions = new JsonArray();
		for (MinePoint point : body.ore()) {
			BlockPos pos = new BlockPos(point.x(), point.y(), point.z());
			if (world.getBlockState(pos).getBlock() != Blocks.EMERALD_ORE) continue;
			if (physical) require(hosts.contains(sampler.sampleColumn(point.x(), point.z(),
					world.getHeight(pos).getY()).rockAt(point.y())), "Emerald outside predicted natural schist");
			positions.add(new JsonPrimitive(point.toString()));
		}
		result.add("ore", positions);
		result.addProperty("planned", body.ore().size());
		result.addProperty("rich", body.rich());
		if (body.large()) {
			result.addProperty("large", true);
			JsonArray zones = new JsonArray();
			for (int i = 0; i < body.pockets().size(); i += 2) {
				int ore = 0;
				for (List<MinePoint> pocket : body.pockets().subList(i, i + 2)) for (MinePoint p : pocket) {
					if (world.getBlockState(new BlockPos(p.x(), p.y(), p.z())).getBlock() == Blocks.EMERALD_ORE) ore++;
				}
				zones.add(new JsonPrimitive(ore));
			}
			result.add("zone_ore", zones);
		}
		result.addProperty("pockets", body.pockets().size());
		result.addProperty("strike", body.strikeLength());
		JsonArray trace = new JsonArray();
		for (int x = body.bounds().minimumX() >> 4; x <= body.bounds().maximumX() >> 4; x++) {
			for (int z = body.bounds().minimumZ() >> 4; z <= body.bounds().maximumZ() >> 4; z++) {
				for (MinePoint p : body.traceInChunk(x, z)) {
					if (world.getBlockState(new BlockPos(p.x(), p.y(), p.z())) == Blocks.STONE.getStateFromMeta(3)) {
						trace.add(new JsonPrimitive(p.toString()));
					}
				}
			}
		}
		result.add("trace", trace);
		result.addProperty("center", body.centerX() + "," + body.centerY() + "," + body.centerZ());
		result.addProperty("surface_mountain", world.getBiomeProvider()
				.getBiome(new BlockPos(body.centerX(), 0, body.centerZ())) instanceof BiomeHills);
		MineWorldData data = MineWorldData.get(world);
		String key = body.identity().toString();
		AbandonedMinePlan mine = data.plan(key);
		if (mine != null) {
			require(body.ore().stream().noneMatch(ore -> mine.blocks().containsKey(ore.packed())),
					"Saved workings consume planned ore");
			result.addProperty("entrance", mine.entrance().toString());
			result.addProperty("landing", mine.landing().toString());
			boolean open = openRoute(world, mine);
			result.addProperty("open_route", open);
			if (open && (body.large() || body.rich() && System.getProperty("rd.emerald.workings", "").startsWith("rich-"))) {
				result.addProperty("walked_pockets", EmeraldWalkProbe.verifyLinked(world, mine, body));
			}
			if (open && System.getProperty("rd.emerald.workings", "off").startsWith("manual")) {
				EmeraldWalkProbe.verify(world, mine);
			}
		} else if (data.rejectionReason(key) != null) {
			result.addProperty("rejected_workings", data.rejectionReason(key));
		}
		return result;
	}

	private static boolean openRoute(WorldServer world, AbandonedMinePlan mine) {
		Set<Long> visited = new HashSet<>();
		ArrayDeque<MinePoint> pending = new ArrayDeque<>();
		pending.add(mine.entrance());
		while (!pending.isEmpty()) {
			MinePoint p = pending.removeFirst();
			if (!visited.add(p.packed()) || !mine.blocks().containsKey(p.packed())
					|| !mine.blocks().get(p.packed()).passable()) continue;
			IBlockState state = world.getBlockState(new BlockPos(p.x(), p.y(), p.z()));
			if (state.getMaterial().isLiquid() || (state.getMaterial().isSolid()
					&& state.getBlock() != Blocks.OAK_STAIRS)) continue;
			if (p.equals(mine.landing())) return true;
			for (int[] side : SIDES) pending.add(new MinePoint(p.x() + side[0], p.y() + side[1], p.z() + side[2]));
		}
		return false;
	}

	private static JsonArray background(WorldServer world) {
		ChunkProviderServer provider = world.getChunkProvider();
		// Match the auxiliary world RNG as well as the saved world seed. Forge
		// decorators can advance it while the probe samples a distant pocket.
		world.rand.setSeed(2718281828L);
		for (int cx = -72; cx <= 72; cx += 4) {
			for (int cz = -72; cz <= 72; cz += 4) {
				if (!(world.getBiomeProvider().getBiome(new BlockPos(cx * 16 + 8, 0, cz * 16 + 8)) instanceof BiomeHills)) {
					continue;
				}
				JsonArray ore = new JsonArray();
				for (int x = cx - 1; x <= cx + 4; x++) for (int z = cz - 1; z <= cz + 4; z++) {
					provider.provideChunk(x, z);
				}
				for (int x = cx - 1; x <= cx + 4; x++) for (int z = cz - 1; z <= cz + 4; z++) {
					provider.provideChunk(x, z).populateChunk(provider, provider.chunkGenerator);
				}
				BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();
				for (int x = cx * 16; x < cx * 16 + 64; x++) for (int z = cz * 16; z < cz * 16 + 64; z++) {
					for (int y = 0; y <= 40; y++) {
						if (world.getBlockState(p.setPos(x, y, z)).getBlock() == Blocks.EMERALD_ORE) {
							ore.add(new JsonPrimitive(new MinePoint(x, y, z).toString()));
						}
					}
				}
				return ore;
			}
		}
		throw new AssertionError("Could not find a native hill biome for the background comparison");
	}

	private static void write(String path, JsonObject value) throws Exception {
		try (FileWriter writer = new FileWriter(path)) { writer.write(value.toString()); }
	}

	private static void require(boolean condition, String message) {
		if (!condition) throw new AssertionError(message);
	}
}
