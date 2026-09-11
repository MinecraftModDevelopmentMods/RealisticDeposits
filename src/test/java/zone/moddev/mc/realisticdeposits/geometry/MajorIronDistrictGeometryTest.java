package zone.moddev.mc.realisticdeposits.geometry;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.junit.jupiter.api.Test;

import zone.moddev.mc.realisticdeposits.model.AlignmentMode;
import zone.moddev.mc.realisticdeposits.model.DepositDefinition;
import zone.moddev.mc.realisticdeposits.model.IntRange;
import zone.moddev.mc.realisticdeposits.model.InteractionPolicy;
import zone.moddev.mc.realisticdeposits.model.StratiformSeamSettings;

class MajorIronDistrictGeometryTest {
	private static final long SEED = 3346071746553592696L;

	@Test
	void atLeastTwentyFourSelectedBodiesHaveIrregularDistrictGeometry() {
		List<StratiformSeamBody> bodies = selectedBodies(24);
		assertEquals(24, bodies.size());
		for (StratiformSeamBody body : bodies) {
			assertTrue(body.strikeLength() >= 640 && body.strikeLength() <= 960);
			assertTrue(body.downDipWidth() >= 160 && body.downDipWidth() <= 240);
			assertTrue(body.splayCount() >= 5 && body.splayCount() <= 7);
			assertTrue(body.lensCount() >= 2 && body.lensCount() <= 3);
			assertTrue(body.faultCount() >= 2 && body.faultCount() <= 3);

			double smallestInterval = Double.POSITIVE_INFINITY;
			double largestInterval = 0.0D;
			for (int i = 1; i < body.splayCount(); i++) {
				double interval = body.splayAnchor(i) - body.splayAnchor(i - 1);
				smallestInterval = Math.min(smallestInterval, interval);
				largestInterval = Math.max(largestInterval, interval);
			}
			assertTrue(largestInterval - smallestInterval > body.strikeLength() * 0.025D,
					"splays collapsed into periodic spacing");
			for (int i = 0; i < body.splayCount(); i++) {
				assertTrue(body.splayLength(i) >= body.downDipWidth() * 0.20D,
						"splay did not meaningfully leave the trunk");
			}
			for (int i = 0; i < body.lensCount(); i++) {
				assertTrue(body.lensHalfLength(i) >= 8.0D);
				assertTrue(body.lensRecoveryDistance(i) <= 0.01D,
						"major lens lies beyond recoverable splay mineralization");
			}
		}
	}

	@Test
	void faultsHaveBoundedGapsAndModestOffsets() {
		for (StratiformSeamBody body : selectedBodies(24)) {
			for (int fault = 0; fault < body.faultCount(); fault++) {
				assertTrue(body.faultGap(fault) >= 4.0D && body.faultGap(fault) <= 12.0D);
				double lateral = Math.abs(body.faultLateralOffset(fault));
				double vertical = Math.abs(body.faultVerticalOffset(fault));
				assertTrue(lateral >= 3.0D && lateral <= 8.0D,
						"fault lateral offset was " + lateral);
				assertTrue(vertical >= 4.0D && vertical <= 12.0D,
						"fault vertical offset was " + vertical);
			}
		}
	}

	@Test
	void subparallelLensesFormBroadOreRunsConnectedToSplays() {
		StratiformSeamBody body = selectedBodies(1).get(0);
		for (int lens = 0; lens < body.lensCount(); lens++) {
			int occupiedSlices = 0;
			for (int step = -8; step <= 8; step++) {
				double along = body.lensCenterAlong(lens)
						+ body.lensHalfLength(lens) * step / 10.0D;
				int x = body.worldXAt(along, body.lensCenterAcross(lens));
				int z = body.worldZAt(along, body.lensCenterAcross(lens));
				for (int y = body.minimumCandidateYAt(x, z);
						y <= body.maximumCandidateYAt(x, z); y++) {
					if (body.isStructuralCoreAt(x, y, z)) {
						occupiedSlices++;
						break;
					}
				}
			}
			assertTrue(occupiedSlices >= 10,
					"subparallel lens did not form a long mineralized run");
		}
	}

	@Test
	void sparseHaloLeavesCluesInsideEveryFaultGap() {
		for (StratiformSeamBody body : selectedBodies(8)) {
			for (int fault = 0; fault < body.faultCount(); fault++) {
				int halo = 0;
				double halfGap = body.faultGap(fault) * 0.5D;
				for (double along = body.faultAlong(fault) - halfGap;
						along <= body.faultAlong(fault) + halfGap; along += 0.75D) {
					for (double across = -body.downDipWidth() * 0.42D;
							across <= body.downDipWidth() * 0.42D; across += 2.0D) {
						int x = body.worldXAt(along, across);
						int z = body.worldZAt(along, across);
						for (int y = body.minimumCandidateYAt(x, z);
								y <= body.maximumCandidateYAt(x, z); y++) {
							if (body.isOreAt(x, y, z) && !body.isStructuralCoreAt(x, y, z)) halo++;
						}
					}
				}
				assertTrue(halo > 0, "fault lacked recoverable halo mineralization");
			}
		}
	}

	@Test
	void verticallyFittableBodiesAreCentredInsideTheirConfiguredRange() {
		DepositDefinition shallow = definition(1.0D, 10.0D, 10.0D);
		for (int region = 0; region < 24; region++) {
			StratiformSeamBody body = RegionDepositPlanner.plan(shallow, SEED,
					"minecraft:overworld", region - 12, region % 7 - 3).get();
			assertFalse(body.verticallyTruncated(),
					"fittable body was unnecessarily truncated");
		}
	}

	@Test
	void localVerticalIntervalsExactlyMatchExhaustiveSampling() {
		DepositDefinition compact = compactDefinition();
		StratiformSeamBody body = RegionDepositPlanner.plan(compact, 918273645L,
				"minecraft:overworld", 0, 0).get();
		BlockBounds bounds = body.bounds();
		long exhaustiveSamples = 0L;
		long localSamples = 0L;
		for (int x = bounds.minimumX(); x <= bounds.maximumX(); x++) {
			for (int z = bounds.minimumZ(); z <= bounds.maximumZ(); z++) {
				int localMinimum = body.minimumCandidateYAt(x, z);
				int localMaximum = body.maximumCandidateYAt(x, z);
				if (localMinimum <= localMaximum) localSamples += localMaximum - localMinimum + 1L;
				for (int y = bounds.minimumY(); y <= bounds.maximumY(); y++) {
					exhaustiveSamples++;
					boolean localContains = y >= localMinimum && y <= localMaximum;
					if (body.isOreAt(x, y, z)) {
						assertTrue(localContains, "local Y interval omitted an ore block");
					}
				}
			}
		}
		assertTrue(localSamples * 4L < exhaustiveSamples,
				"local Y scan did not substantially reduce sampled blocks");
	}

	@Test
	void structuralLayoutAndOreMaskAreIndependentOfEvaluationOrder() {
		StratiformSeamBody body = selectedBodies(1).get(0);
		BlockBounds bounds = body.bounds();
		long forward = 0xcbf29ce484222325L;
		long reverse = 0xcbf29ce484222325L;
		for (int x = bounds.minimumX(); x <= bounds.maximumX(); x += 7) {
			for (int z = bounds.minimumZ(); z <= bounds.maximumZ(); z += 7) {
				forward = mixColumn(forward, body, x, z);
			}
		}
		List<Long> columns = new ArrayList<>();
		for (int x = bounds.minimumX(); x <= bounds.maximumX(); x += 7) {
			for (int z = bounds.minimumZ(); z <= bounds.maximumZ(); z += 7) {
				columns.add((((long) x) << 32) ^ (z & 0xffffffffL));
			}
		}
		Collections.reverse(columns);
		List<Long> masks = new ArrayList<>();
		for (long column : columns) {
			int x = (int) (column >> 32);
			int z = (int) column;
			masks.add(columnMask(body, x, z));
		}
		Collections.sort(masks);
		for (long mask : masks) {
			reverse ^= mask;
			reverse *= 0x100000001b3L;
		}
		List<Long> forwardMasks = new ArrayList<>();
		for (int x = bounds.minimumX(); x <= bounds.maximumX(); x += 7) {
			for (int z = bounds.minimumZ(); z <= bounds.maximumZ(); z += 7) {
				forwardMasks.add(columnMask(body, x, z));
			}
		}
		Collections.sort(forwardMasks);
		long normalizedForward = 0xcbf29ce484222325L;
		for (long mask : forwardMasks) {
			normalizedForward ^= mask;
			normalizedForward *= 0x100000001b3L;
		}
		assertEquals(normalizedForward, reverse);
		assertFalse(forward == 0L);
	}

	private static long mixColumn(long hash, StratiformSeamBody body, int x, int z) {
		hash ^= columnMask(body, x, z);
		return hash * 0x100000001b3L;
	}

	private static long columnMask(StratiformSeamBody body, int x, int z) {
		long mask = (((long) x) << 32) ^ (z & 0xffffffffL);
		for (int y = body.minimumCandidateYAt(x, z);
				y <= body.maximumCandidateYAt(x, z); y++) {
			if (body.isOreAt(x, y, z)) mask = StableHash.combine(mask, y);
		}
		return mask;
	}

	private static List<StratiformSeamBody> selectedBodies(int count) {
		DepositDefinition definition = definition(0.25D, 10.0D, 22.0D);
		List<StratiformSeamBody> result = new ArrayList<>();
		for (int ring = 0; ring < 40 && result.size() < count; ring++) {
			for (int x = -ring; x <= ring && result.size() < count; x++) {
				for (int z = -ring; z <= ring && result.size() < count; z++) {
					if (Math.max(Math.abs(x), Math.abs(z)) != ring) continue;
					RegionDepositPlanner.plan(definition, SEED, "minecraft:overworld", x, z)
							.ifPresent(result::add);
				}
			}
		}
		return result;
	}

	private static DepositDefinition definition(double chance, double minimumDip,
			double maximumDip) {
		return new DepositDefinition("realisticdeposits:overworld_iron_formation", true,
				"minecraft:iron_ore", AlignmentMode.MASSIVE_RARE, InteractionPolicy.HYBRID,
				Collections.singleton("minecraft:overworld"), Collections.singleton("minecraft:stone"),
				Collections.emptySet(), Collections.emptySet(), Collections.emptySet(),
				Collections.emptySet(), Collections.emptySet(), Collections.emptySet(), 0.15D,
				4, 120, 64, chance, 7146057694168187204L,
				new StratiformSeamSettings(new IntRange(640, 960), new IntRange(160, 240),
						3.0D, 8.0D, minimumDip, maximumDip, 32.0D, 240.0D,
						12.0D, 180.0D, 0.12D, 0.18D));
	}

	private static DepositDefinition compactDefinition() {
		return new DepositDefinition("realisticdeposits:test_iron", true,
				"minecraft:iron_ore", AlignmentMode.MASSIVE_RARE, InteractionPolicy.ADDITIVE,
				Collections.singleton("minecraft:overworld"), Collections.singleton("minecraft:stone"),
				8, 56, 16, 1.0D, 93475821L,
				new StratiformSeamSettings(new IntRange(80, 80), new IntRange(20, 20),
						2.0D, 3.0D, 12.0D, 12.0D, 4.0D, 64.0D,
						3.0D, 48.0D, 0.20D, 0.2D));
	}
}
