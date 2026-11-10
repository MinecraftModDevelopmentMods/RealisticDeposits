package zone.moddev.mc.realisticdeposits.config;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

import com.google.gson.JsonObject;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeAll;

import zone.moddev.mc.realisticdeposits.model.DepositDefinition;

import static org.junit.jupiter.api.Assertions.*;

public class LapisCatalogTest {
	@BeforeAll
	static void vanilla() {
		net.minecraft.init.Bootstrap.register();
	}

	public static DepositDefinition defaults() {
		InputStream source = LapisCatalogTest.class.getResourceAsStream(
				"/assets/realisticdeposits/defaults/lapis.json");
		assertNotNull(source, "Ship the lapis catalogue with the mod");

		return DepositCatalogLoader.load(new InputStreamReader(source, StandardCharsets.UTF_8),
				"lapis.json").definitions().get(0);
	}

	@Test
	void shipsASeparateLapisShapeAndBackgroundController() {
		DepositDefinition definition = defaults();

		assertEquals("lapis_lens", definition.shape().type());
		assertEquals("minecraft:lapis_ore", definition.resource());
		assertEquals(0.30, definition.effectiveBackgroundGenerationScale().getAsDouble());
		assertTrue(definition.abandonedMine().enabled());
		assertEquals(1.0, definition.abandonedMine().chance());
	}

	@Test
	void geometryIsCapturedAndRoundTripsWithoutLoss() {
		DepositDefinition definition = defaults();
		assertEquals(definition.toJson(), DepositCatalogLoader.loadDefinition(
				definition.toJson(), "round trip").toJson());

		JsonObject changed = definition.toJson();
		changed.addProperty("salt", definition.salt() + 1);
		assertNotEquals(definition.structuralFingerprint(), DepositCatalogLoader.loadDefinition(
				changed, "changed lapis").structuralFingerprint());
	}

	@Test
	void exactProviderHostsCannotBeBroadenedByRockFamilies() {
		JsonObject ore = zone.moddev.mc.realisticdeposits.integration.OreSpawnIntegration
				.provider(java.util.Collections.singleton(defaults())).toJson().getAsJsonObject("ores")
				.getAsJsonObject("realisticdeposits:deposit/realisticdeposits/overworld_lapis_lenses");
		JsonObject dimension = ore.getAsJsonObject("dimensions").getAsJsonObject("minecraft:overworld");

		assertEquals(0, dimension.getAsJsonArray("host_families").size());
		assertEquals(4, dimension.getAsJsonArray("host_blocks").size());
		java.util.Set<Integer> variants = new java.util.HashSet<>();
		for (com.google.gson.JsonElement value : dimension.getAsJsonArray("host_blocks")) {
			JsonObject host = value.getAsJsonObject();
			assertEquals("minecraft:stone", host.get("block").getAsString());
			variants.add(host.get("metadata").getAsInt());
		}
		assertEquals(new java.util.HashSet<>(java.util.Arrays.asList(0, 1, 3, 5)), variants);
		assertFalse(ore.get("retrogen").getAsBoolean());
		assertEquals("realisticdeposits:lapis_lens", dimension.getAsJsonObject("pattern").get("type").getAsString());
	}

	@Test
	void unboundedOrInvalidSettingsFailWithAnActionableError() {
		JsonObject json = defaults().toJson();
		json.getAsJsonObject("shape").addProperty("legacy_selection_scale", 0);
		assertThrows(IllegalArgumentException.class, () -> DepositCatalogLoader.loadDefinition(json, "lapis.json"));
	}
}
