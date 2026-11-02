package zone.moddev.mc.realisticdeposits.geometry;

import java.util.HashSet;
import java.util.Optional;
import java.util.Set;

import org.junit.jupiter.api.Test;

import zone.moddev.mc.realisticdeposits.config.GoldCatalogTest;
import zone.moddev.mc.realisticdeposits.model.DepositDefinition;

import static org.junit.jupiter.api.Assertions.*;

public class GoldDistrictTest {
	public static final GoldDistrict.Site MOUNTAIN = site("orespawn:mountain_belt", 80);

	public static GoldDistrict.Site site(String geome, int height) {
		return new GoldDistrict.Site() {
			@Override public int surfaceY(int x, int z) { return height; }
			@Override public String geome(int x, int z, int surface) { return geome; }
		};
	}

	public static GoldDistrict body(long seed) {
		DepositDefinition definition = GoldCatalogTest.defaults().get(0);
		for (int region = -16; region <= 16; region++) {
			Optional<GoldDistrict> planned = GoldDistrict.plan(definition, seed, "minecraft:overworld",
					region, -2, MOUNTAIN);
			if (planned.isPresent()) return planned.get();
		}
		throw new AssertionError("No gold district in fixture search");
	}

	@Test
	void repeatsTheSameDistrictWithSteepThinVeins() {
		for (long seed = 10; seed < 18; seed++) {
			GoldDistrict body = body(seed);
			GoldDistrict again = body(seed);
			assertEquals(body.identity().seed(), again.identity().seed());
			assertEquals(body.centerX(), again.centerX());
			assertEquals(body.centerZ(), again.centerZ());
			assertTrue(body.veins().size() >= 2 && body.veins().size() <= 4);
			assertTrue(body.bounds().minimumY() >= 8);
			assertTrue(body.bounds().maximumY() <= 80 - body.surfaceCover());
			for (GoldDistrict.Vein vein : body.veins()) {
				assertTrue(vein.width() >= 1 && vein.width() <= 3);
				assertTrue(vein.dipDegrees() >= 55 && vein.dipDegrees() <= 85);
				assertTrue(vein.length() >= 256 && vein.length() <= 512);
			}
			assertThrows(UnsupportedOperationException.class, () -> body.veins().clear());
		}
	}

	@Test
	void geologyAndHeightRejectUnsuitableSitesWithoutChangingSelection() {
		DepositDefinition definition = GoldCatalogTest.defaults().get(0);
		for (int region = 0; region < 32; region++) {
			assertFalse(GoldDistrict.plan(definition, 17, "minecraft:overworld", region, 0,
					site("orespawn:sedimentary_basin", 80)).isPresent());
			assertFalse(GoldDistrict.plan(definition, 17, "minecraft:overworld", region, 0,
					site("orespawn:mountain_belt", 20)).isPresent());
			assertFalse(GoldDistrict.plan(definition, 17, "minecraft:the_nether", region, 0, MOUNTAIN).isPresent());
		}
	}

	@Test
	void legacyApproximationIsExplicitRarerAndDeterministic() {
		DepositDefinition definition = GoldCatalogTest.defaults().get(0);
		int mountain = 0;
		int legacy = 0;
		for (int region = 0; region < 256; region++) {
			if (GoldDistrict.plan(definition, 57, "minecraft:overworld", region, 0, MOUNTAIN).isPresent()) mountain++;
			Optional<GoldDistrict> cyano = GoldDistrict.plan(definition, 57, "minecraft:overworld",
					region, 0, site("orespawn:cyano", 80));
			Optional<GoldDistrict> absent = GoldDistrict.plan(definition, 57, "minecraft:overworld",
					region, 0, site(null, 80));
			assertEquals(cyano.isPresent(), absent.isPresent());
			if (cyano.isPresent()) legacy++;
		}
		assertTrue(legacy > 0 && legacy < mountain / 2);
	}

	@Test
	void narrowColumnBoundsMatchAnExhaustiveSliceAndContainRichAndLeanSections() {
		GoldDistrict body = body(71);
		Set<MinePoint> exhaustive = new HashSet<>();
		Set<MinePoint> bounded = new HashSet<>();
		int richSections = 0;
		int leanSections = 0;

		for (int along = -120; along <= 120; along += 3) {
			int section = 0;
			for (int height = body.centerY() - 16; height <= body.centerY() + 16; height++) {
				MinePoint point = body.pointOnVein(0, along, height);
				for (int dx = -2; dx <= 2; dx++) {
					for (int dz = -2; dz <= 2; dz++) {
						int x = point.x() + dx;
						int z = point.z() + dz;
						if (body.isOreAt(x, height, z)) {
							MinePoint ore = new MinePoint(x, height, z);
							exhaustive.add(ore);
							section++;
							if (height >= body.minimumCandidateYAt(x, z) && height <= body.maximumCandidateYAt(x, z)) {
								bounded.add(ore);
							}
						}
					}
				}
			}
			if (section > 40) richSections++;
			if (section < 8) leanSections++;
		}
		assertEquals(exhaustive, bounded);
		assertTrue(exhaustive.size() > 1000, "The lode needs productive mining sections");
		assertTrue(richSections > 0 && leanSections > 0, "Gold must form shoots, not a solid wall");
	}

	@Test
	void placerPatchesAreStableAndDoNotCoverEveryRiverColumn() {
		int accepted = 0;
		for (int x = -32; x < 32; x++) {
			for (int z = -32; z < 32; z++) {
				boolean selected = GoldDistrict.placerPocket(812, x, z, 0, 0.35);
				assertEquals(selected, GoldDistrict.placerPocket(812, x, z, 0, 0.35));
				if (selected) accepted++;
			}
		}
		assertTrue(accepted > 100 && accepted < 2500);
	}
}
