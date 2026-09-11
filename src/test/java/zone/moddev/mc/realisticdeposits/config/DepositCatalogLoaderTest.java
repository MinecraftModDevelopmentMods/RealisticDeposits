package zone.moddev.mc.realisticdeposits.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.util.Collections;

import org.junit.jupiter.api.Test;

import zone.moddev.mc.realisticdeposits.model.AlignmentMode;
import zone.moddev.mc.realisticdeposits.model.DepositDefinition;
import zone.moddev.mc.realisticdeposits.model.InteractionPolicy;
import zone.moddev.mc.orespawn.api.GeologyFamily;

class DepositCatalogLoaderTest {
	@Test
	void loadsPackagedVanillaIronFormation() {
		InputStream stream = getClass().getResourceAsStream(
				"/assets/realisticdeposits/defaults/vanilla.json");
		DepositCatalog catalog = DepositCatalogLoader.load(
				new InputStreamReader(stream, StandardCharsets.UTF_8), "vanilla.json");

		assertEquals("realisticdeposits:vanilla", catalog.id());
		assertEquals(1, catalog.definitions().size());
		assertEquals("realisticdeposits:overworld_iron_formation",
				catalog.definitions().get(0).id());
		assertEquals("minecraft:iron_ore", catalog.definitions().get(0).resource());
		assertEquals(AlignmentMode.MASSIVE_RARE, catalog.definitions().get(0).alignment());
		assertEquals(InteractionPolicy.HYBRID, catalog.definitions().get(0).interaction());
		assertEquals(0.15D, catalog.definitions().get(0)
				.effectiveBackgroundGenerationScale().getAsDouble());
		assertEquals(640, catalog.definitions().get(0).seam().strikeLength().minimum());
		assertEquals(960, catalog.definitions().get(0).seam().strikeLength().maximum());
		assertTrue(catalog.warnings().isEmpty());
	}

	@Test
	void reportsForwardKeysWithoutChangingTheDefinition() {
		String json = validJson().replace("\"deposits\":[", "\"future_root\": 2, \"deposits\":[")
				.replace("\"resource\":", "\"future_deposit\": true, \"resource\":")
				.replace("\"type\":", "\"future_shape\": {}, \"type\":");
		DepositCatalog catalog = DepositCatalogLoader.load(new StringReader(json), "future.json");
		assertEquals(3, catalog.warnings().size());
		assertEquals("minecraft:iron_ore", catalog.definitions().get(0).resource());
	}

	@Test
	void rejectsMalformedAndDuplicateIdentifiers() {
		IllegalArgumentException malformed = assertThrows(IllegalArgumentException.class,
				() -> DepositCatalogLoader.load(new StringReader(
						validJson().replace("minecraft:iron_ore", "Minecraft:Iron Ore")), "bad.json"));
		assertTrue(malformed.getMessage().contains("Invalid canonical resource"));

		String duplicate = validJson().replace("]\n}", "," + definitionJson() + "]\n}");
		IllegalArgumentException repeated = assertThrows(IllegalArgumentException.class,
				() -> DepositCatalogLoader.load(new StringReader(duplicate), "duplicate.json"));
		assertTrue(repeated.getMessage().contains("duplicate deposit id"));
	}

	@Test
	void rejectsUnsupportedShapeAndUnsafeRanges() {
		assertThrows(IllegalArgumentException.class, () -> DepositCatalogLoader.load(
				new StringReader(validJson().replace("stratiform_seam", "giant_blob")), "shape.json"));
		assertThrows(IllegalArgumentException.class, () -> DepositCatalogLoader.load(
				new StringReader(validJson().replace("\"min_y\":8", "\"min_y\":-1")), "height.json"));
	}

	@Test
	void rejectsStringEncodedScalarsAndFractionalSalt() {
		assertThrows(IllegalArgumentException.class, () -> DepositCatalogLoader.load(
				new StringReader(validJson().replace("\"enabled\":true", "\"enabled\":\"true\"")),
				"boolean.json"));
		assertThrows(IllegalArgumentException.class, () -> DepositCatalogLoader.load(
				new StringReader(validJson().replace("\"selection_chance\":1.0",
						"\"selection_chance\":\"1.0\"")), "number.json"));
		assertThrows(IllegalArgumentException.class, () -> DepositCatalogLoader.load(
				new StringReader(validJson().replace("\"salt\":17", "\"salt\":17.5")),
				"integer.json"));
	}

	@Test
	void parsesGeologyBiomeAndHybridPolicyControls() {
		String json = validJson().replace("\"alignment\":\"massive_rare\"",
				"\"alignment\":\"rock_aligned\",\"geomes\":[\"orespawn:sedimentary_basin\"],"
				+ "\"rock_families\":[\"sedimentary\"],\"biome_ids\":[\"minecraft:plains\"],"
				+ "\"excluded_biome_ids\":[\"minecraft:desert\"],"
				+ "\"biome_dictionary\":[\"forest\"],\"excluded_biome_dictionary\":[\"hot\"]")
				.replace("\"interaction\":\"additive\"",
						"\"interaction\":\"hybrid\",\"background_generation_scale\":0.4");
		DepositDefinition definition = DepositCatalogLoader.load(new StringReader(json),
				"aligned.json").definitions().get(0);

		assertEquals(Collections.singleton("orespawn:sedimentary_basin"), definition.geomes());
		assertEquals(Collections.singleton(GeologyFamily.SEDIMENTARY), definition.rockFamilies());
		assertEquals(Collections.singleton("minecraft:plains"), definition.biomeIds());
		assertEquals(Collections.singleton("minecraft:desert"), definition.excludedBiomeIds());
		assertEquals(Collections.singleton("FOREST"), definition.biomeDictionary());
		assertEquals(Collections.singleton("HOT"), definition.excludedBiomeDictionary());
		assertEquals(0.4D, definition.effectiveBackgroundGenerationScale().getAsDouble());
		assertEquals(definition.toJson(), DepositCatalogLoader.loadDefinition(definition.toJson(),
				"round-trip" ).toJson());
	}

	@Test
	void enforcesAlignmentAndPolicySpecificFields() {
		assertThrows(IllegalArgumentException.class, () -> DepositCatalogLoader.load(
				new StringReader(validJson().replace("\"alignment\":\"massive_rare\"",
						"\"alignment\":\"geome_aligned\"")), "missing-geome.json"));
		assertThrows(IllegalArgumentException.class, () -> DepositCatalogLoader.load(
				new StringReader(validJson().replace("\"interaction\":\"additive\"",
						"\"interaction\":\"additive\",\"background_generation_scale\":0.5")),
				"additive-scale.json"));
		DepositDefinition hybrid = DepositCatalogLoader.load(new StringReader(validJson()
				.replace("\"interaction\":\"additive\"", "\"interaction\":\"hybrid\"")),
				"hybrid-default.json").definitions().get(0);
		assertEquals(0.25D, hybrid.effectiveBackgroundGenerationScale().getAsDouble());
	}

	private static String validJson() {
		return "{\n\"schema_version\":1,\n\"catalog\":\"realisticdeposits:test\",\n\"deposits\":["
				+ definitionJson() + "]\n}";
	}

	private static String definitionJson() {
		return "{\"id\":\"realisticdeposits:test_iron\",\"enabled\":true,"
				+ "\"resource\":\"minecraft:iron_ore\",\"alignment\":\"massive_rare\","
				+ "\"interaction\":\"additive\",\"dimensions\":[\"minecraft:overworld\"],"
				+ "\"hosts\":[\"minecraft:stone\"],\"min_y\":8,\"max_y\":56,"
				+ "\"region_spacing_chunks\":16,\"selection_chance\":1.0,\"salt\":17,"
				+ "\"shape\":{\"type\":\"stratiform_seam\",\"strike_length\":[64,96],"
				+ "\"down_dip_width\":[16,24],\"thickness\":[1.0,3.0],"
				+ "\"dip_degrees\":[-20,20],\"bend_amplitude\":4,\"bend_wavelength\":64,"
				+ "\"fold_amplitude\":3,\"fold_wavelength\":48,\"density\":0.25,"
				+ "\"edge_falloff\":0.2}}";
	}
}
