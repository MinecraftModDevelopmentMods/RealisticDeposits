package zone.moddev.mc.realisticdeposits.generation;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import com.google.gson.JsonObject;
import com.mojang.serialization.Codec;

import net.minecraft.block.state.IBlockState;

import zone.moddev.mc.orespawn.api.CompiledOrePattern;
import zone.moddev.mc.orespawn.api.GeologyColumn;
import zone.moddev.mc.orespawn.api.GeologyFamily;
import zone.moddev.mc.orespawn.api.GeologySampler;
import zone.moddev.mc.orespawn.api.OreGenerationContext;
import zone.moddev.mc.orespawn.api.OrePatternType;
import zone.moddev.mc.orespawn.api.OrePlacementContext;
import zone.moddev.mc.realisticdeposits.config.DepositCatalogLoader;
import zone.moddev.mc.realisticdeposits.config.RealisticDepositsRuntime;
import zone.moddev.mc.realisticdeposits.geometry.CoalMeasuresBody;
import zone.moddev.mc.realisticdeposits.model.AlignmentMode;
import zone.moddev.mc.realisticdeposits.model.DepositDefinition;

/** Places the current chunk's beds through OreSpawn's exact-host safety checks. */
public final class CoalMeasuresPattern {
	private CoalMeasuresPattern() { }

	public static OrePatternType type() {
		return OrePatternType.create(Codec.of(input -> {
			if (input == null || !input.isJsonObject()) {
				throw new IllegalArgumentException("Coal measures need a captured deposit definition");
			}
			JsonObject settings = input.getAsJsonObject();
			if (!settings.has("definition") || !settings.get("definition").isJsonObject()) {
				throw new IllegalArgumentException("Coal measures need a definition object");
			}
			DepositDefinition definition = DepositCatalogLoader.loadDefinition(
					settings.getAsJsonObject("definition"), "OreSpawn coal profile");
			if (!definition.isCoal()) throw new IllegalArgumentException("Expected a coal_measures shape");
			return definition;
		}), CoalMeasuresPattern::compile);
	}

	private static CompiledOrePattern compile(DepositDefinition captured) {
		RealisticDepositsRuntime.GenerationDefinitionHandle handle = RealisticDepositsRuntime.generationHandle(captured);
		Renderer renderer = new Renderer();
		return context -> renderer.place(handle.current(), context, CoalGeology.active());
	}

	static boolean place(DepositDefinition definition, OreGenerationContext context, CoalGeology.Snapshot profile) {
		return new Renderer().place(definition, context, profile);
	}

	public static void newServerSession() { CoalGeology.reset(); }

	private static final class Renderer {
		private final Map<Long, Optional<CoalMeasuresBody>> bodies = new LinkedHashMap<Long, Optional<CoalMeasuresBody>>() {
			@Override
			protected boolean removeEldestEntry(Map.Entry<Long, Optional<CoalMeasuresBody>> eldest) {
				return size() > 64;
			}
		};
		private DepositDefinition observed;
		private Set<IBlockState> hosts;
		private long seed;
		private int epoch = -1;

		boolean place(DepositDefinition definition, OrePlacementContext base, CoalGeology.Snapshot profile) {
			if (!(base instanceof OreGenerationContext)) {
				throw new IllegalStateException("Coalfields require OreSpawn's extended generation context");
			}
			OreGenerationContext context = (OreGenerationContext) base;
			if (!definition.enabled() || !definition.dimensions().contains(context.dimension().toString())) return false;

			Optional<GeologySampler> sampler = context.geologySampler();
			if (!sampler.isPresent() && definition.alignment() != AlignmentMode.MASSIVE_RARE) {
				RealisticDepositsRuntime.warnMissingSampler(definition.id());
				return false;
			}
			if (definition != observed || seed != context.worldSeed() || epoch != CoalGeology.epoch()) {
				bodies.clear();
				hosts = CoalGeology.hosts(definition);
				observed = definition;
				seed = context.worldSeed();
				epoch = CoalGeology.epoch();
			}

			int chunkX = context.chunkX() << 4;
			int chunkZ = context.chunkZ() << 4;
			int regionSize = definition.regionSpacingChunks() * 16;
			int radius = (definition.coal().strikeLength().maximum() + definition.coal().downDipWidth().maximum()) / 2 + 3;
			if (definition.abandonedMine().enabled() && definition.abandonedMine().chance() > 0) radius += 96;
			int count = 0;
			java.util.List<int[]> structures = CoalStructureProtection.inChunk(context.chunkX(), context.chunkZ());
			GeologyColumn[] columns = new GeologyColumn[256];

			for (int rx = Math.floorDiv(chunkX - radius, regionSize); rx <= Math.floorDiv(chunkX + 15 + radius, regionSize); rx++) {
				for (int rz = Math.floorDiv(chunkZ - radius, regionSize); rz <= Math.floorDiv(chunkZ + 15 + radius, regionSize); rz++) {
					long key = ((long) rx << 32) ^ (rz & 0xffffffffL);
					Optional<CoalMeasuresBody> selected = bodies.get(key);
					if (selected == null) {
						selected = CoalMeasuresBody.plan(definition, seed, context.dimension().toString(), rx, rz);
						if (selected.isPresent() && sampler.isPresent()) {
							CoalMeasuresBody body = selected.get();
							if (!CoalGeology.acceptsGeome(definition, body, sampler.get())) {
								selected = Optional.empty();
							} else {
								selected = CoalGeology.align(body, definition, sampler.get(), hosts, profile);
							}
						}
						bodies.put(key, selected);
					}
					if (!selected.isPresent()) continue;
					CoalMeasuresBody body = selected.get();
					MineWorkingsGenerator.offer(context, definition, body);
					if (!body.bounds().intersectsChunk(context.chunkX(), context.chunkZ())) continue;

					for (int x = chunkX; x < chunkX + 16; x++) {
						for (int z = chunkZ; z < chunkZ + 16; z++) {
							int index = (x - chunkX) + (z - chunkZ) * 16;
							for (int y = body.minimumCandidateYAt(x, z); y <= body.maximumCandidateYAt(x, z); y++) {
								if (!body.isOreAt(x, y, z) || !context.inside(x, y, z)) continue;
								if (CoalStructureProtection.contains(structures, x, y, z)) continue;
								if (profile.physicalStrata && sampler.isPresent()) {
									if (columns[index] == null) columns[index] = sampler.get().sampleColumn(x, z, body.centerY());
									GeologyColumn column = columns[index];
									if (column.familyAt(y).orElse(null) != GeologyFamily.SEDIMENTARY
											|| !hosts.contains(column.rockAt(y))) continue;
								}
								if (context.tryPlace(x, y, z)) count++;
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
}
