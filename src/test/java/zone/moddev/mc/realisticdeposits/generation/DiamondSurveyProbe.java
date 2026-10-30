package zone.moddev.mc.realisticdeposits.generation;

import java.io.File;
import java.io.FileWriter;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Random;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;

import net.minecraft.block.material.Material;
import net.minecraft.block.state.IBlockState;
import net.minecraft.init.Blocks;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.WorldServer;
import net.minecraft.world.biome.Biome;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.gen.ChunkProviderOverworld;
import net.minecraftforge.common.BiomeDictionary;
import net.minecraftforge.fml.common.FMLCommonHandler;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.event.FMLServerStartedEvent;

import zone.moddev.mc.orespawn.api.GeologySampler;
import zone.moddev.mc.orespawn.api.OreSpawnApi;
import zone.moddev.mc.realisticdeposits.config.RealisticDepositsRuntime;
import zone.moddev.mc.realisticdeposits.geometry.BlockBounds;
import zone.moddev.mc.realisticdeposits.geometry.DiamondDistrict;
import zone.moddev.mc.realisticdeposits.model.DepositDefinition;

/** Area-weighted native-terrain survey; no survey chunk is installed in the world. */
@Mod(modid = "rddiamondsurvey", name = "Diamond Reward Survey", version = "1",
		dependencies = "required-after:realisticdeposits")
public final class DiamondSurveyProbe {
	private final Map<Long, Optional<DiamondDistrict>> districts = new HashMap<>();
	private final Map<Long, Chunk> terrain = new java.util.LinkedHashMap<Long, Chunk>() {
		@Override protected boolean removeEldestEntry(Map.Entry<Long, Chunk> entry) { return size() > 128; }
	};
	private WorldServer world;
	private ChunkProviderOverworld generator;
	private MineTerrainSampler heights;
	private Optional<GeologySampler> geology;
	private DepositDefinition pipe;
	private DepositDefinition placer;

	@Mod.EventHandler
	public void started(FMLServerStartedEvent event) throws Exception {
		if (!Boolean.getBoolean("rd.diamond.survey")) return;
		MinecraftServer server = FMLCommonHandler.instance().getMinecraftServerInstance();
		try {
			world = server.worldServerForDimension(0);
			if (!"diamond-test-world".equals(world.getSaveHandler().getWorldDirectory().getName())) {
				throw new IllegalStateException("The survey requires its disposable world");
			}
			if (Boolean.getBoolean("rd.diamond.survey.control")) {
				control();
				return;
			}
			pipe = RealisticDepositsRuntime.state().definitions().stream().filter(DepositDefinition::isDiamondPipe).findFirst().get();
			placer = RealisticDepositsRuntime.state().definitions().stream().filter(DepositDefinition::isDiamondPlacer).findFirst().get();
			geology = OreSpawnApi.createSampler(world);
			generator = new ChunkProviderOverworld(world, world.getSeed(), false, world.getWorldInfo().getGeneratorOptions());
			heights = new MineTerrainSampler(world, generator);
			int loadedBefore = world.getChunkProvider().getLoadedChunkCount();
			int side = Integer.getInteger("rd.diamond.survey.regions", 16);
			int regionStart = 256;
			int size = pipe.regionSpacingChunks() * 16;
			boolean nativeSediment = Boolean.getBoolean("rd.diamond.survey.native_sediment");
			long pipeOre = 0;
			java.util.Set<Long> orePositions = new java.util.HashSet<>();
			int productive = 0;
			int bodies = 0;
			for (int rx = regionStart; !nativeSediment && rx < regionStart + side; rx++) {
				for (int rz = regionStart; rz < regionStart + side; rz++) {
					Optional<DiamondDistrict> selected = district(rx, rz);
					if (!selected.isPresent()) continue;
					productive++;
					for (DiamondDistrict.Pipe body : selected.get().pipes()) {
						bodies++;
						BlockBounds bounds = body.bounds();
						BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
						for (int x = bounds.minimumX(); x <= bounds.maximumX(); x++) {
							for (int z = bounds.minimumZ(); z <= bounds.maximumZ(); z++) {
								Chunk chunk = terrain(x >> 4, z >> 4);
								int top = Math.min(body.topY(), chunk.getHeightValue(x & 15, z & 15) - 4);
								for (int y = body.bottomY(); y <= top; y++) {
									if (body.isOreAt(x, y, z) && natural(chunk.getBlockState(pos.setPos(x, y, z)))
											&& orePositions.add(pos.toLong())) pipeOre++;
								}
							}
							}
					}
				}
			}

			int samples = Integer.getInteger("rd.diamond.survey.chunks", 4096);
			Random random = new Random(world.getSeed() ^ 0x795AB7C3D012461FL);
			long rich = 0;
			long eligible = 0;
			long riverColumns = 0;
			long actualRich = 0;
			for (int sample = 0; sample < samples; sample++) {
				int cx = regionStart * pipe.regionSpacingChunks() + random.nextInt(side * pipe.regionSpacingChunks());
				int cz = regionStart * pipe.regionSpacingChunks() + random.nextInt(side * pipe.regionSpacingChunks());
				Biome[] biomes = world.getBiomeProvider().getBiomes(null, cx * 16, cz * 16, 16, 16);
				boolean river = false;
				for (Biome biome : biomes) river |= BiomeDictionary.isBiomeOfType(biome, BiomeDictionary.Type.RIVER);
				if (!river) continue;
				Chunk chunk = nativeSediment ? populated(cx, cz) : terrain(cx, cz);
				BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
				for (int lx = 0; lx < 16; lx++) {
					for (int lz = 0; lz < 16; lz++) {
						if (!BiomeDictionary.isBiomeOfType(biomes[lx + lz * 16], BiomeDictionary.Type.RIVER)) continue;
						riverColumns++;
						int x = cx * 16 + lx;
						int z = cz * 16 + lz;
						DiamondDistrict source = nearest(x, z);
						if (source == null) continue;
						int y = chunk.getHeightValue(lx, lz) - 1;
						IBlockState state = chunk.getBlockState(pos.setPos(x, y, z));
						if (state.getMaterial() != Material.WATER) continue;
						while (y > placer.minimumY() && state.getMaterial() == Material.WATER) {
							state = chunk.getBlockState(pos.setPos(x, --y, z));
						}
						int thickness = placer.placer().thickness().minimum() + (int) Math.floorMod(
								source.identity().seed() ^ x ^ ((long) z << 32),
								placer.placer().thickness().maximum() - placer.placer().thickness().minimum() + 1);
						for (int depth = 0; depth < thickness && y - depth >= placer.minimumY(); depth++) {
							int py = y - depth;
							state = chunk.getBlockState(pos.setPos(x, py, z));
							boolean placedRich = state.getBlock().getRegistryName().toString().equals("realisticdeposits:diamond_gravel");
							if (state.getBlock() != Blocks.SAND && state.getBlock() != Blocks.GRAVEL && !placedRich) break;
							if (py > placer.maximumY() || !chunk.getBlockState(pos.setPos(x, py - 1, z)).getMaterial().isSolid()) continue;
							eligible++;
							if (placedRich) actualRich++;
							if (DiamondDistrict.placerPocket(source.identity().seed() ^ placer.salt(), x, z, depth,
									placer.placer().coverage())) rich++;
						}
					}
				}
				if (nativeSediment && world.getChunkProvider().getLoadedChunkCount() > 800) {
					world.getChunkProvider().unloadAllChunks();
					for (int pass = 0; pass < 12; pass++) world.getChunkProvider().tick();
				}
			}
			if (!nativeSediment && world.getChunkProvider().getLoadedChunkCount() != loadedBefore) {
				throw new IllegalStateException("The in-memory survey loaded real world chunks");
			}
			JsonObject report = new JsonObject();
			report.addProperty("seed", world.getSeed());
			report.addProperty("survey_region_side", side);
			report.addProperty("represented_chunks", (long) side * side * size * size / 256);
			report.addProperty("productive_districts", productive);
			report.addProperty("pipes", bodies);
			report.addProperty("pipe_ore", pipeOre);
			report.addProperty("placer_sample_chunks", samples);
			report.addProperty("river_columns", riverColumns);
			report.addProperty("eligible_sediment", eligible);
			report.addProperty("rich_gravel", rich);
			report.addProperty("actual_rich_gravel", actualRich);
			report.addProperty("native_sediment", nativeSediment);
			report.addProperty("loaded_chunks_unchanged", !nativeSediment);
			try (FileWriter output = new FileWriter(new File("diamond-survey.json"))) {
				new GsonBuilder().setPrettyPrinting().create().toJson(report, output);
			}
			System.out.println("RD_DIAMOND_SURVEY_PASS");
		} finally {
			server.initiateShutdown();
		}
	}

	private Optional<DiamondDistrict> district(int rx, int rz) {
		long key = ((long) rx << 32) ^ (rz & 0xffffffffL);
		return districts.computeIfAbsent(key, ignored -> DiamondDistrict.plan(pipe, world.getSeed(), "minecraft:overworld", rx, rz,
				new DiamondDistrict.Site() {
			@Override public int surfaceY(int x, int z) { return heights.naturalSurfaceY(x, z); }
			@Override public String geome(int x, int z, int y) {
				return geology.isPresent() ? geology.get().sampleColumn(x, z, y).geome().toString() : null;
			}
		}));
	}

	private DiamondDistrict nearest(int x, int z) {
		int reach = placer.placer().maximumSourceDistance();
		int search = reach + pipe.pipe().districtRadius();
		int size = pipe.regionSpacingChunks() * 16;
		DiamondDistrict closest = null;
		double distance = (double) reach * reach;
		for (int rx = Math.floorDiv(x - search, size); rx <= Math.floorDiv(x + search, size); rx++) {
			for (int rz = Math.floorDiv(z - search, size); rz <= Math.floorDiv(z + search, size); rz++) {
				Optional<DiamondDistrict> candidate = district(rx, rz);
				if (candidate.isPresent() && candidate.get().distanceSquared(x, z) < distance) {
					closest = candidate.get();
					distance = closest.distanceSquared(x, z);
				}
			}
		}
		return closest;
	}

	private Chunk terrain(int cx, int cz) {
		long key = ((long) cx << 32) ^ (cz & 0xffffffffL);
		return terrain.computeIfAbsent(key, ignored -> generator.provideChunk(cx, cz));
	}

	private Chunk populated(int cx, int cz) {
		net.minecraft.world.gen.ChunkProviderServer provider = world.getChunkProvider();
		for (int dx = -2; dx <= 2; dx++) {
			for (int dz = -2; dz <= 2; dz++) provider.provideChunk(cx + dx, cz + dz);
		}
		for (int dx = -1; dx <= 1; dx++) {
			for (int dz = -1; dz <= 1; dz++) {
				provider.provideChunk(cx + dx, cz + dz).populateChunk(provider, provider.chunkGenerator);
			}
		}
		return provider.getLoadedChunk(cx, cz);
	}

	private static boolean natural(IBlockState state) {
		if (state.getBlock() != Blocks.STONE) return false;
		int meta = Blocks.STONE.getMetaFromState(state);
		return meta == 0 || meta == 1 || meta == 3 || meta == 5;
	}

	private void control() throws Exception {
		if (RealisticDepositsRuntime.state().definitions().stream().anyMatch(def -> def.isDiamond() && def.enabled())) {
			throw new IllegalStateException("Vanilla controls must disable both diamond definitions before startup");
		}
		int base = 8192;
		int side = 12;
		net.minecraft.world.gen.ChunkProviderServer provider = world.getChunkProvider();
		for (int cx = base - 2; cx <= base + side + 1; cx++) {
			for (int cz = base - 2; cz <= base + side + 1; cz++) provider.provideChunk(cx, cz);
		}
		for (int cx = base - 1; cx <= base + side; cx++) {
			for (int cz = base - 1; cz <= base + side; cz++) {
				provider.provideChunk(cx, cz).populateChunk(provider, provider.chunkGenerator);
			}
		}
		int diamonds = 0;
		BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
		for (int cx = base; cx < base + side; cx++) {
			for (int cz = base; cz < base + side; cz++) {
				Chunk chunk = provider.getLoadedChunk(cx, cz);
				for (int x = 0; x < 16; x++) {
					for (int z = 0; z < 16; z++) {
						for (int y = 0; y < 128; y++) {
							if (chunk.getBlockState(pos.setPos(cx * 16 + x, y, cz * 16 + z)).getBlock() == Blocks.DIAMOND_ORE) diamonds++;
						}
					}
				}
			}
		}
		JsonObject report = new JsonObject();
		report.addProperty("seed", world.getSeed());
		report.addProperty("chunks", side * side);
		report.addProperty("diamond_ore", diamonds);
		try (FileWriter output = new FileWriter(new File("diamond-survey.json"))) {
			new GsonBuilder().setPrettyPrinting().create().toJson(report, output);
		}
		System.out.println("RD_DIAMOND_SURVEY_PASS");
	}
}
