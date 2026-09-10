package zone.moddev.mc.realisticdeposits.geometry;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;

import org.junit.jupiter.api.Test;

import zone.moddev.mc.realisticdeposits.model.AlignmentMode;
import zone.moddev.mc.realisticdeposits.model.DepositDefinition;
import zone.moddev.mc.realisticdeposits.model.IntRange;
import zone.moddev.mc.realisticdeposits.model.InteractionPolicy;
import zone.moddev.mc.realisticdeposits.model.StratiformSeamSettings;

class RegionDepositPlannerTest {
	@Test
	void selectionAndBodyAreStableForTheSameIdentity() {
		DepositDefinition definition = definition(1.0D);
		StratiformSeamBody first = RegionDepositPlanner.plan(definition, 5059928472718672684L,
				"minecraft:overworld", -3, 7).get();
		StratiformSeamBody second = RegionDepositPlanner.plan(definition, 5059928472718672684L,
				"minecraft:overworld", -3, 7).get();

		assertEquals(first.identity().seed(), second.identity().seed());
		assertEquals(first.centerX(), second.centerX());
		assertEquals(first.centerY(), second.centerY());
		assertEquals(first.centerZ(), second.centerZ());
		assertEquals(first.strikeLength(), second.strikeLength());
		assertEquals(first.downDipWidth(), second.downDipWidth());
		assertEquals(hashBody(first, false), hashBody(second, true));
	}

	@Test
	void worldDimensionDefinitionAndRegionContributeToIdentity() {
		DepositDefinition definition = definition(1.0D);
		long base = RegionDepositPlanner.plan(definition, 19L, "minecraft:overworld", 0, 0)
				.get().identity().seed();
		assertNotEquals(base, RegionDepositPlanner.plan(definition, 20L,
				"minecraft:overworld", 0, 0).get().identity().seed());
		assertFalse(RegionDepositPlanner.plan(definition, 19L,
				"minecraft:the_nether", 0, 0).isPresent());
		assertNotEquals(base, RegionDepositPlanner.plan(definition, 19L,
				"minecraft:overworld", 1, 0).get().identity().seed());
	}

	@Test
	void candidateChanceIsStableRatherThanLoadOrderDriven() {
		DepositDefinition definition = definition(0.27D);
		Set<String> first = selectedRegions(definition, false);
		Set<String> reversed = selectedRegions(definition, true);
		assertEquals(first, reversed);
		assertTrue(first.size() > 10 && first.size() < 55,
				"unexpected deterministic selection count: " + first.size());
	}

	@Test
	void seamSprawlsAcrossChunksButRemainsVerticallyThin() {
		StratiformSeamBody body = RegionDepositPlanner.plan(definition(1.0D), 42L,
				"minecraft:overworld", 0, 0).get();
		BlockBounds bounds = body.bounds();
		Set<String> occupiedChunks = new HashSet<>();
		int minimumOreY = Integer.MAX_VALUE;
		int maximumOreY = Integer.MIN_VALUE;
		for (int x = bounds.minimumX(); x <= bounds.maximumX(); x++) {
			for (int z = bounds.minimumZ(); z <= bounds.maximumZ(); z++) {
				for (int y = bounds.minimumY(); y <= bounds.maximumY(); y++) {
					if (body.isOreAt(x, y, z)) {
						occupiedChunks.add(Math.floorDiv(x, 16) + "," + Math.floorDiv(z, 16));
						minimumOreY = Math.min(minimumOreY, y);
						maximumOreY = Math.max(maximumOreY, y);
					}
				}
			}
		}
		assertTrue(occupiedChunks.size() >= 5, "seam did not form a multi-chunk mine");
		assertTrue(maximumOreY - minimumOreY < body.strikeLength(),
				"tabular body became taller than its strike extent");
	}

	@Test
	void bodyNeverEscapesConfiguredHeightAndTapersAtItsEdges() {
		StratiformSeamBody body = RegionDepositPlanner.plan(definition(1.0D), 7L,
				"minecraft:overworld", 0, 0).get();
		assertEquals(0.0D, body.gradeAt(body.centerX(), 7, body.centerZ()));
		assertEquals(0.0D, body.gradeAt(body.centerX(), 57, body.centerZ()));
		assertTrue(body.gradeAt(body.centerX(), body.centerY(), body.centerZ()) >= 0.0D);
		assertTrue(body.bounds().intersectsChunk(Math.floorDiv(body.centerX(), 16),
				Math.floorDiv(body.centerZ(), 16)));
	}

	private static Set<String> selectedRegions(DepositDefinition definition, boolean reverse) {
		Set<String> selected = new TreeSet<>();
		for (int step = 0; step < 121; step++) {
			int index = reverse ? 120 - step : step;
			int x = index % 11 - 5;
			int z = index / 11 - 5;
			if (RegionDepositPlanner.plan(definition, 918273645L,
					"minecraft:overworld", x, z).isPresent()) selected.add(x + "," + z);
		}
		return selected;
	}

	private static long hashBody(StratiformSeamBody body, boolean reverse) {
		BlockBounds bounds = body.bounds();
		long hash = 0xcbf29ce484222325L;
		int minChunkX = Math.floorDiv(bounds.minimumX(), 16);
		int maxChunkX = Math.floorDiv(bounds.maximumX(), 16);
		int minChunkZ = Math.floorDiv(bounds.minimumZ(), 16);
		int maxChunkZ = Math.floorDiv(bounds.maximumZ(), 16);
		Set<String> points = new TreeSet<>();
		int chunkCountX = maxChunkX - minChunkX + 1;
		int chunkCountZ = maxChunkZ - minChunkZ + 1;
		for (int step = 0; step < chunkCountX * chunkCountZ; step++) {
			int index = reverse ? chunkCountX * chunkCountZ - 1 - step : step;
			int chunkX = minChunkX + index % chunkCountX;
			int chunkZ = minChunkZ + index / chunkCountX;
			for (int x = Math.max(bounds.minimumX(), chunkX << 4);
					x <= Math.min(bounds.maximumX(), (chunkX << 4) + 15); x++) {
				for (int z = Math.max(bounds.minimumZ(), chunkZ << 4);
						z <= Math.min(bounds.maximumZ(), (chunkZ << 4) + 15); z++) {
					for (int y = bounds.minimumY(); y <= bounds.maximumY(); y++) {
						if (body.isOreAt(x, y, z)) points.add(x + "," + y + "," + z);
					}
				}
			}
		}
		for (String point : points) {
			for (int i = 0; i < point.length(); i++) {
				hash ^= point.charAt(i);
				hash *= 0x100000001b3L;
			}
		}
		return hash;
	}

	private static DepositDefinition definition(double chance) {
		return new DepositDefinition("realisticdeposits:test_iron", true, "minecraft:iron_ore",
				AlignmentMode.MASSIVE_RARE, InteractionPolicy.ADDITIVE,
				Arrays.asList("minecraft:overworld"), Arrays.asList("minecraft:stone"),
				8, 56, 16, chance, 93475821L,
				new StratiformSeamSettings(new IntRange(80, 112), new IntRange(16, 28),
						1.0D, 3.0D, -20.0D, 20.0D, 4.0D, 64.0D,
						3.0D, 48.0D, 0.35D, 0.2D));
	}
}
