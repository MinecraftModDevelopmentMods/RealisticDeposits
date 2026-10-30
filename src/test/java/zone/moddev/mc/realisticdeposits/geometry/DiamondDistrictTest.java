package zone.moddev.mc.realisticdeposits.geometry;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.junit.jupiter.api.Test;

import zone.moddev.mc.realisticdeposits.DiamondFixtures;
import zone.moddev.mc.realisticdeposits.config.DepositCatalogLoader;
import zone.moddev.mc.realisticdeposits.model.DepositDefinition;

class DiamondDistrictTest {
	private static final long SEED = 3346071746553592696L;

	private static DiamondDistrict.Site site(String geome, int surface) {
		return new DiamondDistrict.Site() {
			@Override public int surfaceY(int x, int z) { return surface; }
			@Override public String geome(int x, int z, int height) { return geome; }
		};
	}

	private static DiamondDistrict first() {
		for (int x = -8; x < 8; x++) {
			Optional<DiamondDistrict> body = DiamondDistrict.plan(DiamondFixtures.pipe(), SEED,
					"minecraft:overworld", x, -3, site("orespawn:stable_craton", 80));
			if (body.isPresent()) return body.get();
		}
		throw new AssertionError("No district selected");
	}

	@Test
	void pipeRootsTopsAndMineralisationStayWithinTheirEnvelope() {
		DiamondDistrict district = first();
		assertTrue(district.pipes().size() >= 1 && district.pipes().size() <= 3);
		for (DiamondDistrict.Pipe pipe : district.pipes()) {
			assertTrue(pipe.bottomY() >= 4 && pipe.bottomY() <= 12);
			assertTrue(pipe.topY() >= 68 && pipe.topY() <= 76);
			assertTrue(pipe.upperRadius() >= 24 && pipe.upperRadius() <= 48);
			assertFalse(pipe.inside(pipe.centerX(), pipe.bottomY() - 1, pipe.centerZ()));
			assertFalse(pipe.isOreAt(pipe.centerX(), pipe.topY() + 1, pipe.centerZ()));
		}
		Set<Long> ore = cells(district, false);
		assertTrue(ore.size() > 0);
		long envelope = 0;
		for (DiamondDistrict.Pipe pipe : district.pipes()) {
			BlockBounds bounds = pipe.bounds();
			for (int x = bounds.minimumX(); x <= bounds.maximumX(); x++) {
				for (int z = bounds.minimumZ(); z <= bounds.maximumZ(); z++) {
					for (int y = pipe.bottomY(); y <= pipe.topY(); y++) {
						if (pipe.inside(x, y, z)) envelope++;
					}
				}
			}
		}
		assertTrue(ore.size() < envelope / 2, "Most of a pipe must remain barren country rock");
		assertTrue(ore.stream().anyMatch(cell -> (cell & 255) > 40), "Shallow portions can contain diamonds");
	}

	@Test
	void reversedChunkOrderAndReplanningGiveExactlyTheSameCells() {
		DiamondDistrict district = first();
		assertEquals(cells(district, false), cells(district, true));
		assertEquals(cells(district, false), cells(first(), false));
	}

	@Test
	void cratonsArePreferredWithoutForcingModernGeologyIntoLegacyWorlds() {
		DepositDefinition definition = DiamondFixtures.pipe();
		int modern = 0;
		int legacy = 0;
		int unavailable = 0;
		for (int x = -16; x < 16; x++) {
			for (int z = -8; z < 8; z++) {
				if (DiamondDistrict.plan(definition, SEED, "minecraft:overworld", x, z,
						site("orespawn:stable_craton", 80)).isPresent()) modern++;
				if (DiamondDistrict.plan(definition, SEED, "minecraft:overworld", x, z,
						site("orespawn:cyano", 80)).isPresent()) legacy++;
				if (DiamondDistrict.plan(definition, SEED, "minecraft:overworld", x, z,
						site(null, 80)).isPresent()) unavailable++;
				assertFalse(DiamondDistrict.plan(definition, SEED, "minecraft:overworld", x, z,
						site("orespawn:volcanic_arc", 80)).isPresent());
			}
		}
		assertTrue(modern > legacy && legacy > 0);
		assertEquals(legacy, unavailable);
		assertFalse(DiamondDistrict.plan(definition, SEED, "minecraft:the_nether", 0, 0,
				site("orespawn:stable_craton", 80)).isPresent());
	}

	@Test
	void gradeChangesDoNotMoveTheSourcePlansOrTheirPlacers() {
		DepositDefinition original = DiamondFixtures.pipe();
		com.google.gson.JsonObject json = original.toJson();
		json.getAsJsonObject("shape").addProperty("ore_density", 0.01);
		DepositDefinition lowerGrade = DepositCatalogLoader.loadDefinition(json, "grade-only change");
		for (int x = -8; x < 8; x++) {
			Optional<DiamondDistrict> a = DiamondDistrict.plan(original, SEED, "minecraft:overworld", x, -3,
					site("orespawn:stable_craton", 80));
			Optional<DiamondDistrict> b = DiamondDistrict.plan(lowerGrade, SEED, "minecraft:overworld", x, -3,
					site("orespawn:stable_craton", 80));
			assertEquals(a.isPresent(), b.isPresent());
			if (!a.isPresent()) continue;
			assertEquals(a.get().identity().seed(), b.get().identity().seed());
			assertEquals(a.get().pipes().size(), b.get().pipes().size());
			for (int i = 0; i < a.get().pipes().size(); i++) {
				DiamondDistrict.Pipe left = a.get().pipes().get(i);
				DiamondDistrict.Pipe right = b.get().pipes().get(i);
				assertEquals(left.centerX(), right.centerX());
				assertEquals(left.centerZ(), right.centerZ());
				assertEquals(left.bottomY(), right.bottomY());
				assertEquals(left.topY(), right.topY());
				assertEquals(left.upperRadius(), right.upperRadius());
			}
			assertEquals(a.get().distanceSquared(0, 0), b.get().distanceSquared(0, 0));
		}
	}

	@Test
	void placerPocketsAndSourceDistancesDoNotDependOnExistingOre() {
		DiamondDistrict district = first();
		assertEquals(0, district.distanceSquared(district.pipes().get(0).centerX(), district.pipes().get(0).centerZ()));
		assertTrue(district.distanceSquared(district.centerX() + 10000, district.centerZ()) > 1024D * 1024);
		int rich = 0;
		long replannedSeed = first().identity().seed();
		for (int x = -32; x < 32; x++) {
			for (int z = -32; z < 32; z++) {
				boolean value = DiamondDistrict.placerPocket(district.identity().seed(), x, z, 0, 0.35);
				assertEquals(value, DiamondDistrict.placerPocket(replannedSeed, x, z, 0, 0.35));
				if (value) rich++;
			}
		}
		assertTrue(rich > 500 && rich < 2500);
	}

	private static Set<Long> cells(DiamondDistrict district, boolean reverse) {
		Set<Long> result = new HashSet<>();
		for (DiamondDistrict.Pipe pipe : district.pipes()) {
			BlockBounds bounds = pipe.bounds();
			List<int[]> chunks = new ArrayList<>();
			for (int x = Math.floorDiv(bounds.minimumX(), 16); x <= Math.floorDiv(bounds.maximumX(), 16); x++) {
				for (int z = Math.floorDiv(bounds.minimumZ(), 16); z <= Math.floorDiv(bounds.maximumZ(), 16); z++) {
					chunks.add(new int[] {x, z});
				}
			}
			if (reverse) Collections.reverse(chunks);
			for (int[] chunk : chunks) {
				for (int x = chunk[0] * 16; x < chunk[0] * 16 + 16; x++) {
					for (int z = chunk[1] * 16; z < chunk[1] * 16 + 16; z++) {
						for (int y = pipe.bottomY(); y <= pipe.topY(); y++) {
							if (pipe.isOreAt(x, y, z)) result.add(((long) (x & 0xfffffff) << 36)
									| ((long) (z & 0xfffffff) << 8) | y);
						}
					}
				}
			}
		}
		return result;
	}
}
