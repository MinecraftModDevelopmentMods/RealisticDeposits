package zone.moddev.mc.realisticdeposits.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.InputStreamReader;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.Test;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import zone.moddev.mc.realisticdeposits.geometry.RegionDepositPlanner;
import zone.moddev.mc.realisticdeposits.model.DepositDefinition;

class AbandonedMineCatalogTest {

	@Test
	void anOptedInMineSurvivesTheOreSpawnProfileRoundTrip() {
		JsonObject catalog = catalog();
		JsonObject mine = new JsonObject();
		mine.addProperty("enabled", true);
		mine.addProperty("chance", 0.25D);
		catalog.getAsJsonArray("deposits").get(0).getAsJsonObject().add("abandoned_mine", mine);

		DepositDefinition definition = load(catalog);
		assertTrue(definition.toJson().has("abandoned_mine"), "The world profile must retain mine settings");
		assertEquals(mine, definition.toJson().getAsJsonObject("abandoned_mine"));
		assertEquals(definition.toJson(), DepositCatalogLoader.loadDefinition(
				definition.toJson(), "round trip").toJson());
	}

	@Test
	void anOlderCatalogRemainsOptedOutAndKeepsItsProfileIdentity() {
		JsonObject catalog = catalog();
		catalog.getAsJsonArray("deposits").get(0).getAsJsonObject().remove("abandoned_mine");

		DepositDefinition definition = load(catalog);
		assertFalse(definition.toJson().has("abandoned_mine"));
		assertFalse(definition.structuralFingerprint().contains("abandoned_mine"));
	}

	@Test
	void enablingMinesDoesNotMoveOrReseedTheOreBody() {
		JsonObject catalog = catalog();
		DepositDefinition original = load(catalog);
		JsonObject mine = new JsonObject();
		mine.addProperty("enabled", true);
		mine.addProperty("chance", 1.0D);
		catalog.getAsJsonArray("deposits").get(0).getAsJsonObject().add("abandoned_mine", mine);
		DepositDefinition changed = load(catalog);

		assertEquals(RegionDepositPlanner.plan(original, 3346071746553592696L,
				"minecraft:overworld", -1, -1).get().identity().seed(),
				RegionDepositPlanner.plan(changed, 3346071746553592696L,
						"minecraft:overworld", -1, -1).get().identity().seed());
	}

	@Test
	void invalidChancesAreRejectedRatherThanQuietlyEnablingEveryMine() {
		for (double chance : new double[] {-0.01D, 1.01D}) {
			JsonObject catalog = catalog();
			JsonObject mine = new JsonObject();
			mine.addProperty("enabled", true);
			mine.addProperty("chance", chance);
			catalog.getAsJsonArray("deposits").get(0).getAsJsonObject().add("abandoned_mine", mine);

			assertThrows(IllegalArgumentException.class, () -> load(catalog));
		}
	}

	private static DepositDefinition load(JsonObject catalog) {
		return DepositCatalogLoader.load(new StringReader(catalog.toString()), "test catalog")
				.definitions().get(0);
	}

	private static JsonObject catalog() {
		return new JsonParser().parse(new InputStreamReader(
				AbandonedMineCatalogTest.class.getResourceAsStream(
						"/assets/realisticdeposits/defaults/vanilla.json"), StandardCharsets.UTF_8))
				.getAsJsonObject();
	}
}
