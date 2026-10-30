package zone.moddev.mc.realisticdeposits.geometry;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import zone.moddev.mc.realisticdeposits.model.AbandonedMineSettings;

class GradualMineAccessTest {

	@Test
	void thePortalUsesItsOwnSurfaceHeightRatherThanTheRockAboveTheLanding() {
		java.util.function.ToIntBiFunction<Integer, Integer> surface = (x, z) -> 100 + Math.floorMod(x + z, 3);
		AbandonedMinePlan first = AbandonedMinePlanner.plan(body(), new AbandonedMineSettings(true, 1), surface).get();
		AbandonedMinePlan second = AbandonedMinePlanner.plan(body(), new AbandonedMineSettings(true, 1), surface).get();
		org.junit.jupiter.api.Assertions.assertEquals(surface.applyAsInt(first.entrance().x(), first.entrance().z()), first.entrance().y());
		org.junit.jupiter.api.Assertions.assertEquals(first.blocks(), second.blocks());
		assertFalse(AbandonedMinePlanner.plan(body(), new AbandonedMineSettings(true, 1), (x, z) -> -1).isPresent());
	}

	@Test
	void deepAccessUsesStepsRatherThanAVerticalLadderPit() {
		MineableDepositBody body = body();
		AbandonedMinePlan plan = AbandonedMinePlanner.plan(body, new AbandonedMineSettings(true, 1), 116).get();

		assertFalse(plan.blocks().containsValue(MineBlock.LADDER_WEST), "New access must not rely on a deep ladder");
		assertTrue(plan.blocks().values().stream().anyMatch(block -> block.name().startsWith("STAIRS_")));
		assertTrue(Math.abs(plan.entrance().x() - plan.landing().x())
				+ Math.abs(plan.entrance().z() - plan.landing().z()) > 12);

		for (int x = plan.bounds().minimumX(); x <= plan.bounds().maximumX(); x++) {
			for (int z = plan.bounds().minimumZ(); z <= plan.bounds().maximumZ(); z++) {
				int drop = 0;
				for (int y = plan.bounds().minimumY(); y <= plan.bounds().maximumY(); y++) {
					MineBlock block = plan.blocks().get(new MinePoint(x, y, z).packed());
					drop = block == MineBlock.AIR ? drop + 1 : 0;
					assertTrue(drop <= 8, "An unguarded vertical opening at " + x + "," + y + "," + z);
				}
			}
		}
	}

	static MineableDepositBody body() {
		return new MineableDepositBody() {
			@Override public DepositIdentity identity() { return new DepositIdentity(123, "minecraft:overworld", "test:deep", 0, 0, 1); }
			@Override public int centerX() { return 0; }
			@Override public int centerY() { return 20; }
			@Override public int centerZ() { return 0; }
			@Override public int strikeLength() { return 80; }
			@Override public BlockBounds bounds() { return new BlockBounds(-40, 18, -30, 40, 22, 30); }
			@Override public int minimumCandidateYAt(int x, int z) { return 18; }
			@Override public int maximumCandidateYAt(int x, int z) { return 22; }
			@Override public boolean isOreAt(int x, int y, int z) { return y == 20; }
			@Override public MinePoint miningRoutePoint(double along, double lateral) {
				return new MinePoint((int) Math.round(along), 20, (int) Math.round(lateral));
			}
		};
	}
}
