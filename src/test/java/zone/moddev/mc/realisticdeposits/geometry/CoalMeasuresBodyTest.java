package zone.moddev.mc.realisticdeposits.geometry;

import static org.junit.jupiter.api.Assertions.*;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;

import org.junit.jupiter.api.Test;

import zone.moddev.mc.realisticdeposits.config.DepositCatalogLoader;
import zone.moddev.mc.realisticdeposits.model.AbandonedMineSettings;
import zone.moddev.mc.realisticdeposits.model.DepositDefinition;

public class CoalMeasuresBodyTest {
	public static DepositDefinition definition() {
		return DepositCatalogLoader.load(new InputStreamReader(CoalMeasuresBodyTest.class
				.getResourceAsStream("/assets/realisticdeposits/defaults/coal.json"), StandardCharsets.UTF_8),
				"coal.json").definitions().get(0);
	}

	public static CoalMeasuresBody body() {
		DepositDefinition definition = definition();
		for (int rx = -10; rx <= 10; rx++) {
			java.util.Optional<CoalMeasuresBody> body = CoalMeasuresBody.plan(definition,
					3346071746553592696L, "minecraft:overworld", rx, -1);
			if (body.isPresent()) return body.get();
		}
		throw new AssertionError("No coalfield selected");
	}

	@Test
	void coalHasSeveralThinBedsSeparatedByRock() {
		CoalMeasuresBody body = body();
		assertTrue(body.bedCount() >= 2 && body.bedCount() <= 4);
		for (int bed = 0; bed < body.bedCount(); bed++) {
			assertTrue(body.bedThickness(bed) >= 1 && body.bedThickness(bed) <= 3);
			if (bed > 0) {
				double gap = body.bedOffset(bed) - body.bedThickness(bed) * 0.5D
						- body.bedOffset(bed - 1) - body.bedThickness(bed - 1) * 0.5D;
				assertTrue(gap >= 4 && gap <= 12, "Rock interval: " + gap);
			}
		}
	}

	@Test
	void foldingAndFaultThrowAreSharedByAllBeds() {
		CoalMeasuresBody body = body();
		for (int dx = -200; dx <= 200; dx += 4) {
			for (int bed = 1; bed < body.bedCount(); bed++) {
				assertEquals(body.bedOffset(bed) - body.bedOffset(0),
						body.bedY(bed, body.centerX() + dx, body.centerZ())
						- body.bedY(0, body.centerX() + dx, body.centerZ()), 0.000001D);
			}
		}
	}

	@Test
	void completeColumnsAndChunkSlicesAgreeInEitherOrder() {
		CoalMeasuresBody body = body();
		Set<Long> forward = sample(body, false);
		Set<Long> reverse = sample(body, true);
		assertFalse(forward.isEmpty());
		assertEquals(forward, reverse);
		assertTrue(forward.size() > 100000, "A coalfield should support a substantial mine");
		assertFalse(body.isOreAt(body.centerX(), 7, body.centerZ()));
		assertFalse(body.isOreAt(body.centerX(), 129, body.centerZ()));
	}

	@Test
	void principalBedCanUseTheExistingMinePlanner() {
		CoalMeasuresBody body = body();
		MinePoint landing = AbandonedMinePlanner.landing(body);
		assertTrue(body.isOreAt(landing.x(), landing.y(), landing.z())
				|| body.isOreAt(landing.x(), landing.y() - 1, landing.z()));
		assertTrue(AbandonedMinePlanner.plan(body, new AbandonedMineSettings(true, 1), landing.y() + 20).isPresent());
		assertFalse(AbandonedMinePlanner.selected(body, AbandonedMineSettings.DISABLED));
	}

	@Test
	void beddingGridIsImmutableAndMissingPackagesStayEmpty() {
		float[] heights = {40, 42, 40, 42};
		CoalMeasuresBody.BeddingGrid grid = new CoalMeasuresBody.BeddingGrid(0, 0, 16, 2, heights);
		heights[0] = 100;
		assertEquals(41, grid.heightAt(8, 8));
		assertTrue(Double.isNaN(grid.heightAt(-1, 0)));
		assertThrows(IllegalArgumentException.class,
				() -> new CoalMeasuresBody.BeddingGrid(0, 0, 0, 2, heights));
	}

	private static Set<Long> sample(CoalMeasuresBody body, boolean reverse) {
		Set<Long> result = new HashSet<>();
		BlockBounds bounds = body.bounds();
		int size = bounds.maximumX() - bounds.minimumX() + 1;
		for (int ix = 0; ix < size; ix++) {
			int x = reverse ? bounds.maximumX() - ix : bounds.minimumX() + ix;
			for (int z = bounds.minimumZ(); z <= bounds.maximumZ(); z++) {
				int min = body.minimumCandidateYAt(x, z);
				int max = body.maximumCandidateYAt(x, z);
				for (int y = min; y <= max; y++) {
					if (body.isOreAt(x, y, z)) result.add(new MinePoint(x, y, z).packed());
				}
			}
		}
		return result;
	}

	@Test
	void missingBeddingCannotFillTheRockBetweenOtherBeds() {
		CoalMeasuresBody body = body();
		CoalMeasuresBody.BeddingGrid[] grids = new CoalMeasuresBody.BeddingGrid[body.bedCount()];
		int allowed = 0;
		for (int bed = 0; bed < grids.length; bed++) {
			float y = bed == 0 ? Float.NaN : (float) (body.centerY() + body.bedOffset(bed));
			grids[bed] = new CoalMeasuresBody.BeddingGrid(body.centerX() - 16, body.centerZ() - 16,
					32, 2, new float[] {y, y, y, y});
			if (bed > 0) allowed += body.bedThickness(bed);
		}
		CoalMeasuresBody fitted = body.withBedding(grids);
		int actual = 0;
		for (int y = 8; y <= 128; y++) {
			if (fitted.isOreAt(body.centerX(), y, body.centerZ())) actual++;
		}
		assertTrue(actual <= allowed, "A missing package must stay empty, not become a solid coal column");
	}

	@Test
	void thickerBedsHaveLocalRockPartingsAndEdgesThinGradually() {
		DepositDefinition definition = definition();
		for (int rx = -10; rx <= 10; rx++) {
			Optional<CoalMeasuresBody> planned = CoalMeasuresBody.plan(definition,
					3346071746553592696L, "minecraft:overworld", rx, -1);
			if (!planned.isPresent()) continue;
			CoalMeasuresBody body = planned.get();
			for (int bed = 0; bed < body.bedCount(); bed++) {
				if (body.bedThickness(bed) != 3) continue;
				MinePoint split = body.miningRoutePoint(0, body.downDipWidth() * 0.18D);
				double centre = body.bedY(bed, split.x(), split.z());
				int rock = (int) Math.floor(centre);
				assertFalse(body.isOreAt(split.x(), rock, split.z()), "The parting must be rock");
				assertTrue(body.isOreAt(split.x(), rock - 1, split.z()));
				assertTrue(body.isOreAt(split.x(), rock + 1, split.z()));

				MinePoint middle = body.miningRoutePoint(0, 0);
				MinePoint edge = body.miningRoutePoint(0, body.downDipWidth() * 0.48D);
				assertTrue(columnCoal(body, middle) > columnCoal(body, edge));
				return;
			}
		}
		fail("The deterministic fixture search needs a three-block bed");
	}

	private static int columnCoal(CoalMeasuresBody body, MinePoint point) {
		int coal = 0;
		for (int y = 8; y <= 128; y++) {
			if (body.isOreAt(point.x(), y, point.z())) coal++;
		}
		return coal;
	}
}
