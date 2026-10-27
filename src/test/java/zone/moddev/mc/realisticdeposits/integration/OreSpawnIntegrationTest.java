package zone.moddev.mc.realisticdeposits.integration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Arrays;
import java.util.Collections;

import org.junit.jupiter.api.Test;

import com.google.gson.JsonObject;

import zone.moddev.mc.orespawn.api.GeologyFamily;
import zone.moddev.mc.orespawn.api.WorldgenProvider;
import zone.moddev.mc.realisticdeposits.model.AlignmentMode;
import zone.moddev.mc.realisticdeposits.model.DepositDefinition;
import zone.moddev.mc.realisticdeposits.model.IntRange;
import zone.moddev.mc.realisticdeposits.model.InteractionPolicy;
import zone.moddev.mc.realisticdeposits.model.StratiformSeamSettings;

class OreSpawnIntegrationTest {
	@Test
	void providerCapturesAllPoliciesAndStructuralWorldContract() {
		WorldgenProvider provider = OreSpawnIntegration.provider(Arrays.asList(
				definition("additive", "minecraft:iron_ore", InteractionPolicy.ADDITIVE, null),
				definition("hybrid", "minecraft:gold_ore", InteractionPolicy.HYBRID, null),
				definition("replacement", "minecraft:diamond_ore", InteractionPolicy.REPLACEMENT, null)));
		JsonObject root = provider.toJson();

		assertFalse(provider.mergeNewEntriesIntoExistingWorlds());
		assertFalse(root.get("merge_new_entries_into_existing_worlds").getAsBoolean());
		JsonObject ores = root.getAsJsonObject("ores");
		assertEquals(3, ores.entrySet().size());
		assertScale(ores.getAsJsonObject("realisticdeposits:deposit/realisticdeposits/additive"), null);
		assertScale(ores.getAsJsonObject("realisticdeposits:deposit/realisticdeposits/hybrid"), 0.25D);
		assertScale(ores.getAsJsonObject("realisticdeposits:deposit/realisticdeposits/replacement"), 0.0D);
		JsonObject hybrid = dimension(ores.getAsJsonObject(
				"realisticdeposits:deposit/realisticdeposits/hybrid"));
		assertEquals("realisticdeposits:stratiform_seam",
				hybrid.getAsJsonObject("pattern").get("type").getAsString());
		assertEquals("realisticdeposits:hybrid", hybrid.getAsJsonObject("pattern")
				.getAsJsonObject("settings").getAsJsonObject("definition").get("id").getAsString());
	}

	@Test
	void explicitHybridScaleAndBiomeFiltersReachExistingProviderFields() {
		DepositDefinition definition = new DepositDefinition("realisticdeposits:filtered", true,
				"minecraft:emerald_ore", AlignmentMode.ROCK_ALIGNED, InteractionPolicy.HYBRID,
				Collections.singleton("minecraft:overworld"), Collections.singleton("minecraft:stone"),
				Collections.singleton("orespawn:mountain_belt"),
				Collections.singleton(GeologyFamily.METAMORPHIC),
				Collections.singleton("minecraft:extreme_hills"),
				Collections.singleton("minecraft:desert"), Collections.singleton("MOUNTAIN"),
				Collections.singleton("HOT"), 0.4D, 8, 56, 16, 1.0D, 19L, seam());
		JsonObject ore = OreSpawnIntegration.provider(Collections.singleton(definition)).toJson()
				.getAsJsonObject("ores").getAsJsonObject(
						"realisticdeposits:deposit/realisticdeposits/filtered");
		JsonObject dimension = dimension(ore);

		assertEquals(0.4D, dimension.get("background_generation_scale").getAsDouble());
		assertEquals(4, dimension.getAsJsonArray("host_families").size());
		assertEquals(0, dimension.get("min_y").getAsInt());
		assertEquals(255, dimension.get("max_y").getAsInt());
		assertEquals("minecraft:extreme_hills", dimension.getAsJsonArray("biome_ids").get(0).getAsString());
		assertEquals("minecraft:desert", dimension.getAsJsonArray("excluded_biome_ids").get(0).getAsString());
		assertEquals("MOUNTAIN", dimension.getAsJsonArray("biome_dictionary").get(0).getAsString());
		assertEquals("HOT", dimension.getAsJsonArray("excluded_biome_dictionary").get(0).getAsString());
	}

	private static void assertScale(JsonObject ore, Double expected) {
		JsonObject dimension = dimension(ore);
		if (expected == null) assertFalse(dimension.has("background_generation_scale"));
		else assertEquals(expected.doubleValue(), dimension.get("background_generation_scale").getAsDouble());
		assertFalse(ore.get("retrogen").getAsBoolean());
		assertFalse(ore.get("suppress_vanilla").getAsBoolean());
	}

	private static JsonObject dimension(JsonObject ore) {
		return ore.getAsJsonObject("dimensions").getAsJsonObject("minecraft:overworld");
	}

	private static DepositDefinition definition(String id, String output,
			InteractionPolicy interaction, Double scale) {
		return new DepositDefinition("realisticdeposits:" + id, true, output,
				AlignmentMode.MASSIVE_RARE, interaction,
				Collections.singleton("minecraft:overworld"), Collections.singleton("minecraft:stone"),
				Collections.emptySet(), Collections.emptySet(), Collections.emptySet(),
				Collections.emptySet(), Collections.emptySet(), Collections.emptySet(), scale,
				8, 56, 16, 1.0D, 19L, seam());
	}

	private static StratiformSeamSettings seam() {
		return new StratiformSeamSettings(new IntRange(64, 96), new IntRange(16, 24),
				1.0D, 3.0D, -20.0D, 20.0D, 4.0D, 64.0D,
				3.0D, 48.0D, 0.25D, 0.2D);
	}
}
