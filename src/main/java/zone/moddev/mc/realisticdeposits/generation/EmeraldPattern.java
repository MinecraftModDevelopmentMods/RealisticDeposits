package zone.moddev.mc.realisticdeposits.generation;

import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mojang.serialization.Codec;
import net.minecraft.block.Block;
import net.minecraft.block.state.IBlockState;
import net.minecraft.init.Blocks;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.ResourceLocation;
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
import zone.moddev.mc.realisticdeposits.geometry.EmeraldDistrict;
import zone.moddev.mc.realisticdeposits.geometry.MinePoint;
import zone.moddev.mc.realisticdeposits.model.DepositDefinition;

/** Writes the current chunk's share of a fitted schist or natural-stone deposit. */
public final class EmeraldPattern {
	public static final ResourceLocation SCHIST = new ResourceLocation("mineralogy", "schist");
	private static final String OVERWORLD = "minecraft:overworld";
	private static int epoch;

	private EmeraldPattern() { }

	public static void newServerSession() { epoch++; }

	public static OrePatternType type() {
		return OrePatternType.create(Codec.of(input -> {
			if (input == null || !input.isJsonObject() || !input.getAsJsonObject().has("definition")) {
				throw new IllegalArgumentException("Emerald pockets need a captured definition");
			}
			DepositDefinition captured = DepositCatalogLoader.loadDefinition(
					input.getAsJsonObject().getAsJsonObject("definition"), "OreSpawn emerald profile");
			if (!captured.isEmerald()) throw new IllegalArgumentException("Emerald pattern and shape do not match");
			JsonObject saved = captured.toJson();
			// OreSpawn has already applied the enclosing saved rule's enabled switch.
			saved.addProperty("enabled", true);
			return DepositCatalogLoader.loadDefinition(saved, "enabled world emerald rule");
		}), definition -> {
			Renderer renderer = new Renderer(definition);
			return base -> base instanceof OreGenerationContext && renderer.place((OreGenerationContext) base);
		});
	}

	static boolean permittedRock(String block, int meta, boolean physical) {
		if (physical) return SCHIST.toString().equals(block) && meta == 0;
		return "minecraft:stone".equals(block) && (meta == 0 || meta == 1 || meta == 3 || meta == 5);
	}

	static Set<IBlockState> exactHosts(DepositDefinition definition, JsonObject profile, boolean physical) {
		Set<IBlockState> hosts = new HashSet<>();
		if (!physical) {
			if (definition.hosts().contains("minecraft:stone")) {
				for (int metadata : new int[] {0, 1, 3, 5}) hosts.add(Blocks.STONE.getStateFromMeta(metadata));
			}
			return hosts;
		}
		JsonObject rocks = profile.getAsJsonObject("rocks");
		if (rocks == null || !Block.REGISTRY.containsKey(SCHIST)) return hosts;
		for (Map.Entry<String, JsonElement> entry : rocks.entrySet()) {
			JsonObject rock = entry.getValue().getAsJsonObject();
			if (!rock.has("block") || (rock.has("enabled") && !rock.get("enabled").getAsBoolean())
					|| (rock.has("ore_replaceable") && !rock.get("ore_replaceable").getAsBoolean())) continue;
			if (permittedRock(rock.get("block").getAsString(),
					rock.has("metadata") ? rock.get("metadata").getAsInt() : 0, true)) {
				hosts.add(Block.REGISTRY.getObject(SCHIST).getStateFromMeta(0));
			}
		}
		return hosts;
	}

	static EmeraldDistrict.Site site(DepositDefinition definition, MineTerrainSampler terrain,
			GeologySampler sampler, Set<IBlockState> hosts, boolean physical) {
		return new EmeraldDistrict.Site() {
			private final Map<Long, Integer> heights = new HashMap<>();
			private final Map<Long, GeologyColumn> columns = new HashMap<>();

			@Override
			public boolean acceptsGeome(int x, int z) {
				int height = terrain.naturalSurfaceY(x, z);
				if (height < 0) return false;
				String geome = sampler.sampleColumn(x, z, height).geome().toString();
				return !"orespawn:cyano".equals(geome) && definition.geomes().contains(geome);
			}

			@Override
			public boolean eligible(int x, int y, int z, int cover) {
				long key = MinePoint.chunkKey(x, z);
				int height = heights.computeIfAbsent(key, unused -> terrain.naturalSurfaceY(x, z));
				if (height < 0 || height - y < cover) return false;
				IBlockState predicted = physical
						? columns.computeIfAbsent(key, unused -> sampler.sampleColumn(x, z, height)).rockAt(y)
						: terrain.state(x, y, z);
				return hosts.contains(predicted);
			}
		};
	}

	private static final class Renderer {
		private final DepositDefinition definition;
		private final Map<Long, Optional<EmeraldDistrict>> plans =
				new LinkedHashMap<Long, Optional<EmeraldDistrict>>() {
			@Override
			protected boolean removeEldestEntry(Map.Entry<Long, Optional<EmeraldDistrict>> entry) {
				return size() > 128;
			}
		};
		private int observedEpoch = -1;
		private boolean physical;
		private Set<IBlockState> hosts;

		Renderer(DepositDefinition definition) { this.definition = definition; }

		boolean place(OreGenerationContext context) {
			Optional<GeologySampler> sampler = context.geologySampler();
			if (!OVERWORLD.equals(context.dimension().toString()) || !sampler.isPresent()) return false;
			MinecraftServer server = FMLCommonHandler.instance().getMinecraftServerInstance();
			WorldServer world = server == null ? null : server.worldServerForDimension(0);
			if (world == null) return false;
			Chunk chunk = world.getChunkProvider().getLoadedChunk(context.chunkX(), context.chunkZ());
			if (chunk == null) return false;
			if (observedEpoch != epoch) {
				plans.clear();
				JsonObject profile = OreSpawnApi.getActiveProfile(server).get().toJson();
				physical = LapisGeology.physicalStrata(profile);
				hosts = exactHosts(definition, profile, physical);
				observedEpoch = epoch;
			}
			if (hosts.isEmpty()) return false;

			int minX = context.chunkX() << 4, minZ = context.chunkZ() << 4;
			int size = definition.regionSpacingChunks() * 16;
			int radius = EmeraldDistrict.searchRadius(definition) + (definition.abandonedMine().enabled() ? 96 : 0);
			BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
			List<int[]> structures = null;
			int placed = 0;
			for (int rx = Math.floorDiv(minX - radius, size); rx <= Math.floorDiv(minX + 15 + radius, size); rx++) {
				for (int rz = Math.floorDiv(minZ - radius, size); rz <= Math.floorDiv(minZ + 15 + radius, size); rz++) {
					Optional<EmeraldDistrict> plan = plans.computeIfAbsent(MinePoint.chunkKey(rx, rz), key -> {
						MineTerrainSampler terrain;
						try {
							terrain = new MineTerrainSampler(world, world.getChunkProvider().chunkGenerator);
						} catch (IllegalArgumentException unsupported) {
							return Optional.empty();
						}
						return EmeraldDistrict.plan(definition, context.worldSeed(), OVERWORLD,
								(int) (key >> 32), (int) (long) key, site(definition, terrain, sampler.get(), hosts, physical));
					});
					if (!plan.isPresent()) continue;
					EmeraldDistrict body = plan.get();
					MineWorkingsGenerator.offer(context, definition, body);
					EmeraldVeinTrace.offer(world, MinePoint.chunkKey(context.chunkX(), context.chunkZ()),
							body.traceInChunk(context.chunkX(), context.chunkZ()), hosts, body.surfaceCover());
					if (!body.bounds().intersectsChunk(context.chunkX(), context.chunkZ())) continue;
					if (structures == null) structures = CoalStructureProtection.inChunk(context.chunkX(), context.chunkZ());
					for (MinePoint point : body.oreInChunk(context.chunkX(), context.chunkZ())) {
						int x = point.x(), y = point.y(), z = point.z();
						if (!context.inside(x, y, z) || y > chunk.getHeightValue(x & 15, z & 15) - body.surfaceCover()
								|| CoalStructureProtection.contains(structures, x, y, z)) continue;
						IBlockState state = chunk.getBlockState(cursor.setPos(x, y, z));
						// Physical hosts were predicted per position in the immutable plan; the actual
						// state must still be the very same natural schist, not merely a rock family.
						if (hosts.contains(state) && context.tryPlace(x, y, z, body.identity().seed())) placed++;
					}
				}
			}
			if (placed != 0) RealisticDepositsRuntime.reportFirstGeneration(definition.id(), OVERWORLD,
					context.chunkX(), context.chunkZ(), placed);
			return placed != 0;
		}
	}

	public static Optional<EmeraldDistrict> plan(DepositDefinition definition, WorldServer world, int rx, int rz) {
		if (world.provider.getDimension() != 0) return Optional.empty();
		Optional<GeologySampler> sampler = OreSpawnApi.createSampler(world);
		if (!sampler.isPresent()) return Optional.empty();
		JsonObject profile = OreSpawnApi.getActiveProfile(world.getMinecraftServer()).get().toJson();
		boolean physical = LapisGeology.physicalStrata(profile);
		Set<IBlockState> hosts = exactHosts(definition, profile, physical);
		if (hosts.isEmpty()) return Optional.empty();
		try {
			MineTerrainSampler terrain = new MineTerrainSampler(world, world.getChunkProvider().chunkGenerator);
			return EmeraldDistrict.plan(definition, world.getSeed(), OVERWORLD, rx, rz,
					site(definition, terrain, sampler.get(), hosts, physical));
		} catch (IllegalArgumentException unsupported) {
			return Optional.empty();
		}
	}

	public static String preview(DepositDefinition definition, WorldServer world, int rx, int rz) {
		return plan(definition, world, rx, rz).map(body -> (body.large() ? "Large emerald district near "
				: body.rich() ? "Rich emerald pockets near " : "Emerald pockets near ")
				+ body.centerX() + "," + body.centerY() + "," + body.centerZ()
				+ (body.large() ? "; zones=" + body.zones() : "") + "; pockets="
				+ body.pockets().size() + ", planned ore=" + body.ore().size() + ". Preview changes no blocks.")
				.orElse("No suitable emerald pockets are selected in this region.");
	}
}
