package zone.moddev.mc.realisticdeposits.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

import com.google.gson.JsonObject;
import org.junit.jupiter.api.Test;

import zone.moddev.mc.realisticdeposits.model.DepositDefinition;

class MineDefaultsTest {

	@Test
	void everyNewUndergroundDepositAttemptsWorkings() {
		for (String name : new String[] {"vanilla", "coal", "diamonds"}) {
			for (DepositDefinition definition : catalogue(name).definitions()) {
				if (definition.isDiamondPlacer()) {
					assertFalse(definition.abandonedMine().enabled());
				} else {
					assertTrue(definition.abandonedMine().enabled(), definition.id());
					assertEquals(1.0D, definition.abandonedMine().chance(), definition.id());
				}
			}
		}
	}

	@Test
	void diamondPipesCanCaptureWorkingsWithoutMovingTheDeposit() {
		DepositDefinition original = catalogue("diamonds").definitions().get(0);
		JsonObject json = original.toJson();
		JsonObject mine = new JsonObject();
		mine.addProperty("enabled", true);
		mine.addProperty("chance", 1.0D);
		json.add("abandoned_mine", mine);

		DepositDefinition changed = DepositCatalogLoader.loadDefinition(json, "diamond workings");
		assertTrue(changed.abandonedMine().enabled());
		assertEquals(original.shape().toJson(), changed.shape().toJson());
		assertEquals(original.salt(), changed.salt());
		assertEquals(mine, changed.toJson().getAsJsonObject("abandoned_mine"));
	}

	@Test
	void oldProfilesAndExplicitOptOutsStayOff() {
		for (String name : new String[] {"vanilla", "coal", "diamonds"}) {
			for (DepositDefinition definition : catalogue(name).definitions()) {
				JsonObject legacy = definition.toJson();
				legacy.remove("abandoned_mine");
				DepositDefinition parsed = DepositCatalogLoader.loadDefinition(legacy, "old world");
				assertFalse(parsed.abandonedMine().enabled());
				assertFalse(parsed.toJson().has("abandoned_mine"));

				JsonObject disabled = new JsonObject();
				disabled.addProperty("enabled", false);
				disabled.addProperty("chance", 0.37D);
				legacy.add("abandoned_mine", disabled);
				parsed = DepositCatalogLoader.loadDefinition(legacy, "custom opt-out");
				assertFalse(parsed.abandonedMine().enabled());
				assertEquals(disabled, parsed.toJson().getAsJsonObject("abandoned_mine"));
			}
		}
	}

	private static DepositCatalog catalogue(String name) {
		return DepositCatalogLoader.load(new InputStreamReader(MineDefaultsTest.class.getResourceAsStream(
				"/assets/realisticdeposits/defaults/" + name + ".json"), StandardCharsets.UTF_8), name);
	}
}
