package zone.moddev.mc.realisticdeposits.generation;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import net.minecraft.block.Block;
import net.minecraft.block.state.IBlockState;
import net.minecraft.init.Blocks;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.WorldServer;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.gen.ChunkProviderServer;
import net.minecraftforge.fml.common.FMLCommonHandler;
import net.minecraftforge.fml.common.Mod;

import zone.moddev.mc.orespawn.api.OreSpawnApi;
import zone.moddev.mc.realisticdeposits.config.DepositCatalogLoader;
import zone.moddev.mc.realisticdeposits.config.RealisticDepositsRuntime;
import zone.moddev.mc.realisticdeposits.geometry.BlockBounds;
import zone.moddev.mc.realisticdeposits.geometry.LapisDistrict;
import zone.moddev.mc.realisticdeposits.model.DepositDefinition;

/** A separately packaged probe; ordinary launches never load it. */
@Mod(modid = "rdlapisprobe", name = "Lapis Qualification", version = "1",
		dependencies = "required-after:realisticdeposits")
public final class LapisRuntimeProbe {
	private static final int SIDE = 8;

	@Mod.EventHandler
	public void started(net.minecraftforge.fml.common.event.FMLServerStartedEvent event) throws Exception {
		if (!Boolean.getBoolean("rd.lapis.enabled")) return;
		MinecraftServer server = FMLCommonHandler.instance().getMinecraftServerInstance();

		try {
			WorldServer world = server.worldServerForDimension(0);
			if (!"lapis-test-world".equals(world.getSaveHandler().getWorldDirectory().getName())) {
				throw new IllegalStateException("Lapis checks require their disposable world");
			}

			world.getGameRules().setOrCreateGameRule("randomTickSpeed", "0");
			DepositDefinition captured = RealisticDepositsRuntime.state().definitions().stream()
					.filter(DepositDefinition::isLapis).findFirst().get();
			JsonObject enabled = captured.toJson();
			enabled.addProperty("enabled", true);
			DepositDefinition definition = DepositCatalogLoader.loadDefinition(enabled, "lapis fixture geometry");
			String phase = System.getProperty("rd.lapis.phase", "first");
			File target = new File("lapis-target.json");
			JsonObject coordinates;

			if (target.isFile()) {
				try (FileReader reader = new FileReader(target)) {
					coordinates = new JsonParser().parse(reader).getAsJsonObject();
				}
			} else {
				coordinates = new JsonObject();
				int loaded = world.getChunkProvider().getLoadedChunkCount();
				find(world, definition, 32, "first", coordinates);
				find(world, definition, 96, "later", coordinates);
				if (loaded != world.getChunkProvider().getLoadedChunkCount()) {
					throw new AssertionError("Site selection loaded neighbouring chunks");
				}
				write(target, coordinates);
			}

			LapisDistrict first = resolve(world, definition, coordinates, "first");
			LapisDistrict later = resolve(world, definition, coordinates, "later");
			if ("first".equals(phase)) populate(world, first);
			if ("later".equals(phase)) populate(world, later);

			JsonObject report = new JsonObject();
			report.addProperty("phase", phase);
			if ("first".equals(phase)) report.add("harvesting", LapisHarvestRuntimeChecks.run(world));
			report.add("first", sample(world, first));
			if ("later".equals(phase) || "stable".equals(phase)) report.add("later", sample(world, later));
			report.add("profile", OreSpawnApi.getActiveProfile(server).get().toJson());
			write(new File("lapis-" + phase + ".json"), report);
			System.out.println("RD_LAPIS_PASS " + phase);
		} finally {
			server.initiateShutdown();
		}
	}

	static LapisDistrict.Site site(WorldServer world, DepositDefinition definition) {
		JsonObject profile = OreSpawnApi.getActiveProfile(world.getMinecraftServer()).get().toJson();
		boolean physical = LapisGeology.physicalStrata(profile);

		return LapisPattern.site(definition, new MineTerrainSampler(world, world.getChunkProvider().chunkGenerator),
				OreSpawnApi.createSampler(world), LapisGeology.hosts(definition, profile, physical), physical);
	}

	private static void find(WorldServer world, DepositDefinition definition, int start, String prefix, JsonObject output) {
		LapisDistrict.Site site = site(world, definition);

		for (int rx = start; rx < start + 48; rx++) {
			for (int rz = start; rz < start + 48; rz++) {
				Optional<LapisDistrict> planned = LapisDistrict.plan(definition, world.getSeed(),
						"minecraft:overworld", rx, rz, site);
				if (!planned.isPresent()) continue;

				output.addProperty(prefix + "_rx", rx);
				output.addProperty(prefix + "_rz", rz);
				return;
			}
		}

		throw new AssertionError("No suitable lapis lenses in bounded fixture search");
	}

	private static LapisDistrict resolve(WorldServer world, DepositDefinition definition, JsonObject coordinates, String prefix) {
		return LapisDistrict.plan(definition, world.getSeed(), "minecraft:overworld",
				coordinates.get(prefix + "_rx").getAsInt(), coordinates.get(prefix + "_rz").getAsInt(),
				site(world, definition)).get();
	}

	private static void populate(WorldServer world, LapisDistrict district) {
		ChunkProviderServer provider = world.getChunkProvider();
		int baseX = (district.centerX() >> 4) - SIDE / 2;
		int baseZ = (district.centerZ() >> 4) - SIDE / 2;
		List<int[]> halo = new ArrayList<>();

		for (int x = baseX - 2; x <= baseX + SIDE + 1; x++) {
			for (int z = baseZ - 2; z <= baseZ + SIDE + 1; z++) halo.add(new int[] {x, z});
		}

		if (Boolean.getBoolean("rd.lapis.reverse")) Collections.reverse(halo);
		for (int[] cell : halo) provider.provideChunk(cell[0], cell[1]);

		List<int[]> order = new ArrayList<>();
		for (int x = baseX - 1; x <= baseX + SIDE; x++) {
			for (int z = baseZ - 1; z <= baseZ + SIDE; z++) order.add(new int[] {x, z});
		}

		if (Boolean.getBoolean("rd.lapis.reverse")) Collections.reverse(order);
		for (int[] cell : order) provider.provideChunk(cell[0], cell[1]).populateChunk(provider, provider.chunkGenerator);
	}

	private static JsonObject sample(WorldServer world, LapisDistrict district) throws Exception {
		MessageDigest terrain = MessageDigest.getInstance("SHA-256");
		MessageDigest lapis = MessageDigest.getInstance("SHA-256");
		int baseX = (district.centerX() >> 4) - SIDE / 2;
		int baseZ = (district.centerZ() >> 4) - SIDE / 2;
		int ore = 0;
		int outside = 0;
		JsonArray section = new JsonArray();
		BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
		DepositDefinition definition = RealisticDepositsRuntime.state().definitions().stream()
				.filter(DepositDefinition::isLapis).findFirst().get();
		JsonObject profile = OreSpawnApi.getActiveProfile(world.getMinecraftServer()).get().toJson();
		boolean physical = LapisGeology.physicalStrata(profile);
		Set<IBlockState> hosts = LapisGeology.hosts(definition, profile, physical);
		BlockBounds bounds = district.bounds();
		java.util.Optional<zone.moddev.mc.orespawn.api.GeologySampler> sampler = OreSpawnApi.createSampler(world);

		for (int cx = baseX; cx < baseX + SIDE; cx++) {
			for (int cz = baseZ; cz < baseZ + SIDE; cz++) {
				Chunk chunk = world.getChunkProvider().loadChunk(cx, cz);
				if (chunk == null || !chunk.isTerrainPopulated()) throw new AssertionError("Unopened sample chunk");

				for (int x = cx * 16; x < cx * 16 + 16; x++) {
					for (int z = cz * 16; z < cz * 16 + 16; z++) {
						for (int y = 0; y < 160; y++) {
							IBlockState state = chunk.getBlockState(pos.setPos(x, y, z));
							int id = Block.getStateId(state);
							terrain.update((byte) (id >>> 8));
							terrain.update((byte) id);

							if (state.getBlock() == Blocks.LAPIS_ORE) {
								if (district.isOreAt(x, y, z)) {
									if (physical && (!sampler.isPresent() || !hosts.contains(sampler.get()
											.sampleColumn(x, z, chunk.getHeightValue(x & 15, z & 15)).rockAt(y)))) {
										throw new AssertionError("Lapis escaped its sampled natural marble host");
									}
									ore++;
								}
								else outside++;
								lapis.update((byte) (x >>> 8));
								lapis.update((byte) x);
								lapis.update((byte) y);
								lapis.update((byte) (z >>> 8));
								lapis.update((byte) z);
							}

							if (z == district.centerZ() && x >= bounds.minimumX() && x <= bounds.maximumX()
									&& y >= 8 && y <= 128) {
								JsonArray cell = new JsonArray();
								cell.add(new com.google.gson.JsonPrimitive(x));
								cell.add(new com.google.gson.JsonPrimitive(y));
								cell.add(new com.google.gson.JsonPrimitive(state.getBlock().getRegistryName().toString()));
								cell.add(new com.google.gson.JsonPrimitive(hosts.contains(state)));
								section.add(cell);
							}
						}
					}
				}
			}
		}

		JsonObject result = new JsonObject();
		result.addProperty("terrain_sha256", hex(terrain.digest()));
		result.addProperty("lapis_sha256", hex(lapis.digest()));
		result.addProperty("lens_ore", ore);
		result.addProperty("outside_ore", outside);
		result.addProperty("expected_pieces", ore * 6);
		result.addProperty("physical_strata", physical);
		result.addProperty("center_x", district.centerX());
		result.addProperty("center_y", district.centerY());
		result.addProperty("center_z", district.centerZ());
		result.add("section", section);
		JsonArray strikeSection = new JsonArray();
		int reach = (int) (district.strikeLength() * 0.35);

		for (int along = -reach; along <= reach; along++) {
			zone.moddev.mc.realisticdeposits.geometry.MinePoint point = district.miningRoutePoint(
					along, district.lenses().get(0).width() * 0.3);
			Chunk chunk = world.getChunkProvider().getLoadedChunk(point.x() >> 4, point.z() >> 4);
			if (chunk == null || !chunk.isTerrainPopulated()) continue;

			for (int y = 8; y <= 128; y++) {
				IBlockState state = chunk.getBlockState(pos.setPos(point.x(), y, point.z()));
				JsonArray cell = new JsonArray();
				cell.add(new com.google.gson.JsonPrimitive(along));
				cell.add(new com.google.gson.JsonPrimitive(y));
				cell.add(new com.google.gson.JsonPrimitive(state.getBlock().getRegistryName().toString()));
				cell.add(new com.google.gson.JsonPrimitive(hosts.contains(state)));
				cell.add(new com.google.gson.JsonPrimitive(point.x()));
				cell.add(new com.google.gson.JsonPrimitive(point.z()));
				strikeSection.add(cell);
			}
		}

		result.add("strike_section", strikeSection);

		return result;
	}

	private static String hex(byte[] bytes) {
		StringBuilder result = new StringBuilder();
		for (byte value : bytes) result.append(String.format("%02X", value & 255));

		return result.toString();
	}

	private static void write(File file, JsonObject json) throws Exception {
		try (FileWriter output = new FileWriter(file)) {
			new GsonBuilder().setPrettyPrinting().create().toJson(json, output);
		}
	}
}
