package zone.moddev.mc.realisticdeposits.generation;

import java.io.File;
import java.io.FileWriter;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Random;
import java.util.Set;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import net.minecraft.block.Block;
import net.minecraft.block.state.IBlockState;
import net.minecraft.init.Blocks;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.WorldServer;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.gen.ChunkProviderOverworld;
import net.minecraftforge.fml.common.FMLCommonHandler;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.event.FMLServerStartedEvent;

import zone.moddev.mc.orespawn.api.OreSpawnApi;
import zone.moddev.mc.realisticdeposits.config.RealisticDepositsRuntime;
import zone.moddev.mc.realisticdeposits.geometry.BlockBounds;
import zone.moddev.mc.realisticdeposits.geometry.LapisDistrict;
import zone.moddev.mc.realisticdeposits.model.DepositDefinition;

/** Measures whole districts in memory and ordinary lapis in separate decorated controls. */
@Mod(modid = "rdlapissurvey", name = "Lapis Reward Survey", version = "1",
		dependencies = "required-after:realisticdeposits")
public final class LapisSurveyProbe {
	@Mod.EventHandler
	public void started(FMLServerStartedEvent event) throws Exception {
		if (!Boolean.getBoolean("rd.lapis.survey")) return;
		MinecraftServer server = FMLCommonHandler.instance().getMinecraftServerInstance();

		try {
			WorldServer world = server.worldServerForDimension(0);
			if (!"lapis-test-world".equals(world.getSaveHandler().getWorldDirectory().getName())) {
				throw new AssertionError("The survey requires its disposable world");
			}

			JsonObject report = Boolean.getBoolean("rd.lapis.survey.control") ? control(world) : lenses(world);
			report.addProperty("seed", world.getSeed());
			try (FileWriter output = new FileWriter(new File("lapis-survey.json"))) {
				new GsonBuilder().setPrettyPrinting().create().toJson(report, output);
			}

			System.out.println("RD_LAPIS_SURVEY_PASS");
		} finally {
			server.initiateShutdown();
		}
	}

	private static JsonObject lenses(WorldServer world) {
		DepositDefinition definition = RealisticDepositsRuntime.state().definitions().stream()
				.filter(DepositDefinition::isLapis).findFirst().get();
		ChunkProviderOverworld generator = new ChunkProviderOverworld(world, world.getSeed(), false,
				world.getWorldInfo().getGeneratorOptions());
		MineTerrainSampler terrain = new MineTerrainSampler(world, generator);
		JsonObject profile = OreSpawnApi.getActiveProfile(world.getMinecraftServer()).get().toJson();
		boolean physical = LapisGeology.physicalStrata(profile);
		Set<IBlockState> hosts = LapisGeology.hosts(definition, profile, physical);
		LapisDistrict.Site site = LapisPattern.site(definition, terrain, OreSpawnApi.createSampler(world), hosts, physical);
		int loaded = world.getChunkProvider().getLoadedChunkCount();
		int side = Integer.getInteger("rd.lapis.survey.regions", 16);
		int size = definition.regionSpacingChunks() * 16;
		long total = 0;
		JsonArray reserves = new JsonArray();
		List<LapisDistrict> productive = new ArrayList<>();
		Set<Long> counted = new HashSet<>();
		java.util.Map<Long, Chunk> chunks = new java.util.LinkedHashMap<Long, Chunk>() {
			@Override
			protected boolean removeEldestEntry(java.util.Map.Entry<Long, Chunk> entry) {
				return size() > 128;
			}
		};
		BlockPos.MutableBlockPos position = new BlockPos.MutableBlockPos();
		if (physical) throw new AssertionError("The reserve survey is a standalone control, not a strata simulation");

		for (int rx = 256; rx < 256 + side; rx++) {
			for (int rz = 256; rz < 256 + side; rz++) {
				Optional<LapisDistrict> selected = LapisDistrict.plan(definition, world.getSeed(),
						"minecraft:overworld", rx, rz, site);
				if (!selected.isPresent()) continue;

				LapisDistrict body = selected.get();
				BlockBounds bounds = body.bounds();
				long ore = 0;

				for (int x = bounds.minimumX(); x <= bounds.maximumX(); x++) {
					for (int z = bounds.minimumZ(); z <= bounds.maximumZ(); z++) {
						for (int y = body.minimumCandidateYAt(x, z); y <= body.maximumCandidateYAt(x, z); y++) {
							if (!body.isOreAt(x, y, z)) continue;

							int cx = Math.floorDiv(x, 16);
							int cz = Math.floorDiv(z, 16);
							long key = ((long) cx << 32) ^ (cz & 0xffffffffL);
							Chunk chunk = chunks.computeIfAbsent(key, ignored -> generator.provideChunk(cx, cz));
							IBlockState state = chunk.getBlockState(position.setPos(x, y, z));
							if (hosts.contains(state) && counted.add(position.toLong())) ore++;
						}
					}
				}

				if (ore == 0) continue;
				total += ore;
				productive.add(body);
				JsonObject reserve = new JsonObject();
				reserve.addProperty("x", body.centerX());
				reserve.addProperty("y", body.centerY());
				reserve.addProperty("z", body.centerZ());
				reserve.addProperty("ore", ore);
				reserves.add(reserve);
			}

			System.out.println("Lapis reserve row " + (rx - 255) + "/" + side);
		}

		if (loaded != world.getChunkProvider().getLoadedChunkCount()) {
			throw new AssertionError("The in-memory survey loaded real world chunks");
		}

		JsonArray distances = new JsonArray();
		if (!productive.isEmpty() && side > 2) {
			for (int gx = 0; gx < 32; gx++) {
				for (int gz = 0; gz < 32; gz++) {
					double x = 257.0 * size + (gx + 0.5) * (side - 2) * size / 32;
					double z = 257.0 * size + (gz + 0.5) * (side - 2) * size / 32;
					double distance = Double.POSITIVE_INFINITY;

					for (LapisDistrict body : productive) {
						double dx = x - body.centerX();
						double dz = z - body.centerZ();
						distance = Math.min(distance, Math.sqrt(dx * dx + dz * dz));
					}

					distances.add(new com.google.gson.JsonPrimitive(distance));
				}
			}
		}

		JsonObject result = new JsonObject();
		result.addProperty("represented_chunks", (long) side * side * size * size / 256);
		result.addProperty("lens_ore", total);
		result.addProperty("productive_districts", productive.size());
		result.addProperty("loaded_chunks_unchanged", true);
		result.add("district_reserves", reserves);
		result.add("prospecting_distances", distances);

		return result;
	}

	private static JsonObject control(WorldServer world) {
		if (RealisticDepositsRuntime.state().definitions().stream().anyMatch(DepositDefinition::enabled)) {
			throw new AssertionError("Vanilla controls must disable all deposit definitions");
		}

		int side = 4;
		int extent = Integer.getInteger("rd.lapis.survey.regions", 16) * 64;
		int ore = 0;
		Random random = new Random(world.getSeed() ^ 0x16E1C830A5572BL);
		BlockPos.MutableBlockPos position = new BlockPos.MutableBlockPos();

		for (int gx = 0; gx < 4; gx++) {
			for (int gz = 0; gz < 4; gz++) {
				int startX = 256 * 64 + gx * extent / 4 + random.nextInt(Math.max(1, extent / 4 - side));
				int startZ = 256 * 64 + gz * extent / 4 + random.nextInt(Math.max(1, extent / 4 - side));

				for (int cx = startX - 2; cx <= startX + side + 1; cx++) {
					for (int cz = startZ - 2; cz <= startZ + side + 1; cz++) world.getChunkProvider().provideChunk(cx, cz);
				}

				for (int cx = startX - 1; cx <= startX + side; cx++) {
					for (int cz = startZ - 1; cz <= startZ + side; cz++) {
						world.getChunkProvider().provideChunk(cx, cz).populateChunk(world.getChunkProvider(),
								world.getChunkProvider().chunkGenerator);
					}
				}

				for (int cx = startX; cx < startX + side; cx++) {
					for (int cz = startZ; cz < startZ + side; cz++) {
						Chunk chunk = world.getChunkProvider().getLoadedChunk(cx, cz);
						for (int x = 0; x < 16; x++) {
							for (int z = 0; z < 16; z++) {
								for (int y = 0; y < 128; y++) {
									Block block = chunk.getBlockState(position.setPos(cx * 16 + x, y, cz * 16 + z)).getBlock();
									if (block == Blocks.LAPIS_ORE) ore++;
								}
							}
						}
					}
				}

				world.getChunkProvider().unloadAllChunks();
				for (int pass = 0; pass < 12; pass++) world.getChunkProvider().tick();
			}
		}

		JsonObject result = new JsonObject();
		result.addProperty("chunks", 16 * side * side);
		result.addProperty("lapis_ore", ore);

		return result;
	}
}
