package zone.moddev.mc.realisticdeposits.integration;

import com.google.gson.JsonObject;

import zone.moddev.mc.orespawn.api.GeologyFamily;
import zone.moddev.mc.orespawn.api.OreSpawnApi;
import zone.moddev.mc.orespawn.api.WorldgenProvider;
import zone.moddev.mc.realisticdeposits.model.DepositDefinition;

import net.minecraft.util.ResourceLocation;

/** Builds the immutable OreSpawn provider captured for newly created worlds. */
public final class OreSpawnIntegration {
	public static final ResourceLocation PATTERN_ID =
			new ResourceLocation("realisticdeposits", "stratiform_seam");

	private OreSpawnIntegration() { }

	public static WorldgenProvider provider(Iterable<DepositDefinition> definitions) {
		WorldgenProvider.Builder provider = WorldgenProvider.builder("realisticdeposits", 1)
				.mergeNewEntriesIntoExistingWorlds(false);
		for (DepositDefinition definition : definitions) add(provider, definition);
		return provider.build();
	}

	public static boolean enqueue(Iterable<DepositDefinition> definitions) {
		return OreSpawnApi.enqueue(provider(definitions));
	}

	private static void add(WorldgenProvider.Builder provider, DepositDefinition definition) {
		ResourceLocation output = new ResourceLocation(definition.resource());
		ResourceLocation oreId = new ResourceLocation("realisticdeposits", "deposit/"
				+ new ResourceLocation(definition.id()).getResourceDomain() + "/"
				+ new ResourceLocation(definition.id()).getResourcePath());
		JsonObject patternSettings = new JsonObject();
		patternSettings.add("definition", definition.toJson());
		provider.ore(oreId, output, ore -> {
			ore.retrogen(false).suppressVanilla(false);
			for (String dimensionId : definition.dimensions()) {
				ore.dimension(new ResourceLocation(dimensionId), dimension -> {
					// Keep the enclosing OreSpawn attempt broad: live geometry reload may
					// change the deposit's own height envelope without mutating a saved profile.
					dimension.yRange(0, 255)
							.attempts(1.0D).quantity(1).spread(0, 0).nodeSize(1)
							.pattern(PATTERN_ID, patternSettings);
					for (String host : definition.hosts()) dimension.hostBlock(new ResourceLocation(host));
					// Every geology family is a safe outer host. ROCK_ALIGNED applies the
					// current live family selection per output block inside the pattern.
					for (GeologyFamily family : GeologyFamily.values()) dimension.hostFamily(family);
					for (String biome : definition.biomeIds()) dimension.biome(new ResourceLocation(biome));
					for (String biome : definition.excludedBiomeIds()) {
						dimension.excludeBiome(new ResourceLocation(biome));
					}
					for (String dictionary : definition.biomeDictionary()) {
						dimension.biomeDictionary(dictionary);
					}
					for (String dictionary : definition.excludedBiomeDictionary()) {
						dimension.excludeBiomeDictionary(dictionary);
					}
					if (definition.effectiveBackgroundGenerationScale().isPresent()) {
						dimension.backgroundGenerationScale(
								definition.effectiveBackgroundGenerationScale().getAsDouble());
					}
				});
			}
		});
	}
}
