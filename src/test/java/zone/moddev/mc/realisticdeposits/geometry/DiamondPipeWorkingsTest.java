package zone.moddev.mc.realisticdeposits.geometry;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayDeque;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.Test;

import zone.moddev.mc.realisticdeposits.DiamondFixtures;
import zone.moddev.mc.realisticdeposits.model.AbandonedMineSettings;

class DiamondPipeWorkingsTest {
	private static final AbandonedMineSettings ALWAYS = new AbandonedMineSettings(true, 1.0D);
	private static final DiamondDistrict.Site SITE = new DiamondDistrict.Site() {
		@Override public int surfaceY(int x, int z) { return 80; }
		@Override public String geome(int x, int z, int y) { return "orespawn:stable_craton"; }
	};

	@Test
	void eachPipeHasItsOwnStableConnectedWorkings() {
		int checked = 0;
		boolean timberSupports = false;
		boolean cobwebs = false;

		for (int region = -8; region < 8; region++) {
			java.util.Optional<DiamondDistrict> selected = DiamondDistrict.plan(DiamondFixtures.pipe(),
					3346071746553592696L, "minecraft:overworld", region, -3, SITE);
			if (!selected.isPresent()) continue;

			DiamondDistrict district = selected.get();
			Set<String> keys = new HashSet<>();

			for (int index = 0; index < district.pipes().size(); index++) {
				DiamondPipeWorkingsBody body = new DiamondPipeWorkingsBody(district, index);
				AbandonedMinePlan plan = AbandonedMinePlanner.plan(body, ALWAYS, 80).get();
				AbandonedMinePlan repeated = AbandonedMinePlanner.plan(
						new DiamondPipeWorkingsBody(district, index), ALWAYS, 80).get();

				assertTrue(keys.add(body.identity().toString()), "Two pipes shared a mine identity");
				assertEquals(plan.blocks(), repeated.blocks());
				assertTrue(district.pipes().get(index).inside(plan.landing().x(),
						plan.landing().y(), plan.landing().z()));
				assertTrue(plan.entrance().y() - plan.landing().y() >= 6);
				assertFalse(plan.blocks().containsValue(MineBlock.LADDER_WEST));
				assertTrue(plan.blocks().values().stream().anyMatch(block -> block.name().startsWith("STAIRS_")));
				assertTrue(plan.blocks().containsValue(MineBlock.PLANKS));
				timberSupports |= plan.blocks().containsValue(MineBlock.FENCE);
				cobwebs |= plan.blocks().containsValue(MineBlock.COBWEB);

				Set<Long> reachable = reachable(plan);
				assertTrue(reachable.contains(plan.landing().packed()));
				int minedOre = 0;

				for (Map.Entry<Long, MineBlock> cell : plan.blocks().entrySet()) {
					if (cell.getValue().passable()) assertTrue(reachable.contains(cell.getKey()),
							"Disconnected " + cell.getValue() + " at " + MinePoint.unpack(cell.getKey()) + " in " + body.identity());
					MinePoint point = MinePoint.unpack(cell.getKey());
					if (body.isOreAt(point.x(), point.y(), point.z())) minedOre++;
				}

				assertTrue(minedOre > 0, "The workings did not reach the pipe's diamond-bearing rock");
				assertTrue(minedOre < 600, "The access should not excavate the whole pipe");
				assertFalse(AbandonedMinePlanner.plan(body, AbandonedMineSettings.DISABLED, 80).isPresent());
				checked++;
			}
		}

		assertTrue(checked >= 8);
		assertTrue(timberSupports);
		assertTrue(cobwebs);
	}

	private static Set<Long> reachable(AbandonedMinePlan plan) {
		Set<Long> visited = new HashSet<>();
		ArrayDeque<MinePoint> queue = new ArrayDeque<>();
		queue.add(plan.entrance());
		int[][] offsets = {{1, 0, 0}, {-1, 0, 0}, {0, 1, 0}, {0, -1, 0}, {0, 0, 1}, {0, 0, -1}};

		while (!queue.isEmpty()) {
			MinePoint point = queue.remove();
			MineBlock block = plan.blocks().get(point.packed());
			if (block == null || !block.passable() || !visited.add(point.packed())) continue;

			for (int[] offset : offsets) {
				queue.add(new MinePoint(point.x() + offset[0], point.y() + offset[1], point.z() + offset[2]));
			}
		}

		return visited;
	}

	@Test
	void rimRouteKeepsThePipeButExposesLessOfItsOre() {
		int checked = 0, legacyOre = 0, rimOre = 0;
		for (int region = -8; region < 8; region++) {
			java.util.Optional<DiamondDistrict> picked = DiamondDistrict.plan(DiamondFixtures.pipe(),
					3346071746553592696L, "minecraft:overworld", region, -3, SITE);
			if (!picked.isPresent()) continue;
			DiamondDistrict district = picked.get();
			for (int i = 0; i < district.pipes().size(); i++) {
				DiamondPipeWorkingsBody legacy = new DiamondPipeWorkingsBody(district, i);
				DiamondPipeWorkingsBody rim = new DiamondPipeWorkingsBody(district, i, true);
				AbandonedMinePlan a = AbandonedMinePlanner.plan(legacy, ALWAYS, 80).get();
				AbandonedMinePlan b = AbandonedMinePlanner.plan(rim, ALWAYS, 80).get();
				assertEquals(legacy.identity().toString(), rim.identity().toString());
				assertEquals(b.blocks(), AbandonedMinePlanner.plan(rim, ALWAYS, 80).get().blocks());
				assertTrue(reachable(b).contains(b.landing().packed()));
				legacyOre += exposed(a, legacy);
				rimOre += exposed(b, rim);
				checked++;
			}
		}
		assertTrue(checked >= 8 && rimOre > 0 && rimOre * 2 < legacyOre,
				"Rim routes should offer a clue without exposing the core: " + rimOre + " vs " + legacyOre);
	}

	private static int exposed(AbandonedMinePlan plan, DiamondPipeWorkingsBody body) {
		Set<Long> ore = new HashSet<>();
		for (long packed : reachable(plan)) {
			MinePoint p = MinePoint.unpack(packed);
			for (int[] d : new int[][] {{1,0,0}, {-1,0,0}, {0,1,0}, {0,-1,0}, {0,0,1}, {0,0,-1}}) {
				MinePoint q = new MinePoint(p.x()+d[0], p.y()+d[1], p.z()+d[2]);
				if (!plan.blocks().containsKey(q.packed()) && body.isOreAt(q.x(),q.y(),q.z())) ore.add(q.packed());
			}
		}
		return ore.size();
	}
}
