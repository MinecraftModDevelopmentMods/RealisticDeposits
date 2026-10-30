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
	public static final ResourceLocation COAL_PATTERN_ID =
			new ResourceLocation("realisticdeposits", "coal_measures");
	public static final ResourceLocation DIAMOND_PIPE_ID =
			new ResourceLocation("realisticdeposits", "diamond_pipe");
	public static final ResourceLocation DIAMOND_PLACER_ID =
			new ResourceLocation("realisticdeposits", "diamond_placer");

	private OreSpawnIntegration() { }

	public static WorldgenProvider provider(Iterable<DepositDefinition> definitions) {
		java.util.Map<String, DepositDefinition> indexed = new java.util.LinkedHashMap<>();
		for (DepositDefinition definition : definitions) indexed.put(definition.id(), definition);
		validateDiamondLinks(indexed.values());
		WorldgenProvider.Builder provider = WorldgenProvider.builder("realisticdeposits", 1)
				.mergeNewEntriesIntoExistingWorlds(false);
		for (DepositDefinition definition : indexed.values()) add(provider, definition, indexed);
		return provider.build();
	}

	public static void validateDiamondLinks(Iterable<DepositDefinition> definitions) {
		java.util.Map<String, DepositDefinition> indexed = new java.util.LinkedHashMap<>();
		for (DepositDefinition definition : definitions) indexed.put(definition.id(), definition);
		for (DepositDefinition definition : indexed.values()) {
			if (!definition.isDiamondPlacer()) continue;
			DepositDefinition source = indexed.get(definition.placer().sourceDeposit());
			if (source == null || !source.isDiamondPipe()
					|| !source.dimensions().containsAll(definition.dimensions())) {
				throw new IllegalArgumentException("Diamond placer '" + definition.id()
						+ "' needs a pipe source in the same dimensions: " + definition.placer().sourceDeposit());
			}
		}
	}

	private static ResourceLocation patternId(DepositDefinition definition) {
		switch (definition.shape().type()) {
			case "stratiform_seam": return PATTERN_ID;
			case "coal_measures": return COAL_PATTERN_ID;
			case "diamond_pipe": return DIAMOND_PIPE_ID;
			case "diamond_placer": return DIAMOND_PLACER_ID;
			default: throw new IllegalArgumentException("Unsupported shape: " + definition.shape().type());
		}
	}

	public static boolean enqueue(Iterable<DepositDefinition> definitions) {
		return OreSpawnApi.enqueue(provider(definitions));
	}

	private static void add(WorldgenProvider.Builder provider, DepositDefinition definition,
			java.util.Map<String, DepositDefinition> indexed) {
		ResourceLocation output = new ResourceLocation(definition.resource());
		ResourceLocation oreId = new ResourceLocation("realisticdeposits", "deposit/"
				+ new ResourceLocation(definition.id()).getResourceDomain() + "/"
				+ new ResourceLocation(definition.id()).getResourcePath());
		JsonObject patternSettings = new JsonObject();
		patternSettings.add("definition", definition.toJson());
		DepositDefinition source = definition.isDiamondPlacer() ? indexed.get(definition.placer().sourceDeposit()) : null;
		if (source != null) patternSettings.add("source_definition", source.toJson());
		provider.ore(oreId, output, ore -> {
			ore.retrogen(false).suppressVanilla(false);
			if (definition.isCoal() || definition.isDiamond()) {
				ore.enabled(definition.enabled() && (source == null || source.enabled()));
			}
			if (definition.isDiamondPlacer()) {
				// Sediment is not an interchangeable output for diamond-bearing rock.
				ore.material(new ResourceLocation("realisticdeposits", "diamond_gravel"));
			}
			for (String dimensionId : definition.dimensions()) {
				ore.dimension(new ResourceLocation(dimensionId), dimension -> {
					// Keep the enclosing OreSpawn attempt broad: live geometry reload may
					// change the deposit's own height envelope without mutating a saved profile.
					dimension.yRange(0, 255)
							.attempts(1.0D).quantity(1).spread(0, 0).nodeSize(1)
							.pattern(patternId(definition), patternSettings);
					for (String host : definition.hosts()) {
						if (definition.isCoal() || definition.isDiamondPlacer()) {
							ResourceLocation id = new ResourceLocation(host);
							if (net.minecraft.block.Block.REGISTRY.containsKey(id)) dimension.hostState(id, 0);
							if (definition.isDiamondPlacer() && "minecraft:sand".equals(host)) dimension.hostState(id, 1);
						} else if (definition.isDiamondPipe() && "minecraft:stone".equals(host)) {
							for (int metadata : new int[] {0, 1, 3, 5}) {
								dimension.hostState(new ResourceLocation(host), metadata);
							}
						} else {
							dimension.hostBlock(new ResourceLocation(host));
						}
					}
					// Every geology family is a safe outer host. ROCK_ALIGNED applies the
					// current live family selection per output block inside the pattern.
					if (!definition.isCoal() && !definition.isDiamondPlacer()) {
						for (GeologyFamily family : GeologyFamily.values()) dimension.hostFamily(family);
					}
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
