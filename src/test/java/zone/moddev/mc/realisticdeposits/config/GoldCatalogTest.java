package zone.moddev.mc.realisticdeposits.config;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import zone.moddev.mc.realisticdeposits.integration.OreSpawnIntegration;
import zone.moddev.mc.realisticdeposits.model.DepositDefinition;

import static org.junit.jupiter.api.Assertions.*;

public class GoldCatalogTest {

	public static List<DepositDefinition> defaults() {
		InputStream source = GoldCatalogTest.class.getResourceAsStream(
				"/assets/realisticdeposits/defaults/gold.json");
		assertNotNull(source, "The gold catalogue must be shipped, not installed by a test helper");

		return DepositCatalogLoader.load(new InputStreamReader(source, StandardCharsets.UTF_8),
				"gold.json").definitions();
	}

	@Test
	void shipsTwoLinkedShapesWithOneBackgroundController() {
		List<DepositDefinition> definitions = defaults();
		assertEquals(2, definitions.size());
		assertEquals("gold_lode", definitions.get(0).shape().type());
		assertEquals("gold_placer", definitions.get(1).shape().type());
		assertEquals(0.15, definitions.get(0).effectiveBackgroundGenerationScale().getAsDouble());
		assertFalse(definitions.get(1).effectiveBackgroundGenerationScale().isPresent());
		assertTrue(definitions.get(0).abandonedMine().enabled());
		assertEquals(1.0, definitions.get(0).abandonedMine().chance());
		assertFalse(definitions.get(1).abandonedMine().enabled());
		assertDoesNotThrow(() -> OreSpawnIntegration.provider(definitions));
	}

	@Test
	void goldGeometryAndSourceLinksAreWorldCaptured() {
		for (DepositDefinition definition : defaults()) {
			JsonObject changed = definition.toJson();
			changed.addProperty("salt", definition.salt() + 1);
			DepositDefinition replacement = DepositCatalogLoader.loadDefinition(changed, "changed gold");
			assertNotEquals(definition.structuralFingerprint(), replacement.structuralFingerprint());
		}
	}

	@Test
	void placersCannotAcquireTheirOwnBackgroundPolicyOrLinkToAnotherShape() {
		JsonObject placer = defaults().get(1).toJson();
		placer.addProperty("interaction", "replacement");
		assertThrows(IllegalArgumentException.class, () -> DepositCatalogLoader.loadDefinition(placer, "placer"));

		placer.addProperty("interaction", "additive");
		placer.getAsJsonObject("shape").addProperty("source_deposit", "example:missing");
		DepositDefinition broken = DepositCatalogLoader.loadDefinition(placer, "bad source");
		assertThrows(IllegalArgumentException.class, () -> OreSpawnIntegration.provider(
				java.util.Arrays.asList(defaults().get(0), broken)));
	}

	@Test
	void goldSettingsRoundTripAndRejectUnboundedGeometry() {
		for (DepositDefinition definition : defaults()) {
			assertEquals(definition.toJson(), DepositCatalogLoader.loadDefinition(definition.toJson(), "round trip").toJson());
		}
		JsonObject lode = defaults().get(0).toJson();
		lode.getAsJsonObject("shape").addProperty("legacy_selection_scale", 0);
		assertThrows(IllegalArgumentException.class, () -> DepositCatalogLoader.loadDefinition(lode, "bad rarity"));
	}

	@Test
	void oldCatalogueStillParsesWithoutGoldFields() {
		JsonObject old = new JsonParser().parse("{\"id\":\"example:old\",\"resource\":\"minecraft:iron_ore\","
				+ "\"alignment\":\"massive_rare\",\"interaction\":\"additive\","
				+ "\"dimensions\":[\"minecraft:overworld\"],\"hosts\":[\"minecraft:stone\"],"
				+ "\"min_y\":8,\"max_y\":64,\"region_spacing_chunks\":32,\"selection_chance\":1,\"salt\":1,"
				+ "\"shape\":{\"type\":\"stratiform_seam\",\"strike_length\":[64,128],"
				+ "\"down_dip_width\":[16,24],\"thickness\":[1,3],\"dip_degrees\":[5,15],"
				+ "\"bend_amplitude\":2,\"bend_wavelength\":80,\"fold_amplitude\":2,"
				+ "\"fold_wavelength\":80,\"density\":0.2,\"edge_falloff\":0.2}}")
				.getAsJsonObject();
		assertEquals("stratiform_seam", DepositCatalogLoader.loadDefinition(old, "old").shape().type());
	}
}
