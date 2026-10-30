package zone.moddev.mc.realisticdeposits.generation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonObject;
import org.junit.jupiter.api.Test;

import zone.moddev.mc.realisticdeposits.DiamondFixtures;
import zone.moddev.mc.realisticdeposits.config.DepositCatalogLoader;
import zone.moddev.mc.realisticdeposits.model.DepositDefinition;

class DiamondRuleSwitchTest {
	@Test
	void anEnabledOreSpawnCallbackHonoursTheWorldSwitchWithoutMutatingDefaults() {
		JsonObject json = DiamondFixtures.pipe().toJson();
		json.addProperty("enabled", false);
		DepositDefinition disabled = DepositCatalogLoader.loadDefinition(json, "disabled catalogue");
		DepositDefinition active = DiamondPattern.enabledForCallback(disabled);
		assertTrue(active.enabled());
		assertFalse(disabled.enabled());
		JsonObject expected = disabled.toJson();
		expected.addProperty("enabled", true);
		assertEquals(expected, active.toJson());
	}
}
