package zone.moddev.mc.realisticdeposits.generation;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import com.google.gson.JsonObject;
import com.mojang.serialization.Codec;
import org.apache.logging.log4j.LogManager;

import net.minecraft.block.material.Material;
import net.minecraft.block.state.IBlockState;
import net.minecraft.init.Blocks;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.WorldServer;
import net.minecraft.world.biome.Biome;
import net.minecraft.world.chunk.Chunk;
import net.minecraftforge.common.BiomeDictionary;
import net.minecraftforge.fml.common.FMLCommonHandler;

import zone.moddev.mc.orespawn.api.GeologyColumn;
import zone.moddev.mc.orespawn.api.GeologySampler;
import zone.moddev.mc.orespawn.api.OreGenerationContext;
import zone.moddev.mc.orespawn.api.OrePatternType;
import zone.moddev.mc.orespawn.api.OreSpawnApi;
import zone.moddev.mc.realisticdeposits.config.DepositCatalogLoader;
import zone.moddev.mc.realisticdeposits.config.RealisticDepositsRuntime;
import zone.moddev.mc.realisticdeposits.geometry.BlockBounds;
import zone.moddev.mc.realisticdeposits.geometry.GoldDistrict;
import zone.moddev.mc.realisticdeposits.model.DepositDefinition;
import zone.moddev.mc.realisticdeposits.model.AlignmentMode;

/** OreSpawn owns the pass; these patterns only place their current chunk's slice. */
public final class GoldPattern {
	private static int epoch;
	private static boolean warnedTerrain;
	private static boolean warnedGeology;

	private GoldPattern() { }

	public static OrePatternType type(boolean placer) {
		return OrePatternType.create(Codec.of(input -> {
			if (input == null || !input.isJsonObject()) {
				throw new IllegalArgumentException("Gold settings need a captured definition");
			}
			JsonObject json = input.getAsJsonObject();
			DepositDefinition definition = DepositCatalogLoader.loadDefinition(
					json.getAsJsonObject("definition"), "OreSpawn gold profile");
			if (placer != definition.isGoldPlacer() || !definition.isGold()) {
				throw new IllegalArgumentException("Gold pattern and shape do not match");
			}
			DepositDefinition source = placer ? DepositCatalogLoader.loadDefinition(
					json.getAsJsonObject("source_definition"), "OreSpawn placer source") : definition;
			if (!source.isGoldLode() || (placer && !source.id().equals(definition.goldPlacer().sourceDeposit()))) {
				throw new IllegalArgumentException("The placer needs its captured gold-lode source");
			}
			return new Captured(enabledForCallback(definition), enabledForCallback(source));
		}), captured -> {
			Renderer renderer = new Renderer(captured);
			return base -> {
				if (!(base instanceof OreGenerationContext)) {
					throw new IllegalStateException("Gold requires OreSpawn's extended generation context");
				}
				return renderer.place((OreGenerationContext) base);
			};
		});
	}

	public static void newServerSession() {
		epoch++;
		warnedTerrain = false;
		warnedGeology = false;
	}

	static DepositDefinition enabledForCallback(DepositDefinition definition) {
		// OreSpawn calls the pattern only for an enabled rule. Its saved switch
		// takes precedence over the catalogue default captured inside the settings.
		if (definition.enabled()) return definition;
		JsonObject json = definition.toJson();
		json.addProperty("enabled", true);
		return DepositCatalogLoader.loadDefinition(json, "enabled world gold rule");
	}

	private static final class Captured {
		final DepositDefinition definition;
		final DepositDefinition source;

		Captured(DepositDefinition definition, DepositDefinition source) {
			this.definition = definition;
			this.source = source;
		}
	}

	private static final class Renderer {
		private final Captured captured;
		private final Map<Long, Optional<GoldDistrict>> districts =
				new LinkedHashMap<Long, Optional<GoldDistrict>>() {
			@Override
			protected boolean removeEldestEntry(Map.Entry<Long, Optional<GoldDistrict>> entry) {
				return size() > 128;
			}
		};
		private int observedEpoch = -1;
		private long observedSeed;
		private DepositDefinition source;
		private Set<IBlockState> hosts;

		Renderer(Captured captured) {
			this.captured = captured;
		}

		boolean place(OreGenerationContext context) {
			DepositDefinition definition = captured.definition;
			if (!definition.enabled() || !definition.dimensions().contains(context.dimension().toString())
					|| !"minecraft:overworld".equals(context.dimension().toString())) return false;

			MinecraftServer server = FMLCommonHandler.instance().getMinecraftServerInstance();
			WorldServer world = server == null ? null : server.worldServerForDimension(0);
			if (world == null) throw new IllegalStateException("Gold placement has no active Overworld");
			Chunk chunk = world.getChunkProvider().getLoadedChunk(context.chunkX(), context.chunkZ());
			if (chunk == null) throw new IllegalStateException("Gold placement may only inspect its loaded chunk");

			if (observedEpoch != epoch || observedSeed != context.worldSeed()) {
				districts.clear();
				observedEpoch = epoch;
				observedSeed = context.worldSeed();
				source = definition.isGoldPlacer() ? activeSource(server, captured.source) : captured.source;
				hosts = GoldGeology.hosts(server);
			}
			if (source == null || !source.enabled()) return false;

			List<int[]> structures = CoalStructureProtection.inChunk(context.chunkX(), context.chunkZ());
			int count = definition.isGoldLode() ? lodes(context, world, chunk, structures)
					: placer(context, world, chunk, structures);
			if (count > 0) RealisticDepositsRuntime.reportFirstGeneration(definition.id(),
					context.dimension().toString(), context.chunkX(), context.chunkZ(), count);
			return count > 0;
		}

		private Optional<GoldDistrict> district(OreGenerationContext context, WorldServer world, int rx, int rz) {
			long key = ((long) rx << 32) ^ (rz & 0xffffffffL);
			Optional<GoldDistrict> result = districts.get(key);
			if (result != null) return result;

			MineTerrainSampler terrain;
			try {
				terrain = new MineTerrainSampler(world, world.getChunkProvider().chunkGenerator);
			} catch (IllegalArgumentException unsupported) {
				if (!warnedTerrain) {
					warnedTerrain = true;
					LogManager.getLogger("Realistic Deposits").warn("Gold deposits cannot safely sample this terrain generator. "
							+ "No neighbouring chunks will be generated to guess a site. "
							+ "Disable the lode rule in OreSpawn if you want to keep the ordinary gold supply.");
				}
				result = Optional.empty();
				districts.put(key, result);
				return result;
			}

			Optional<GeologySampler> sampler = context.geologySampler();
			result = GoldDistrict.plan(source, context.worldSeed(), context.dimension().toString(), rx, rz,
					new GoldDistrict.Site() {
				@Override public int surfaceY(int x, int z) { return terrain.naturalSurfaceY(x, z); }

				@Override
				public String geome(int x, int z, int surface) {
					String id = sampler.isPresent() ? sampler.get().sampleColumn(x, z, surface).geome().toString() : null;
					if ((id == null || "orespawn:cyano".equals(id)) && !warnedGeology) {
						warnedGeology = true;
						LogManager.getLogger("Realistic Deposits").warn("This world has no modern district classification. "
								+ "Gold districts use the configured rarity-only approximation; geology settings are unchanged.");
					}
					return id;
				}
			});
			if (result.isPresent() && CoalGeology.active().physicalStrata) {
				if (!sampler.isPresent() || !productiveHosts(result.get(), sampler.get())) result = Optional.empty();
			}
			districts.put(key, result);
			return result;
		}

		private boolean productiveHosts(GoldDistrict district, GeologySampler sampler) {
			for (int vein = 0; vein < district.veins().size(); vein++) {
				for (int offset = -1; offset <= 1; offset++) {
					zone.moddev.mc.realisticdeposits.geometry.MinePoint point = district.pointOnVein(
							vein, offset * district.veins().get(vein).length() * 0.25, district.centerY());
					GeologyColumn column = sampler.sampleColumn(point.x(), point.z(), district.bounds().maximumY());
					if (hosts.contains(column.rockAt(point.y()))) return true;
				}
			}
			return false;
		}

		private int lodes(OreGenerationContext context, WorldServer world, Chunk chunk, List<int[]> structures) {
			int minX = context.chunkX() << 4;
			int minZ = context.chunkZ() << 4;
			int radius = source.lode().strikeLength().maximum() / 2 + 192;
			int size = source.regionSpacingChunks() * 16;
			int count = 0;
			boolean physical = CoalGeology.active().physicalStrata;
			GeologyColumn[] columns = new GeologyColumn[256];
			BlockPos.MutableBlockPos position = new BlockPos.MutableBlockPos();

			for (int rx = Math.floorDiv(minX - radius, size); rx <= Math.floorDiv(minX + 15 + radius, size); rx++) {
				for (int rz = Math.floorDiv(minZ - radius, size); rz <= Math.floorDiv(minZ + 15 + radius, size); rz++) {
					Optional<GoldDistrict> selected = district(context, world, rx, rz);
					if (!selected.isPresent()) continue;

					GoldDistrict district = selected.get();
					BlockBounds bounds = district.bounds();
					if (source.abandonedMine().enabled()) MineWorkingsGenerator.offer(context, source, district);
					if (!bounds.intersectsChunk(context.chunkX(), context.chunkZ())) continue;

					for (int x = Math.max(minX, bounds.minimumX()); x <= Math.min(minX + 15, bounds.maximumX()); x++) {
						for (int z = Math.max(minZ, bounds.minimumZ()); z <= Math.min(minZ + 15, bounds.maximumZ()); z++) {
							int index = (x & 15) + (z & 15) * 16;
							int surface = chunk.getHeightValue(x & 15, z & 15);
							int top = Math.min(district.maximumCandidateYAt(x, z), surface - district.surfaceCover());

							for (int y = district.minimumCandidateYAt(x, z); y <= top; y++) {
								if (!district.isOreAt(x, y, z) || !context.inside(x, y, z)
										|| CoalStructureProtection.contains(structures, x, y, z)) continue;

								IBlockState state = chunk.getBlockState(position.setPos(x, y, z));
								if (!hosts.contains(state) || !naturalRock(source, state, physical,
										columns, index, context, x, y, z, surface)) continue;
								if (context.tryPlace(x, y, z, district.identity().seed())) count++;
							}
						}
					}
				}
			}
			return count;
		}

		private int placer(OreGenerationContext context, WorldServer world, Chunk chunk, List<int[]> structures) {
			DepositDefinition definition = captured.definition;
			int minX = context.chunkX() << 4;
			int minZ = context.chunkZ() << 4;
			int reach = definition.goldPlacer().maximumSourceDistance();
			int size = source.regionSpacingChunks() * 16;
			int search = reach + (source.lode().strikeLength().maximum() / 2 + 96);
			java.util.ArrayList<GoldDistrict> nearby = new java.util.ArrayList<>();

			Biome[] biomes = world.provider.getBiomeProvider().getBiomes(null, minX, minZ, 16, 16);
			boolean possibleRiver = false;
			for (Biome biome : biomes) {
				if (BiomeDictionary.isBiomeOfType(biome, BiomeDictionary.Type.RIVER)) {
					possibleRiver = true;
					break;
				}
			}
			if (!possibleRiver) return 0;

			for (int rx = Math.floorDiv(minX - search, size); rx <= Math.floorDiv(minX + 15 + search, size); rx++) {
				for (int rz = Math.floorDiv(minZ - search, size); rz <= Math.floorDiv(minZ + 15 + search, size); rz++) {
					Optional<GoldDistrict> candidate = district(context, world, rx, rz);
					if (candidate.isPresent()) nearby.add(candidate.get());
				}
			}
			if (nearby.isEmpty()) return 0;

			int count = 0;
			BlockPos.MutableBlockPos position = new BlockPos.MutableBlockPos();
			for (int x = minX; x < minX + 16; x++) {
				for (int z = minZ; z < minZ + 16; z++) {
					Biome biome = biomes[(x & 15) + (z & 15) * 16];
					if (!BiomeDictionary.isBiomeOfType(biome, BiomeDictionary.Type.RIVER)) continue;
					GoldDistrict nearest = null;
					double distance = (double) reach * reach;
					for (GoldDistrict candidate : nearby) {
						double current = candidate.distanceSquared(x, z);
						if (current < distance) { nearest = candidate; distance = current; }
					}
					if (nearest == null) continue;

					int y = Math.min(255, chunk.getHeightValue(x & 15, z & 15) - 1);
					IBlockState state = chunk.getBlockState(position.setPos(x, y, z));
					if (state.getMaterial() != Material.WATER) continue;
					while (y > definition.minimumY() && state.getMaterial() == Material.WATER) {
						state = chunk.getBlockState(position.setPos(x, --y, z));
					}
					int thickness = definition.goldPlacer().thickness().minimum()
							+ (int) Math.floorMod(nearest.identity().seed() ^ x ^ ((long) z << 32),
							definition.goldPlacer().thickness().maximum() - definition.goldPlacer().thickness().minimum() + 1);
					for (int depth = 0; depth < thickness && y - depth >= definition.minimumY(); depth++) {
						int py = y - depth;
						if (py > definition.maximumY()) continue;
						state = chunk.getBlockState(position.setPos(x, py, z));
						if (state.getBlock() != Blocks.GRAVEL && state.getBlock() != Blocks.SAND) break;
						IBlockState below = chunk.getBlockState(position.setPos(x, py - 1, z));
						if (!below.getMaterial().isSolid() || CoalStructureProtection.contains(structures, x, py, z)) continue;
						if (GoldDistrict.placerPocket(nearest.identity().seed() ^ definition.salt(), x, z,
								depth, definition.goldPlacer().coverage()) && context.inside(x, py, z)
								&& context.tryPlace(x, py, z, nearest.identity().seed())) count++;
					}
				}
			}
			return count;
		}
	}

	private static boolean naturalRock(DepositDefinition definition, IBlockState state, boolean physical, GeologyColumn[] columns,
			int index, OreGenerationContext context, int x, int y, int z, int surface) {
		if (state.getMaterial() != Material.ROCK || state.getBlock().hasTileEntity(state)) return false;
		if (physical && context.geologySampler().isPresent()) {
			if (columns[index] == null) columns[index] = context.geologySampler().get().sampleColumn(x, z, surface);
			if (!columns[index].familyAt(y).isPresent() || !state.equals(columns[index].rockAt(y))) return false;
			return definition.alignment() != AlignmentMode.ROCK_ALIGNED
					|| definition.rockFamilies().contains(columns[index].familyAt(y).get());
		}
		if (state.getBlock() != Blocks.STONE) return false;
		int meta = Blocks.STONE.getMetaFromState(state);
		return meta == 0 || meta == 1 || meta == 3 || meta == 5;
	}

	private static DepositDefinition activeSource(MinecraftServer server, DepositDefinition fallback) {
		if (fallback == null) return null;
		Optional<zone.moddev.mc.orespawn.api.GeologyProfileView> profile = OreSpawnApi.getActiveProfile(server);
		if (!profile.isPresent()) return null;
		JsonObject root = profile.get().toJson();
		if (!root.has("ores")) return null;
		JsonObject ores = root.getAsJsonObject("ores");
		String key = "realisticdeposits:deposit/" + fallback.id().replace(':', '/');
		if (!ores.has(key)) return null;
		JsonObject rule = ores.getAsJsonObject(key);
		if (rule.has("enabled") && !rule.get("enabled").getAsBoolean()) return null;
		JsonObject dimensions = rule.getAsJsonObject("dimensions");
		if (dimensions == null || !dimensions.has("minecraft:overworld")) return null;
		JsonObject dimension = dimensions.getAsJsonObject("minecraft:overworld");
		if (dimension.has("enabled") && !dimension.get("enabled").getAsBoolean()) return null;
		if (!dimension.has("pattern") || !dimension.get("pattern").isJsonObject()) return null;
		JsonObject pattern = dimension.getAsJsonObject("pattern");
		if (!"realisticdeposits:gold_lode".equals(pattern.get("type").getAsString())) return null;
		DepositDefinition source = DepositCatalogLoader.loadDefinition(pattern.getAsJsonObject("settings")
				.getAsJsonObject("definition"), "world-captured gold source");
		return source.isGoldLode() ? enabledForCallback(source) : null;
	}

	public static String preview(DepositDefinition definition, WorldServer world, int rx, int rz) {
		DepositDefinition source = definition.isGoldLode() ? definition
				: activeSource(world.getMinecraftServer(), RealisticDepositsRuntime.state()
						.definition(definition.goldPlacer().sourceDeposit()));
		if (source == null) return "No active captured lode source is available for this placer.";
		MineTerrainSampler terrain;
		try {
			terrain = new MineTerrainSampler(world, world.getChunkProvider().chunkGenerator);
		} catch (IllegalArgumentException unsupported) {
			return "This terrain generator does not support safe gold-site previews.";
		}
		Optional<GeologySampler> sampler = OreSpawnApi.createSampler(world);
		Optional<GoldDistrict> district = GoldDistrict.plan(source, world.getSeed(), "minecraft:overworld", rx, rz,
				new GoldDistrict.Site() {
			@Override public int surfaceY(int x, int z) { return terrain.naturalSurfaceY(x, z); }
			@Override public String geome(int x, int z, int surface) {
				return sampler.isPresent() ? sampler.get().sampleColumn(x, z, surface).geome().toString() : null;
			}
		});
		return district.isPresent() ? "Gold district at " + district.get().centerX() + ","
				+ district.get().centerZ() + "; veins=" + district.get().veins().size()
				+ ". River placers also require suitable submerged sediment nearby. Preview changes no blocks."
				: "No productive gold district is selected in this region.";
	}
}
