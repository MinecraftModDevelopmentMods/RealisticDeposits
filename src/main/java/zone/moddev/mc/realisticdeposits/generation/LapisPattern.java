package zone.moddev.mc.realisticdeposits.generation;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import com.google.gson.JsonObject;
import com.mojang.serialization.Codec;
import org.apache.logging.log4j.LogManager;

import net.minecraft.block.state.IBlockState;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.WorldServer;
import net.minecraft.world.chunk.Chunk;
import net.minecraftforge.fml.common.FMLCommonHandler;

import zone.moddev.mc.orespawn.api.GeologyColumn;
import zone.moddev.mc.orespawn.api.GeologySampler;
import zone.moddev.mc.orespawn.api.OreGenerationContext;
import zone.moddev.mc.orespawn.api.OrePatternType;
import zone.moddev.mc.orespawn.api.OreSpawnApi;
import zone.moddev.mc.realisticdeposits.config.DepositCatalogLoader;
import zone.moddev.mc.realisticdeposits.config.RealisticDepositsRuntime;
import zone.moddev.mc.realisticdeposits.geometry.BlockBounds;
import zone.moddev.mc.realisticdeposits.geometry.LapisDistrict;
import zone.moddev.mc.realisticdeposits.model.DepositDefinition;
import zone.moddev.mc.realisticdeposits.model.AlignmentMode;

/** Fits lapis to captured geology and writes only the current OreSpawn chunk. */
public final class LapisPattern {
	private static int epoch;
	private static boolean warnedTerrain;
	private static boolean warnedGeology;
	private static boolean warnedMarble;

	private LapisPattern() { }

	public static OrePatternType type() {
		return OrePatternType.create(Codec.of(input -> {
			if (input == null || !input.isJsonObject()) {
				throw new IllegalArgumentException("Lapis settings need a captured definition");
			}

			DepositDefinition definition = DepositCatalogLoader.loadDefinition(
					input.getAsJsonObject().getAsJsonObject("definition"), "OreSpawn lapis profile");
			if (!definition.isLapis()) throw new IllegalArgumentException("Lapis pattern and shape do not match");

			// The enclosing saved rule has already passed OreSpawn's enabled check.
			JsonObject enabled = definition.toJson();
			enabled.addProperty("enabled", true);

			return DepositCatalogLoader.loadDefinition(enabled, "enabled world lapis rule");
		}), definition -> {
			Renderer renderer = new Renderer(definition);

			return base -> {
				if (!(base instanceof OreGenerationContext)) {
					throw new IllegalStateException("Lapis requires OreSpawn's extended generation context");
				}

				return renderer.place((OreGenerationContext) base);
			};
		});
	}

	public static void newServerSession() {
		epoch++;
		warnedTerrain = false;
		warnedGeology = false;
		warnedMarble = false;
	}

	static LapisDistrict.Site site(DepositDefinition definition, MineTerrainSampler terrain,
			Optional<GeologySampler> sampler, Set<IBlockState> hosts, boolean physical) {
		return new LapisDistrict.Site() {
			@Override public boolean hasPhysicalStrata() { return physical; }

			@Override
			public int surfaceY(int x, int z) {
				return terrain.naturalSurfaceY(x, z);
			}

			@Override
			public String geome(int x, int z, int surfaceY) {
				String id = sampler.isPresent()
						? sampler.get().sampleColumn(x, z, surfaceY).geome().toString() : null;

				if ((id == null || "orespawn:cyano".equals(id)) && !warnedGeology) {
					warnedGeology = true;
					LogManager.getLogger("Realistic Deposits").warn("This world has no modern district classification. "
							+ "Lapis uses the configured rarity-only approximation; geology settings are unchanged.");
				}

				return id;
			}

			@Override
			public double fitHeight(int x, int z, double targetY, int thickness) {
				int surface = surfaceY(x, z);
				if (surface < 0) return Double.NaN;

				int maximum = Math.min(definition.maximumY(), surface - definition.lapis().surfaceCover().maximum());
				if (!physical) return targetY;
				if (!sampler.isPresent() || hosts.isEmpty()) return Double.NaN;

				return LapisGeology.fitHeight(sampler.get().sampleColumn(x, z, surface), targetY,
						thickness, definition.minimumY(), maximum, hosts);
			}
		};
	}

	private static final class Renderer {
		private final DepositDefinition definition;
		private final Map<Long, Optional<LapisDistrict>> districts =
				new LinkedHashMap<Long, Optional<LapisDistrict>>() {
			@Override
			protected boolean removeEldestEntry(Map.Entry<Long, Optional<LapisDistrict>> entry) {
				return size() > 128;
			}
		};
		private int observedEpoch = -1;
		private long observedSeed;
		private boolean physical;
		private Set<IBlockState> hosts;
		private boolean supportedTerrain;

		Renderer(DepositDefinition definition) {
			this.definition = definition;
		}

		boolean place(OreGenerationContext context) {
			if (!definition.dimensions().contains(context.dimension().toString())
					|| !"minecraft:overworld".equals(context.dimension().toString())) return false;

			MinecraftServer server = FMLCommonHandler.instance().getMinecraftServerInstance();
			WorldServer world = server == null ? null : server.worldServerForDimension(0);
			if (world == null) throw new IllegalStateException("Lapis placement has no active Overworld");

			Chunk chunk = world.getChunkProvider().getLoadedChunk(context.chunkX(), context.chunkZ());
			if (chunk == null) throw new IllegalStateException("Lapis may only inspect its loaded chunk");

			if (observedEpoch != epoch || observedSeed != context.worldSeed()) {
				districts.clear();
				observedEpoch = epoch;
				observedSeed = context.worldSeed();
				JsonObject profile = OreSpawnApi.getActiveProfile(server).get().toJson();
				physical = LapisGeology.physicalStrata(profile);
				hosts = LapisGeology.hosts(definition, profile, physical);

				try {
					new MineTerrainSampler(world, world.getChunkProvider().chunkGenerator);
					supportedTerrain = true;
				} catch (IllegalArgumentException unsupported) {
					supportedTerrain = false;
					if (!warnedTerrain) {
						warnedTerrain = true;
						LogManager.getLogger("Realistic Deposits").warn("Lapis cannot safely sample this terrain generator. "
								+ "No neighbouring chunks will be generated to guess a site. "
								+ "Disable the lapis rule in OreSpawn to retain the ordinary supply.");
					}
				}

				if (physical && hosts.isEmpty() && !warnedMarble) {
					warnedMarble = true;
					LogManager.getLogger("Realistic Deposits").warn("Lapis lenses need enabled natural marble in this "
							+ "world's strata. No suitable host is available; existing geology settings are unchanged.");
				}
			}

			if (!supportedTerrain || hosts.isEmpty()) return false;

			int minX = context.chunkX() << 4;
			int minZ = context.chunkZ() << 4;
			int radius = definition.lapis().strikeLength().maximum() / 2 + 96;
			int size = definition.regionSpacingChunks() * 16;
			int count = 0;
			List<int[]> structures = CoalStructureProtection.inChunk(context.chunkX(), context.chunkZ());
			GeologyColumn[] columns = new GeologyColumn[256];
			BlockPos.MutableBlockPos position = new BlockPos.MutableBlockPos();

			for (int rx = Math.floorDiv(minX - radius, size); rx <= Math.floorDiv(minX + 15 + radius, size); rx++) {
				for (int rz = Math.floorDiv(minZ - radius, size); rz <= Math.floorDiv(minZ + 15 + radius, size); rz++) {
					long key = ((long) rx << 32) ^ (rz & 0xffffffffL);
					Optional<LapisDistrict> selected = districts.computeIfAbsent(key, ignored -> {
						// Discard sampled terrain after fitting this district. Exploration must
						// not leave every visited region's terrain cached for the whole session.
						MineTerrainSampler terrain = new MineTerrainSampler(world, world.getChunkProvider().chunkGenerator);
						LapisDistrict.Site candidateSite = site(definition, terrain, context.geologySampler(), hosts, physical);

						return LapisDistrict.plan(definition, context.worldSeed(), context.dimension().toString(),
								(int) (key >> 32), (int) key, candidateSite);
					});
					if (!selected.isPresent()) continue;

					LapisDistrict district = selected.get();
					if (definition.abandonedMine().enabled()) MineWorkingsGenerator.offer(context, definition, district);
					BlockBounds bounds = district.bounds();
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
								if (!hosts.contains(state)) continue;

								if (physical || definition.alignment() == AlignmentMode.ROCK_ALIGNED) {
									if (!context.geologySampler().isPresent()) continue;
									if (columns[index] == null) columns[index] = context.geologySampler().get().sampleColumn(x, z, surface);
									if (physical && !state.equals(columns[index].rockAt(y))) continue;
									if (definition.alignment() == AlignmentMode.ROCK_ALIGNED
											&& !definition.rockFamilies().contains(columns[index].familyAt(y).orElse(null))) continue;
								}

								if (context.tryPlace(x, y, z, district.identity().seed())) count++;
							}
						}
					}
				}
			}

			if (count > 0) RealisticDepositsRuntime.reportFirstGeneration(definition.id(),
					context.dimension().toString(), context.chunkX(), context.chunkZ(), count);

			return count > 0;
		}
	}

	public static String preview(DepositDefinition definition, WorldServer world, int rx, int rz) {
		if (world.provider.getDimension() != 0) {
			return "Lapis lens previews are available in the Overworld only.";
		}

		try {
			JsonObject profile = OreSpawnApi.getActiveProfile(world.getMinecraftServer()).get().toJson();
			boolean physical = LapisGeology.physicalStrata(profile);
			LapisDistrict.Site site = site(definition, new MineTerrainSampler(world, world.getChunkProvider().chunkGenerator),
					OreSpawnApi.createSampler(world), LapisGeology.hosts(definition, profile, physical), physical);
			Optional<LapisDistrict> planned = LapisDistrict.plan(definition, world.getSeed(),
					"minecraft:overworld", rx, rz, site);

			if (!planned.isPresent()) return "No suitable lapis lenses are selected in this region.";

			LapisDistrict district = planned.get();
			return "Lapis lenses near " + district.centerX() + "," + district.centerY() + "," + district.centerZ()
					+ "; lenses=" + district.lenses().size() + (physical ? ", fitted to natural marble."
							: ", using the natural-stone approximation.") + " Preview changes no blocks.";
		} catch (IllegalArgumentException unsupported) {
			return "This terrain generator does not support safe lapis previews.";
		}
	}
}
