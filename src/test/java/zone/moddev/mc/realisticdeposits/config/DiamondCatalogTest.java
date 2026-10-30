package zone.moddev.mc.realisticdeposits.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.StringReader;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;
import zone.moddev.mc.realisticdeposits.model.DepositDefinition;
import zone.moddev.mc.realisticdeposits.DiamondFixtures;
import zone.moddev.mc.realisticdeposits.integration.OreSpawnIntegration;

class DiamondCatalogTest {

	static JsonObject pipe() {
		return new JsonParser().parse("{\"id\":\"realisticdeposits:overworld_diamond_pipes\","
				+ "\"enabled\":true,\"resource\":\"minecraft:diamond_ore\","
				+ "\"alignment\":\"geome_aligned\",\"interaction\":\"hybrid\","
				+ "\"background_generation_scale\":0.15,\"dimensions\":[\"minecraft:overworld\"],"
				+ "\"hosts\":[\"minecraft:stone\"],\"geomes\":[\"orespawn:stable_craton\"],"
				+ "\"min_y\":4,\"max_y\":128,\"region_spacing_chunks\":32,"
				+ "\"selection_chance\":0.5,\"salt\":7146057694168187206,"
				+ "\"shape\":{\"type\":\"diamond_pipe\",\"pipes\":[1,3],"
				+ "\"upper_diameter\":[48,96],\"root_y\":[4,12],\"surface_cover\":[4,12],"
				+ "\"district_radius\":192,\"ore_density\":0.05,\"legacy_selection_scale\":0.25}}")
				.getAsJsonObject();
	}

	@Test
	void understandsDiamondPipesAsTheirOwnShape() {
		assertEquals("diamond_pipe", parsed(pipe()).shape().type());
	}

	@Test
	void shipsLinkedPipesAndPlacersWithOnlyOneBackgroundController() {
		net.minecraft.init.Bootstrap.register();
		java.util.List<DepositDefinition> definitions = DiamondFixtures.definitions();
		assertEquals(2, definitions.size());
		assertTrue(definitions.get(0).isDiamondPipe());
		assertTrue(definitions.get(1).isDiamondPlacer());
		assertEquals(definitions.get(0).id(), definitions.get(1).placer().sourceDeposit());
		assertFalse(definitions.get(1).effectiveBackgroundGenerationScale().isPresent());

		JsonObject ores = OreSpawnIntegration.provider(definitions).toJson().getAsJsonObject("ores");
		int controllers = 0;
		for (java.util.Map.Entry<String, com.google.gson.JsonElement> entry : ores.entrySet()) {
			JsonObject rule = entry.getValue().getAsJsonObject();
			JsonObject dimension = rule.getAsJsonObject("dimensions").getAsJsonObject("minecraft:overworld");
			if (dimension.has("background_generation_scale")) controllers++;
			if (entry.getKey().endsWith("placers")) {
				assertEquals("realisticdeposits:diamond_gravel", rule.get("material").getAsString());
				assertEquals(0, dimension.getAsJsonArray("host_families").size());
				assertTrue(dimension.getAsJsonObject("pattern").getAsJsonObject("settings").has("source_definition"));
			}
		}
		assertEquals(1, controllers);
	}

	@Test
	void disabledSourcesDisablePlacersAndMissingSourcesAreRejected() {
		net.minecraft.init.Bootstrap.register();
		JsonObject disabled = pipe();
		disabled.addProperty("enabled", false);
		JsonObject ores = OreSpawnIntegration.provider(java.util.Arrays.asList(parsed(disabled), DiamondFixtures.placer()))
				.toJson().getAsJsonObject("ores");
		for (com.google.gson.JsonElement rule : ores.entrySet().stream().map(java.util.Map.Entry::getValue)
				.collect(java.util.stream.Collectors.toList())) {
			assertFalse(rule.getAsJsonObject().get("enabled").getAsBoolean());
		}
		assertThrows(IllegalArgumentException.class, () -> OreSpawnIntegration.provider(
				java.util.Collections.singleton(DiamondFixtures.placer())));
	}

	@Test
	void linkedGeometryIsStructuralAndInvalidGeometryIsRejected() {
		JsonObject changed = pipe();
		changed.getAsJsonObject("shape").addProperty("ore_density", 0.07);
		assertFalse(parsed(pipe()).structuralFingerprint().equals(parsed(changed).structuralFingerprint()));
		changed.getAsJsonObject("shape").addProperty("ore_density", -1);
		assertThrows(IllegalArgumentException.class, () -> parsed(changed));

		JsonObject workings = pipe();
		workings.add("abandoned_mine", new JsonParser().parse("{\"enabled\":true,\"chance\":1}"));
		assertTrue(parsed(workings).abandonedMine().enabled());
		JsonObject placer = DiamondFixtures.placer().toJson();
		placer.add("abandoned_mine", workings.get("abandoned_mine"));
		assertThrows(IllegalArgumentException.class, () -> parsed(placer));
	}

	private static DepositDefinition parsed(JsonObject definition) {
		String json = "{\"schema_version\":1,\"catalog\":\"realisticdeposits:diamonds\","
				+ "\"deposits\":[" + definition + "]}";
		DepositCatalog catalogue = DepositCatalogLoader.load(new StringReader(json), "diamond test");
		return catalogue.definitions().get(0);
	}
}
