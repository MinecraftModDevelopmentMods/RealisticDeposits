package zone.moddev.mc.realisticdeposits.geometry;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Deque;
import java.util.HashSet;
import java.util.List;
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
	void defaultIronFormationContainsAMineScaleConnectedRun() {
		DepositDefinition definition = new DepositDefinition(
				"realisticdeposits:overworld_iron_formation", true, "minecraft:iron_ore",
				AlignmentMode.MASSIVE_RARE, InteractionPolicy.HYBRID,
				Arrays.asList("minecraft:overworld"), Arrays.asList("minecraft:stone"),
				Collections.emptySet(), Collections.emptySet(), Collections.emptySet(),
				Collections.emptySet(), Collections.emptySet(), Collections.emptySet(), 0.15D,
				4, 120, 64, 0.25D, 7146057694168187204L,
				new StratiformSeamSettings(new IntRange(640, 960), new IntRange(160, 240),
						3.0D, 8.0D, 10.0D, 22.0D, 32.0D, 240.0D,
						12.0D, 180.0D, 0.12D, 0.18D));
		StratiformSeamBody body = RegionDepositPlanner.plan(definition,
				3346071746553592696L, "minecraft:overworld", -1, -1).get();
		Set<Long> ore = orePositions(body);
		List<Integer> components = componentSizes(ore);
		int largest = components.stream().mapToInt(Integer::intValue).max().orElse(0);
		components.sort(Collections.reverseOrder());
		double minimumAlong = Double.POSITIVE_INFINITY;
		double maximumAlong = Double.NEGATIVE_INFINITY;
		for (long position : ore) {
			double along = body.localAlongAt(unpackX(position), unpackZ(position));
			minimumAlong = Math.min(minimumAlong, along);
			maximumAlong = Math.max(maximumAlong, along);
		}

		assertTrue(ore.size() >= 150000 && ore.size() <= 180000,
				"planned major district size was " + ore.size());
		assertTrue(largest >= 30000,
				"largest run was only " + largest + " blocks for strike " + body.strikeLength());
		assertTrue(components.size() >= 3 && components.get(2) >= 10000,
				"major district did not contain three substantial fault-separated sections");
		assertTrue(maximumAlong - minimumAlong >= 640.0D,
				"combined haulage extent did not span 640 blocks");
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

	private static Set<Long> orePositions(StratiformSeamBody body) {
		Set<Long> result = new HashSet<>();
		BlockBounds bounds = body.bounds();
		for (int x = bounds.minimumX(); x <= bounds.maximumX(); x++) {
			for (int z = bounds.minimumZ(); z <= bounds.maximumZ(); z++) {
				for (int y = bounds.minimumY(); y <= bounds.maximumY(); y++) {
					if (body.isOreAt(x, y, z)) result.add(pack(x, y, z));
				}
			}
		}
		return result;
	}

	private static List<Integer> componentSizes(Set<Long> positions) {
		Set<Long> remaining = new HashSet<>(positions);
		List<Integer> result = new ArrayList<>();
		Deque<Long> pending = new ArrayDeque<>();
		while (!remaining.isEmpty()) {
			long start = remaining.iterator().next();
			remaining.remove(start);
			pending.add(start);
			int size = 0;
			while (!pending.isEmpty()) {
				long packed = pending.removeFirst();
				size++;
				int x = unpackX(packed);
				int y = unpackY(packed);
				int z = unpackZ(packed);
				visit(remaining, pending, x - 1, y, z);
				visit(remaining, pending, x + 1, y, z);
				visit(remaining, pending, x, y - 1, z);
				visit(remaining, pending, x, y + 1, z);
				visit(remaining, pending, x, y, z - 1);
				visit(remaining, pending, x, y, z + 1);
			}
			result.add(size);
		}
		return result;
	}

	private static void visit(Set<Long> remaining, Deque<Long> pending, int x, int y, int z) {
		long packed = pack(x, y, z);
		if (remaining.remove(packed)) pending.addLast(packed);
	}

	private static long pack(int x, int y, int z) {
		return ((long) (x & 0x3FFFFFF) << 38) | ((long) (z & 0x3FFFFFF) << 12)
				| (long) (y & 0xFFF);
	}

	private static int unpackX(long packed) {
		return (int) (packed >> 38);
	}

	private static int unpackY(long packed) {
		return (int) (packed << 52 >> 52);
	}

	private static int unpackZ(long packed) {
		return (int) (packed << 26 >> 38);
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
