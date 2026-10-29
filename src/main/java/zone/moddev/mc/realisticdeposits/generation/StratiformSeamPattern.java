package zone.moddev.mc.realisticdeposits.generation;

import java.util.Optional;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mojang.serialization.Codec;

import zone.moddev.mc.orespawn.api.CompiledOrePattern;
import zone.moddev.mc.orespawn.api.GeologyColumn;
import zone.moddev.mc.orespawn.api.GeologyFamily;
import zone.moddev.mc.orespawn.api.GeologySampler;
import zone.moddev.mc.orespawn.api.OreGenerationContext;
import zone.moddev.mc.orespawn.api.OrePatternType;
import zone.moddev.mc.realisticdeposits.config.DepositCatalogLoader;
import zone.moddev.mc.realisticdeposits.config.RealisticDepositsRuntime;
import zone.moddev.mc.realisticdeposits.geometry.BlockBounds;
import zone.moddev.mc.realisticdeposits.geometry.RegionDepositPlanner;
import zone.moddev.mc.realisticdeposits.geometry.StratiformSeamBody;
import zone.moddev.mc.realisticdeposits.model.AlignmentMode;
import zone.moddev.mc.realisticdeposits.model.DepositDefinition;

/** OreSpawn custom pattern that renders only the current-chunk slice of each seam. */
public final class StratiformSeamPattern {
	private StratiformSeamPattern() { }

	public static OrePatternType type() {
		return OrePatternType.create(Codec.of(StratiformSeamPattern::decode),
				StratiformSeamPattern::compile);
	}

	private static DepositDefinition decode(JsonElement input) {
		if (input == null || !input.isJsonObject()) {
			throw new IllegalArgumentException("stratiform_seam settings must be an object");
		}
		JsonObject root = input.getAsJsonObject();
		JsonElement definition = root.get("definition");
		if (definition == null || !definition.isJsonObject()) {
			throw new IllegalArgumentException("stratiform_seam settings require definition");
		}
		return DepositCatalogLoader.loadDefinition(definition.getAsJsonObject(),
				"OreSpawn stratiform_seam profile");
	}

	private static CompiledOrePattern compile(DepositDefinition captured) {
		RealisticDepositsRuntime.GenerationDefinitionHandle handle =
				RealisticDepositsRuntime.generationHandle(captured);
		return context -> placeResolved(handle.current(), context);
	}

	static boolean place(DepositDefinition captured, zone.moddev.mc.orespawn.api.OrePlacementContext base) {
		return placeResolved(RealisticDepositsRuntime.definitionForGeneration(captured), base);
	}

	private static boolean placeResolved(DepositDefinition definition,
			zone.moddev.mc.orespawn.api.OrePlacementContext base) {
		if (!(base instanceof OreGenerationContext)) {
			throw new IllegalStateException("Realistic Deposits requires OreSpawn 4.1 OreGenerationContext");
		}
		OreGenerationContext context = (OreGenerationContext) base;
		if (!definition.enabled() || !definition.dimensions().contains(context.dimension().toString())) {
			return false;
		}
		Optional<GeologySampler> sampler = context.geologySampler();
		if (definition.alignment() != AlignmentMode.MASSIVE_RARE && !sampler.isPresent()) {
			RealisticDepositsRuntime.warnMissingSampler(definition.id());
			return false;
		}
		int chunkMinX = context.chunkX() << 4;
		int chunkMinZ = context.chunkZ() << 4;
		int chunkMaxX = chunkMinX + 15;
		int chunkMaxZ = chunkMinZ + 15;
		int regionBlocks = definition.regionSpacingChunks() * 16;
		int radius = maximumHorizontalRadius(definition);
		int minRegionX = Math.floorDiv(chunkMinX - radius, regionBlocks);
		int maxRegionX = Math.floorDiv(chunkMaxX + radius, regionBlocks);
		int minRegionZ = Math.floorDiv(chunkMinZ - radius, regionBlocks);
		int maxRegionZ = Math.floorDiv(chunkMaxZ + radius, regionBlocks);
		GeologyColumn[] columns = definition.alignment() == AlignmentMode.ROCK_ALIGNED
				? new GeologyColumn[256] : null;
		int placedBlocks = 0;
		for (int regionX = minRegionX; regionX <= maxRegionX; regionX++) {
			for (int regionZ = minRegionZ; regionZ <= maxRegionZ; regionZ++) {
				Optional<StratiformSeamBody> planned = RegionDepositPlanner.plan(definition,
						context.worldSeed(), context.dimension().toString(), regionX, regionZ);
				if (!planned.isPresent()) continue;
				StratiformSeamBody body = planned.get();
				if (!body.bounds().intersectsChunk(context.chunkX(), context.chunkZ())
						|| !acceptsCenter(definition, body, sampler)) continue;
				MineWorkingsGenerator.offer(context, definition, body);
				BlockBounds bounds = body.bounds();
				for (int x = Math.max(chunkMinX, bounds.minimumX());
						x <= Math.min(chunkMaxX, bounds.maximumX()); x++) {
					for (int z = Math.max(chunkMinZ, bounds.minimumZ());
							z <= Math.min(chunkMaxZ, bounds.maximumZ()); z++) {
						int minimumY = body.minimumCandidateYAt(x, z);
						int maximumY = body.maximumCandidateYAt(x, z);
						for (int y = minimumY; y <= maximumY; y++) {
							if (!body.isOreAt(x, y, z) || !context.inside(x, y, z)) continue;
							if (definition.alignment() == AlignmentMode.ROCK_ALIGNED
									&& !acceptsFamily(definition, sampler.get(), columns,
											x, y, z, chunkMinX, chunkMinZ, body.centerY())) continue;
							if (context.tryPlace(x, y, z)) placedBlocks++;
						}
					}
				}
			}
		}
		if (placedBlocks > 0) {
			RealisticDepositsRuntime.reportFirstGeneration(definition.id(),
					context.dimension().toString(), context.chunkX(), context.chunkZ(), placedBlocks);
		}
		return placedBlocks > 0;
	}

	private static boolean acceptsCenter(DepositDefinition definition, StratiformSeamBody body,
			Optional<GeologySampler> sampler) {
		if (definition.alignment() == AlignmentMode.MASSIVE_RARE) return true;
		GeologyColumn center = sampler.get().sampleColumn(body.centerX(), body.centerZ(), body.centerY());
		return definition.geomes().contains(center.geome().toString());
	}

	private static boolean acceptsFamily(DepositDefinition definition, GeologySampler sampler,
			GeologyColumn[] columns, int x, int y, int z, int chunkMinX, int chunkMinZ,
			int surfaceY) {
		int index = (x - chunkMinX) + ((z - chunkMinZ) << 4);
		GeologyColumn column = columns[index];
		if (column == null) {
			column = sampler.sampleColumn(x, z, surfaceY);
			columns[index] = column;
		}
		Optional<GeologyFamily> family = column.familyAt(y);
		return family.isPresent() && definition.rockFamilies().contains(family.get());
	}

	private static int maximumHorizontalRadius(DepositDefinition definition) {
		return (int) Math.ceil(definition.seam().strikeLength().maximum() * 0.5D
				+ definition.seam().downDipWidth().maximum() * 0.5D
				+ definition.seam().bendAmplitude() + 13.0D);
	}
}
