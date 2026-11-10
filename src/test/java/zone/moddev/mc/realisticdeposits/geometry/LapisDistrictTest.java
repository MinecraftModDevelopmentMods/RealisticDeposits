package zone.moddev.mc.realisticdeposits.geometry;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.junit.jupiter.api.Test;

import zone.moddev.mc.realisticdeposits.config.LapisCatalogTest;
import zone.moddev.mc.realisticdeposits.model.DepositDefinition;

import static org.junit.jupiter.api.Assertions.*;

public class LapisDistrictTest {

	public static LapisDistrict.Site site(String geome, int surface) {
		return new LapisDistrict.Site() {
			@Override public int surfaceY(int x, int z) { return surface; }
			@Override public String geome(int x, int z, int y) { return geome; }
			@Override public double fitHeight(int x, int z, double target, int thickness) { return target; }
		};
	}

	public static LapisDistrict body(long seed) {
		DepositDefinition definition = LapisCatalogTest.defaults();

		for (int region = -16; region <= 16; region++) {
			Optional<LapisDistrict> body = LapisDistrict.plan(definition, seed, "minecraft:overworld",
					region, -2, site("orespawn:mountain_belt", 90));
			if (body.isPresent()) return body.get();
		}

		throw new AssertionError("No lapis lens in fixture search");
	}

	@Test
	void repeatableCompactLensesRespectTheirDimensionsAndHeightLimits() {
		for (long seed = 10; seed < 30; seed++) {
			LapisDistrict body = body(seed);
			assertEquals(body.identity().seed(), body(seed).identity().seed());
			assertEquals(body.centerY(), body(seed).centerY());
			assertTrue(body.lenses().size() >= 1 && body.lenses().size() <= 3);
			assertThrows(UnsupportedOperationException.class, () -> body.lenses().clear());

			for (LapisDistrict.Lens lens : body.lenses()) {
				assertTrue(lens.length() >= 64 && lens.length() <= 160);
				assertTrue(lens.width() >= 12 && lens.width() <= 24);
				assertTrue(lens.thickness() >= 1 && lens.thickness() <= 4);
			}

			Set<Long> ore = blocks(body, false);
			assertTrue(ore.size() > 60 && ore.size() < 25000, "Compact but worthwhile lenses: " + ore.size());

			for (long packed : ore) {
				MinePoint point = MinePoint.unpack(packed);
				assertTrue(point.y() >= 8 && point.y() <= 90 - body.surfaceCover());
				assertTrue(point.y() >= body.minimumCandidateYAt(point.x(), point.z()));
				assertTrue(point.y() <= body.maximumCandidateYAt(point.x(), point.z()));
			}
		}
	}

	@Test
	void lensesStayIdenticalAcrossChunkOrdersAndFreshPlanners() {
		LapisDistrict first = body(81);
		assertEquals(blocks(first, false), blocks(body(81), true));
	}

	@Test
	void boundedMarbleSearchRecoversAFailedSiteWithoutChangingLegacyOrNativeGeometry() {
		long seed = 81;
		LapisDistrict original = body(seed);
		int rx = original.identity().regionX(), rz = original.identity().regionZ();
		DepositDefinition current = LapisCatalogTest.defaults();
		com.google.gson.JsonObject json = current.toJson();
		json.getAsJsonObject("shape").remove("marble_search_radius");
		DepositDefinition captured = zone.moddev.mc.realisticdeposits.config.DepositCatalogLoader
				.loadDefinition(json, "captured lapis");
		assertEquals(json, captured.toJson());
		LapisDistrict.Site marble = new LapisDistrict.Site() {
			@Override public int surfaceY(int x, int z) { return 90; }
			@Override public String geome(int x, int z, int y) { return "orespawn:mountain_belt"; }
			@Override public boolean hasPhysicalStrata() { return true; }
			@Override public double fitHeight(int x, int z, double target, int thickness) {
				return Math.hypot(x - original.centerX(), z - original.centerZ()) < 37 ? Double.NaN : target;
			}
		};
		assertFalse(LapisDistrict.plan(captured, seed, "minecraft:overworld", rx, rz, marble).isPresent());
		LapisDistrict fitted = LapisDistrict.plan(current, seed, "minecraft:overworld", rx, rz, marble).get();
		assertEquals(original.identity().toString(), fitted.identity().toString());
		assertEquals(rx, Math.floorDiv(fitted.centerX(), 1024));
		assertEquals(rz, Math.floorDiv(fitted.centerZ(), 1024));
		assertEquals(blocks(fitted, false), blocks(LapisDistrict.plan(current, seed, "minecraft:overworld", rx, rz, marble).get(), true));
		assertEquals(blocks(original, false), blocks(LapisDistrict.plan(captured, seed, "minecraft:overworld", rx, rz,
				site("orespawn:mountain_belt", 90)).get(), true));
	}

	@Test
	void richPocketsContainFollowableSectionsRatherThanScatteredSingleBlocks() {
		LapisDistrict body = body(81);
		Set<Long> remaining = blocks(body, false);
		int largest = 0;
		int singles = 0;
		int total = remaining.size();

		while (!remaining.isEmpty()) {
			java.util.ArrayDeque<Long> queue = new java.util.ArrayDeque<>();
			long first = remaining.iterator().next();
			queue.add(first);
			remaining.remove(first);
			int connected = 0;

			while (!queue.isEmpty()) {
				MinePoint point = MinePoint.unpack(queue.remove());
				connected++;

				for (int[] offset : new int[][] {{1,0,0}, {-1,0,0}, {0,1,0}, {0,-1,0}, {0,0,1}, {0,0,-1}}) {
					long next = new MinePoint(point.x() + offset[0], point.y() + offset[1], point.z() + offset[2]).packed();
					if (remaining.remove(next)) queue.add(next);
				}
			}

			largest = Math.max(largest, connected);
			if (connected == 1) singles++;
		}

		assertTrue(largest > 100, "The lens needs a substantial connected mining section");
		assertTrue(singles < total / 10, "The lens must not become a cloud of isolated blocks");
	}

	@Test
	void unsuitableGeologyMissingMarbleAndWrongDimensionsFailClosed() {
		DepositDefinition definition = LapisCatalogTest.defaults();
		LapisDistrict.Site noMarble = new LapisDistrict.Site() {
			@Override public int surfaceY(int x, int z) { return 90; }
			@Override public String geome(int x, int z, int y) { return "orespawn:mountain_belt"; }
			@Override public double fitHeight(int x, int z, double target, int thickness) { return Double.NaN; }
		};

		for (int region = 0; region < 32; region++) {
			assertFalse(LapisDistrict.plan(definition, 7, "minecraft:overworld", region, 0,
					site("orespawn:sedimentary_basin", 90)).isPresent());
			assertFalse(LapisDistrict.plan(definition, 7, "minecraft:overworld", region, 0, noMarble).isPresent());
			assertFalse(LapisDistrict.plan(definition, 7, "minecraft:the_nether", region, 0,
					site("orespawn:mountain_belt", 90)).isPresent());
		}
	}

	@Test
	void legacyApproximationIsRarerAndIndependentOfMissingClassification() {
		DepositDefinition definition = LapisCatalogTest.defaults();
		int modern = 0;
		int legacy = 0;

		for (int region = 0; region < 256; region++) {
			if (LapisDistrict.plan(definition, 19, "minecraft:overworld", region, 0,
					site("orespawn:mountain_belt", 90)).isPresent()) modern++;

			Optional<LapisDistrict> cyano = LapisDistrict.plan(definition, 19, "minecraft:overworld", region, 0,
					site("orespawn:cyano", 90));
			Optional<LapisDistrict> absent = LapisDistrict.plan(definition, 19, "minecraft:overworld", region, 0,
					site(null, 90));
			assertEquals(cyano.isPresent(), absent.isPresent());
			if (cyano.isPresent()) legacy++;
		}

		assertTrue(legacy > 0 && legacy < modern / 2);
	}

	@Test
	void workingsLeaveMostOfTheCompactReserveAndKeepTheirGradualAccess() {
		int checked = 0;

		for (long seed = 20; seed < 36; seed++) {
			LapisDistrict body = body(seed);
			Optional<AbandonedMinePlan> planned = AbandonedMinePlanner.plan(body,
					LapisCatalogTest.defaults().abandonedMine(), 90);
			if (!planned.isPresent()) continue;

			Set<Long> ore = blocks(body, false);
			long removed = ore.stream().filter(planned.get().blocks()::containsKey).count();
			assertTrue(removed < ore.size() * 0.5, "Workings must leave a useful reserve: " + removed + "/" + ore.size());
			assertNotEquals(planned.get().entrance().x(), planned.get().landing().x());
			assertEquals(planned.get().blocks(), AbandonedMinePlanner.plan(body,
						LapisCatalogTest.defaults().abandonedMine(), 90).get().blocks());
			checked++;
		}

		assertTrue(checked > 4);
	}

	public static Set<Long> blocks(LapisDistrict body, boolean reverse) {
		BlockBounds bounds = body.bounds();
		List<int[]> chunks = new ArrayList<>();

		for (int x = Math.floorDiv(bounds.minimumX(), 16); x <= Math.floorDiv(bounds.maximumX(), 16); x++) {
			for (int z = Math.floorDiv(bounds.minimumZ(), 16); z <= Math.floorDiv(bounds.maximumZ(), 16); z++) {
				chunks.add(new int[] {x, z});
			}
		}

		if (reverse) Collections.reverse(chunks);
		Set<Long> ore = new HashSet<>();

		for (int[] chunk : chunks) {
			for (int x = chunk[0] * 16; x < chunk[0] * 16 + 16; x++) {
				for (int z = chunk[1] * 16; z < chunk[1] * 16 + 16; z++) {
					for (int y = 8; y <= 128; y++) {
						if (body.isOreAt(x, y, z)) ore.add(new MinePoint(x, y, z).packed());
					}
				}
			}
		}

		return ore;
	}
}
