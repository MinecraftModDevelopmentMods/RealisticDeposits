package zone.moddev.mc.realisticdeposits.generation;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.security.MessageDigest;
import java.util.Optional;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import net.minecraft.block.Block;
import net.minecraft.block.state.IBlockState;
import net.minecraft.init.Blocks;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.WorldServer;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.gen.ChunkProviderServer;
import net.minecraftforge.fml.common.FMLCommonHandler;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.event.FMLServerStartedEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.event.terraingen.PopulateChunkEvent;

import zone.moddev.mc.orespawn.api.GeologySampler;
import zone.moddev.mc.orespawn.api.OreSpawnApi;
import zone.moddev.mc.realisticdeposits.config.DepositCatalogLoader;
import zone.moddev.mc.realisticdeposits.config.RealisticDepositsRuntime;
import zone.moddev.mc.realisticdeposits.geometry.DiamondDistrict;
import zone.moddev.mc.realisticdeposits.model.DepositDefinition;

/** Runs only in the separately packaged, disposable diamond-test server. */
@Mod(modid = "rddiamondprobe", name = "Diamond Qualification", version = "1",
		dependencies = "required-after:realisticdeposits")
public final class DiamondRuntimeProbe {
	private static final int SIDE = 8;
	private static int riverMinX = Integer.MAX_VALUE;
	private static int riverMinZ;
	private static final java.util.Set<Long> installedRiverChunks = new java.util.HashSet<>();

	public DiamondRuntimeProbe() {
		net.minecraftforge.common.MinecraftForge.EVENT_BUS.register(this);
	}

	@SubscribeEvent
	public void beforePopulation(PopulateChunkEvent.Pre event) {
		if (!Boolean.getBoolean("rd.diamond.enabled") || !Boolean.getBoolean("rd.diamond.fixture_river")
				|| event.getChunkX() < riverMinX || event.getChunkX() >= riverMinX + 3
				|| event.getChunkZ() < riverMinZ || event.getChunkZ() >= riverMinZ + SIDE) return;
		if (!(event.getWorld() instanceof WorldServer) || !"diamond-test-world".equals(
				((WorldServer) event.getWorld()).getSaveHandler().getWorldDirectory().getName())) {
			throw new IllegalStateException("The river fixture cannot touch another world");
		}

		Chunk chunk = ((WorldServer) event.getWorld()).getChunkProvider()
				.getLoadedChunk(event.getChunkX(), event.getChunkZ());
		long chunkKey = ((long) event.getChunkX() << 32) ^ (event.getChunkZ() & 0xffffffffL);
		if (chunk == null || !installedRiverChunks.add(chunkKey)) {
			throw new IllegalStateException("The river fixture requires one pre-population event per new chunk");
		}
		for (int x = event.getChunkX() * 16; x < event.getChunkX() * 16 + 16; x++) {
			for (int z = event.getChunkZ() * 16; z < event.getChunkZ() * 16 + 16; z++) {
				for (int y = 58; y < 130; y++) {
					IBlockState state = y <= 60 ? Blocks.GRAVEL.getDefaultState()
							: y <= 63 ? Blocks.WATER.getDefaultState() : Blocks.AIR.getDefaultState();
					chunk.setBlockState(new BlockPos(x, y, z), state);
				}
			}
		}
		chunk.generateSkylightMap();
	}

	@Mod.EventHandler
	public void started(FMLServerStartedEvent event) throws Exception {
		if (!Boolean.getBoolean("rd.diamond.enabled")) return;
		MinecraftServer server = FMLCommonHandler.instance().getMinecraftServerInstance();
		try {
			WorldServer world = server.worldServerForDimension(0);
			if (!"diamond-test-world".equals(world.getSaveHandler().getWorldDirectory().getName())) {
				throw new IllegalStateException("Diamond checks require their disposable world");
			}
			world.getGameRules().setOrCreateGameRule("randomTickSpeed", "0");
			Block gravel = Block.REGISTRY.getObject(new ResourceLocation("realisticdeposits:diamond_gravel"));
			if (gravel == Blocks.AIR) throw new IllegalStateException("Diamond-bearing gravel is not registered");
			DepositDefinition original = RealisticDepositsRuntime.state().definitions().stream()
					.filter(DepositDefinition::isDiamondPipe).findFirst().get();
			JsonObject enabled = original.toJson();
			enabled.addProperty("enabled", true);
			DepositDefinition definition = DepositCatalogLoader.loadDefinition(enabled, "diamond fixture geometry");
			String phase = System.getProperty("rd.diamond.phase", "first");
			File target = new File("diamond-target.json");
			JsonObject coordinates;
			if (target.isFile()) {
				try (FileReader reader = new FileReader(target)) {
					coordinates = new JsonParser().parse(reader).getAsJsonObject();
				}
			} else {
				coordinates = new JsonObject();
				find(world, definition, 32, "first", coordinates);
				find(world, definition, 96, "later", coordinates);
				write(target, coordinates);
			}

			DiamondDistrict first = resolve(world, definition, coordinates, "first");
			DiamondDistrict later = resolve(world, definition, coordinates, "later");
			if ("first".equals(phase)) populate(world, first);
			if ("later".equals(phase)) populate(world, later);
			JsonObject report = new JsonObject();
			report.addProperty("phase", phase);
			if ("first".equals(phase)) report.add("gravel_contract", DiamondGravelRuntimeChecks.run(world, gravel));
			report.add("first", sample(world, first, gravel));
			if ("later".equals(phase) || "stable".equals(phase)) report.add("later", sample(world, later, gravel));
			report.add("profile", OreSpawnApi.getActiveProfile(server).get().toJson());
			write(new File("diamond-" + phase + ".json"), report);
			System.out.println("RD_DIAMOND_PASS " + phase);
		} finally {
			server.initiateShutdown();
		}
	}

	private static DiamondDistrict.Site site(WorldServer world) {
		MineTerrainSampler terrain = new MineTerrainSampler(world, world.getChunkProvider().chunkGenerator);
		Optional<GeologySampler> sampler = OreSpawnApi.createSampler(world);
		return new DiamondDistrict.Site() {
			@Override public int surfaceY(int x, int z) { return terrain.naturalSurfaceY(x, z); }
			@Override public String geome(int x, int z, int surface) {
				return sampler.isPresent() ? sampler.get().sampleColumn(x, z, surface).geome().toString() : null;
			}
		};
	}

	private static void find(WorldServer world, DepositDefinition definition, int start, String prefix, JsonObject output) {
		for (int x = start; x < start + 32; x++) {
			for (int z = start; z < start + 32; z++) {
				Optional<DiamondDistrict> planned = DiamondDistrict.plan(definition, world.getSeed(),
						"minecraft:overworld", x, z, site(world));
				if (!planned.isPresent()) continue;
				output.addProperty(prefix + "_rx", x);
				output.addProperty(prefix + "_rz", z);
				return;
			}
		}
		throw new IllegalStateException("No diamond district in bounded fixture search");
	}

	private static DiamondDistrict resolve(WorldServer world, DepositDefinition definition, JsonObject coordinates, String prefix) {
		return DiamondDistrict.plan(definition, world.getSeed(), "minecraft:overworld",
				coordinates.get(prefix + "_rx").getAsInt(), coordinates.get(prefix + "_rz").getAsInt(), site(world)).get();
	}

	private static void populate(WorldServer world, DiamondDistrict district) {
		ChunkProviderServer provider = world.getChunkProvider();
		int baseX = (district.centerX() >> 4) - SIDE / 2;
		int baseZ = (district.centerZ() >> 4) - SIDE / 2;
		riverMinX = baseX + 4;
		riverMinZ = baseZ;
		java.util.List<int[]> halo = new java.util.ArrayList<>();
		for (int x = baseX - 2; x <= baseX + SIDE + 1; x++) {
			for (int z = baseZ - 2; z <= baseZ + SIDE + 1; z++) halo.add(new int[] {x, z});
		}
		if (Boolean.getBoolean("rd.diamond.reverse")) java.util.Collections.reverse(halo);
		for (int[] cell : halo) provider.provideChunk(cell[0], cell[1]);

		java.util.List<int[]> order = new java.util.ArrayList<>();
		for (int x = baseX - 1; x <= baseX + SIDE; x++) {
			for (int z = baseZ - 1; z <= baseZ + SIDE; z++) order.add(new int[] {x, z});
		}
		if (Boolean.getBoolean("rd.diamond.reverse")) java.util.Collections.reverse(order);
		for (int[] cell : order) provider.provideChunk(cell[0], cell[1]).populateChunk(provider, provider.chunkGenerator);
	}

	private static JsonObject sample(WorldServer world, DiamondDistrict district, Block gravel) throws Exception {
		MessageDigest terrain = MessageDigest.getInstance("SHA-256");
		MessageDigest diamonds = MessageDigest.getInstance("SHA-256");
		int baseX = (district.centerX() >> 4) - SIDE / 2;
		int baseZ = (district.centerZ() >> 4) - SIDE / 2;
		int pipeOre = 0;
		int background = 0;
		int sediment = 0;
		int shallow = 0;
		JsonArray section = new JsonArray();
		BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
		for (int cx = baseX; cx < baseX + SIDE; cx++) {
			for (int cz = baseZ; cz < baseZ + SIDE; cz++) {
				Chunk chunk = world.getChunkProvider().loadChunk(cx, cz);
				if (chunk == null || !chunk.isTerrainPopulated()) throw new IllegalStateException("Unopened sample chunk");
				for (int x = cx * 16; x < cx * 16 + 16; x++) {
					for (int z = cz * 16; z < cz * 16 + 16; z++) {
						for (int y = 0; y < 160; y++) {
							IBlockState state = chunk.getBlockState(pos.setPos(x, y, z));
							update(terrain, Block.getStateId(state));
							if (state.getBlock() == Blocks.DIAMOND_ORE) {
								boolean planned = district.pipes().stream().anyMatch(pipe -> pipe.isOreAt(pos.getX(), pos.getY(), pos.getZ()));
								if (planned) pipeOre++; else background++;
								if (y > 40) shallow++;
								update(diamonds, x); update(diamonds, y); update(diamonds, z);
							}
							if (state.getBlock() == gravel) {
								sediment++;
								update(diamonds, x); update(diamonds, y); update(diamonds, z);
							}
							if (z == district.centerZ() && y <= 140) {
								JsonArray cell = new JsonArray();
								cell.add(new com.google.gson.JsonPrimitive(x));
								cell.add(new com.google.gson.JsonPrimitive(y));
								cell.add(new com.google.gson.JsonPrimitive(state.getBlock().getRegistryName().toString()));
								section.add(cell);
							}
						}
					}
				}
			}
		}
		JsonObject report = new JsonObject();
		report.addProperty("centre_x", district.centerX());
		report.addProperty("centre_z", district.centerZ());
		report.addProperty("pipe_ore", pipeOre);
		report.addProperty("background_ore", background);
		report.addProperty("rich_gravel", sediment);
		report.addProperty("shallow_ore", shallow);
		report.addProperty("terrain_sha256", hex(terrain.digest()));
		report.addProperty("diamonds_sha256", hex(diamonds.digest()));
		report.add("section", section);
		return report;
	}

	private static void update(MessageDigest digest, int value) {
		digest.update((byte) (value >>> 24)); digest.update((byte) (value >>> 16));
		digest.update((byte) (value >>> 8)); digest.update((byte) value);
	}

	private static String hex(byte[] bytes) {
		StringBuilder text = new StringBuilder();
		for (byte value : bytes) text.append(String.format("%02x", value & 255));
		return text.toString();
	}

	private static void write(File file, JsonObject json) throws Exception {
		try (FileWriter writer = new FileWriter(file)) {
			new GsonBuilder().setPrettyPrinting().create().toJson(json, writer);
		}
	}
}
