package zone.moddev.mc.realisticdeposits.geometry;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.InputStreamReader;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;

import org.junit.jupiter.api.Test;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import zone.moddev.mc.realisticdeposits.config.DepositCatalogLoader;
import zone.moddev.mc.realisticdeposits.model.AbandonedMineSettings;
import zone.moddev.mc.realisticdeposits.model.DepositDefinition;

class AbandonedMinePlannerTest {
	private static final long SEED = 3346071746553592696L;
	private static final AbandonedMineSettings ALWAYS = new AbandonedMineSettings(true, 1.0D);

	@Test
	void oldDefinitionsAndZeroChanceNeverProduceWorkings() {
		StratiformSeamBody body = body(-1, -1);

		assertFalse(AbandonedMinePlanner.plan(body, AbandonedMineSettings.DISABLED, 100).isPresent());
		assertFalse(AbandonedMinePlanner.plan(body, new AbandonedMineSettings(true, 0.0D), 100).isPresent());
	}

	@Test
	void chanceIsStablePerDepositRatherThanRolledOnceForEveryChunk() {
		AbandonedMineSettings quarter = new AbandonedMineSettings(true, 0.25D);
		int selected = 0;

		for (int x = -16; x < 16; x++) {
			for (int z = -8; z < 8; z++) {
				StratiformSeamBody body = body(x, z);
				boolean first = AbandonedMinePlanner.selected(body, quarter);
				assertEquals(first, AbandonedMinePlanner.selected(body(x, z), quarter));
				assertTrue(AbandonedMinePlanner.selected(body, ALWAYS));
				if (first) selected++;
			}
		}

		assertTrue(selected > 90 && selected < 170, "Expected roughly a quarter of 512 deposits, got " + selected);
	}

	@Test
	void networkIsBoundedConnectedAndHasAUsableSurfaceLadder() {
		StratiformSeamBody body = body(-1, -1);
		AbandonedMinePlan plan = AbandonedMinePlanner.plan(body, ALWAYS, 100).get();
		Set<Long> reachable = reachable(plan, plan.entrance());

		assertTrue(reachable.contains(plan.landing().packed()));
		assertTrue(plan.blocks().size() < AbandonedMinePlan.MAXIMUM_BLOCKS);
		assertTrue(plan.bounds().maximumX() - plan.bounds().minimumX() < 100);
		assertTrue(plan.bounds().maximumZ() - plan.bounds().minimumZ() < 100);
		assertTrue(plan.blocks().containsValue(MineBlock.COBWEB));
		assertTrue(plan.blocks().containsValue(MineBlock.FENCE));
		assertTrue(plan.blocks().containsValue(MineBlock.RAIL_EAST_WEST)
				|| plan.blocks().containsValue(MineBlock.RAIL_NORTH_SOUTH));

		for (Map.Entry<Long, MineBlock> cell : plan.blocks().entrySet()) {
			if (cell.getValue().passable()) {
				assertTrue(reachable.contains(cell.getKey()), "Disconnected tunnel cell " + MinePoint.unpack(cell.getKey()));
			}
		}

		for (int y = plan.landing().y(); y <= plan.entrance().y(); y++) {
			assertEquals(MineBlock.LADDER_WEST,
					plan.blocks().get(new MinePoint(plan.landing().x() + 1, y, plan.landing().z()).packed()));
			assertEquals(MineBlock.PLANKS,
					plan.blocks().get(new MinePoint(plan.landing().x() + 2, y, plan.landing().z()).packed()));
		}
	}

	@Test
	void chunkSlicesAreDisjointAndReassembleInAnyOrder() {
		AbandonedMinePlan plan = AbandonedMinePlanner.plan(body(-1, -1), ALWAYS, 100).get();
		List<Long> chunks = new ArrayList<>();

		for (int x = Math.floorDiv(plan.bounds().minimumX(), 16);
				x <= Math.floorDiv(plan.bounds().maximumX(), 16); x++) {
			for (int z = Math.floorDiv(plan.bounds().minimumZ(), 16);
					z <= Math.floorDiv(plan.bounds().maximumZ(), 16); z++) {
				chunks.add(MinePoint.chunkKey(x, z));
			}
		}

		for (int order = 0; order < 3; order++) {
			Collections.shuffle(chunks, new Random(order));
			Map<Long, MineBlock> combined = new LinkedHashMap<>();

			for (long key : chunks) {
				int x = (int) (key >> 32);
				int z = (int) key;
				for (AbandonedMinePlan.Cell cell : plan.cellsInChunk(x, z)) {
					assertEquals(x, Math.floorDiv(cell.point().x(), 16));
					assertEquals(z, Math.floorDiv(cell.point().z(), 16));
					assertFalse(combined.containsKey(cell.point().packed()));
					combined.put(cell.point().packed(), cell.block());
				}
			}

			assertEquals(plan.blocks(), combined);
		}
	}

	@Test
	void unsafeShaftDepthsAreRejected() {
		StratiformSeamBody body = body(-1, -1);
		MinePoint landing = AbandonedMinePlanner.landing(body);

		assertFalse(AbandonedMinePlanner.plan(body, ALWAYS, landing.y() + 3).isPresent());
		assertFalse(AbandonedMinePlanner.plan(body, ALWAYS, landing.y() + 97).isPresent());
		assertFalse(AbandonedMinePlanner.plan(body, ALWAYS, 253).isPresent());
	}

	@Test
	void coordinatePackingPreservesNegativeCoordinates() {
		for (int x : new int[] {-30000000, -1, 0, 30000000}) {
			for (int z : new int[] {-30000000, -1, 0, 30000000}) {
				MinePoint point = new MinePoint(x, 67, z);
				assertEquals(point, MinePoint.unpack(point.packed()));
			}
		}
	}

	private static Set<Long> reachable(AbandonedMinePlan plan, MinePoint start) {
		Set<Long> visited = new HashSet<>();
		ArrayDeque<MinePoint> queue = new ArrayDeque<>();
		queue.add(start);
		int[][] offsets = {{1,0,0}, {-1,0,0}, {0,1,0}, {0,-1,0}, {0,0,1}, {0,0,-1}};

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

	private static StratiformSeamBody body(int x, int z) {
		JsonObject catalog = new JsonParser().parse(new InputStreamReader(
				AbandonedMinePlannerTest.class.getResourceAsStream(
						"/assets/realisticdeposits/defaults/vanilla.json"), StandardCharsets.UTF_8)).getAsJsonObject();
		catalog.getAsJsonArray("deposits").get(0).getAsJsonObject().addProperty("selection_chance", 1.0D);
		DepositDefinition definition = DepositCatalogLoader.load(new StringReader(catalog.toString()), "test")
				.definitions().get(0);
		return RegionDepositPlanner.plan(definition, SEED, "minecraft:overworld", x, z).get();
	}
}
