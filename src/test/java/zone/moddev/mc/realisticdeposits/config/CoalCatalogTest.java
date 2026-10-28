package zone.moddev.mc.realisticdeposits.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.Test;
import net.minecraft.init.Bootstrap;
import zone.moddev.mc.realisticdeposits.integration.OreSpawnIntegration;

class CoalCatalogTest {

	@Test
	void shipsCoalSeparatelyWithoutChangingTheIronCatalogue() {
		InputStream source = getClass().getResourceAsStream("/assets/realisticdeposits/defaults/coal.json");
		assertNotNull(source, "A separate coal catalogue is required");

		DepositCatalog catalogue = DepositCatalogLoader.load(
				new InputStreamReader(source, StandardCharsets.UTF_8), "coal.json");
		assertEquals(1, catalogue.definitions().size());
		assertEquals("minecraft:coal_ore", catalogue.definitions().get(0).resource());
		assertEquals(0.15D, catalogue.definitions().get(0).effectiveBackgroundGenerationScale().getAsDouble());
		assertTrue(catalogue.definitions().get(0).isCoal());
		assertEquals(32, catalogue.definitions().get(0).regionSpacingChunks());
		assertFalse(catalogue.definitions().get(0).abandonedMine().enabled());
	}

	@Test
	void coalDoesNotOfferBroadFamiliesOrWholeStoneBlocksAsAlternativeHosts() {
		Bootstrap.register();
		com.google.gson.JsonObject provider = OreSpawnIntegration.provider(java.util.Collections.singleton(
				zone.moddev.mc.realisticdeposits.geometry.CoalMeasuresBodyTest.definition())).toJson();
		com.google.gson.JsonObject rule = provider.getAsJsonObject("ores").entrySet().iterator().next().getValue()
				.getAsJsonObject().getAsJsonObject("dimensions").getAsJsonObject("minecraft:overworld");
		assertEquals(0, rule.getAsJsonArray("host_families").size());
		rule.getAsJsonArray("host_blocks").forEach(host -> assertTrue(host.getAsJsonObject().has("metadata")));
	}
}
