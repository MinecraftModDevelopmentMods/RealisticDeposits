package zone.moddev.mc.realisticdeposits.generation;

import static org.junit.jupiter.api.Assertions.*;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import com.google.gson.JsonObject;
import org.junit.jupiter.api.Test;
import zone.moddev.mc.realisticdeposits.config.DepositCatalogLoader;
import zone.moddev.mc.realisticdeposits.geometry.AbandonedMinePlan;
import zone.moddev.mc.realisticdeposits.geometry.EmeraldDistrict;
import zone.moddev.mc.realisticdeposits.geometry.EmeraldMinePlanner;
import zone.moddev.mc.realisticdeposits.geometry.MinePoint;
import zone.moddev.mc.realisticdeposits.geometry.RichEmeraldMinePlanner;
import zone.moddev.mc.realisticdeposits.integration.OreSpawnIntegration;
import zone.moddev.mc.realisticdeposits.model.DepositDefinition;

public class EmeraldDepositTest {
	private static final String OVERWORLD = "minecraft:overworld";
	static final String ID = "realisticdeposits:overworld_emerald_pockets";
	private static final EmeraldDistrict.Site STONE = new EmeraldDistrict.Site() {
		@Override public boolean acceptsGeome(int x, int z) { return true; }
		@Override public boolean eligible(int x, int y, int z, int cover) { return y + cover <= 64; }
	};

	static DepositDefinition defaults() {
		return DepositCatalogLoader.load(new InputStreamReader(EmeraldDepositTest.class.getResourceAsStream(
				"/assets/realisticdeposits/defaults/emerald.json"), StandardCharsets.UTF_8),
				"emerald.json").definitions().get(0);
	}

	@Test
	void additiveCatalogueRoundTripsAndRegistersOnlyExactHosts() {
		DepositDefinition definition = defaults();
		assertEquals(ID, definition.id());
		assertEquals(0.35, definition.selectionChance());
		assertFalse(definition.effectiveBackgroundGenerationScale().isPresent());
		assertEquals(definition.structuralFingerprint(), DepositCatalogLoader.loadDefinition(
				definition.toJson(), ID).structuralFingerprint());
		JsonObject dimension = OreSpawnIntegration.provider(java.util.Collections.singletonList(definition))
				.toJson().getAsJsonObject("ores").getAsJsonObject("realisticdeposits:deposit/" + ID.replace(':', '/'))
				.getAsJsonObject("dimensions").getAsJsonObject(OVERWORLD);
		assertFalse(dimension.has("background_generation_scale"));
		assertEquals(4, dimension.getAsJsonArray("host_blocks").size());
		assertEquals(0, dimension.getAsJsonArray("host_families").size());
		JsonObject invalid = definition.toJson();
		invalid.addProperty("resource", "minecraft:diamond_ore");
		assertThrows(IllegalArgumentException.class, () -> DepositCatalogLoader.loadDefinition(invalid, ID));
	}

	@Test
	void hostStatesExcludeDecorativeAndFallbackRock() {
		assertTrue(EmeraldPattern.permittedRock("mineralogy:schist", 0, true));
		assertFalse(EmeraldPattern.permittedRock("mineralogy:schist", 1, true));
		assertFalse(EmeraldPattern.permittedRock("mineralogy:marble", 0, true));
		assertFalse(EmeraldPattern.permittedRock("minecraft:stone", 0, true));
		for (int meta = 0; meta <= 6; meta++) {
			assertEquals(meta == 0 || meta % 2 == 1, EmeraldPattern.permittedRock("minecraft:stone", meta, false));
		}
	}

	@Test
	void fitsSeparatedPocketsInNegativeRegionsWithoutChunkOrder() {
		DepositDefinition definition = defaults();
		int planned = 0;
		for (int rx = -40; rx < 0; rx++) {
			Optional<EmeraldDistrict> candidate = EmeraldDistrict.plan(definition, 19, OVERWORLD, rx, -1, STONE);
			if (!candidate.isPresent()) continue;
			planned++;
			EmeraldDistrict body = candidate.get();
			if (body.large()) continue; // Covered by the district-specific geometry test.
			assertEquals(body.ore(), EmeraldDistrict.plan(definition, 19, OVERWORLD, rx, -1, STONE).get().ore());
			assertTrue(body.rich() ? body.ore().size() >= 70 && body.ore().size() <= 120
					: body.ore().size() >= 25 && body.ore().size() <= 60);
			assertEquals(body.ore().size(), new HashSet<>(body.ore()).size());
			assertTrue(body.rich() ? body.pockets().size() >= 4 && body.pockets().size() <= 6
					: body.pockets().size() >= 2 && body.pockets().size() <= 4);
			assertTrue(body.ore().stream().allMatch(p -> p.y() >= 12 && p.y() <= 48 && body.isOreAt(p.x(), p.y(), p.z())));
			for (int i = 0; i < body.pockets().size(); i++) {
				for (List<MinePoint> other : body.pockets().subList(i + 1, body.pockets().size())) {
					for (MinePoint a : body.pockets().get(i)) for (MinePoint b : other) {
						assertTrue(Math.max(Math.abs(a.x() - b.x()),
								Math.max(Math.abs(a.y() - b.y()), Math.abs(a.z() - b.z()))) > 1);
					}
				}
			}
			assertFalse(EmeraldDistrict.plan(definition, 19, "minecraft:the_nether", rx, -1, STONE).isPresent());
		}
		assertTrue(planned >= 5);
	}

	@Test
	void rareFindsExtendTheTrendAndLeaveCapturedSmallFindsAlone() {
		DepositDefinition definition = defaults();
		JsonObject legacyJson = definition.toJson();
		JsonObject shape = legacyJson.getAsJsonObject("shape");
		shape.remove("rich_find");
		shape.remove("vein_trace");
		shape.remove("large_mine");
		DepositDefinition legacy = DepositCatalogLoader.loadDefinition(legacyJson, ID);
		assertFalse(legacy.toJson().getAsJsonObject("shape").has("rich_find"));
		assertFalse(legacy.toJson().getAsJsonObject("shape").has("vein_trace"));
		int total = 0, rich = 0;
		for (int rx = -160; rx < 160; rx++) {
			Optional<EmeraldDistrict> sample = EmeraldDistrict.plan(definition, 19, OVERWORLD, rx, -1, STONE);
			if (!sample.isPresent()) continue;
			total++;
			EmeraldDistrict body = sample.get();
			if (body.large()) continue;
			EmeraldDistrict old = EmeraldDistrict.plan(legacy, 19, OVERWORLD, rx, -1, STONE).get();
			if (body.rich()) {
				rich++;
				assertTrue(body.strikeLength() >= 52 && body.strikeLength() <= 80);
				assertTrue(body.ore().size() > old.ore().size());
				int[] anchor = {Integer.MAX_VALUE, Integer.MAX_VALUE};
				EmeraldDistrict.Site shortGeome = new EmeraldDistrict.Site() {
					@Override public boolean acceptsGeome(int x, int z) {
						if (anchor[0] == Integer.MAX_VALUE) { anchor[0] = x; anchor[1] = z; }
						return x == anchor[0] && z == anchor[1];
					}
					@Override public boolean eligible(int x, int y, int z, int cover) { return STONE.eligible(x, y, z, cover); }
				};
				EmeraldDistrict fallback = EmeraldDistrict.plan(definition, 19, OVERWORLD, rx, -1, shortGeome).get();
				assertFalse(fallback.rich());
				assertEquals(old.ore(), fallback.ore());
			} else {
				assertEquals(old.ore(), body.ore());
			}
			Set<MinePoint> trace = new HashSet<>();
			for (int x = body.bounds().minimumX() >> 4; x <= body.bounds().maximumX() >> 4; x++) {
				for (int z = body.bounds().minimumZ() >> 4; z <= body.bounds().maximumZ() >> 4; z++) {
					for (MinePoint point : body.traceInChunk(x, z)) {
						assertTrue(trace.add(point));
						assertEquals(x, point.x() >> 4);
						assertEquals(z, point.z() >> 4);
						assertFalse(body.isOreAt(point.x(), point.y(), point.z()));
					}
				}
			}
			assertTrue(trace.size() >= 5);
		}
		assertTrue(rich > 0 && rich * 4 < total,
				"Larger finds should be possible but unusual: " + rich + "/" + total);
	}

	@Test
	void largeDistrictsUseEmptyRegionsAndKeepEarlierTiersByteForByte() {
		DepositDefinition definition = defaults();
		JsonObject captured = definition.toJson();
		captured.getAsJsonObject("shape").remove("large_mine");
		DepositDefinition prior = DepositCatalogLoader.loadDefinition(captured, ID);
		assertNull(prior.emerald().large());
		assertEquals(captured, prior.toJson());
		JsonObject originalChance = definition.toJson();
		originalChance.getAsJsonObject("shape").getAsJsonObject("large_mine").addProperty("chance", 0.008);
		DepositDefinition rare = DepositCatalogLoader.loadDefinition(originalChance, ID);
		int small = 0, medium = 0, large = 0, workings = 0, reroutes = 0, retained = 0;
		for (long seed : new long[] {19L, -73L, 20261109L}) {
			for (int rx = -180; rx < 180; rx++) {
				Optional<EmeraldDistrict> before = EmeraldDistrict.plan(prior, seed, OVERWORLD, rx, -3, STONE);
				Optional<EmeraldDistrict> after = EmeraldDistrict.plan(definition, seed, OVERWORLD, rx, -3, STONE);
				if (before.isPresent()) {
					EmeraldDistrict old = before.get(), updated = after.get();
					assertEquals(old.pockets(), updated.pockets());
					assertEquals(old.strikeLength(), updated.strikeLength());
					assertEquals(old.rich(), updated.rich());
					assertFalse(updated.large());
					assertEquals(trace(old), trace(updated));
					if (old.rich()) medium++; else small++;
				} else if (after.isPresent()) {
					EmeraldDistrict body = after.get();
					assertTrue(body.large());
					Optional<EmeraldDistrict> previous = EmeraldDistrict.plan(rare, seed, OVERWORLD, rx, -3, STONE);
					if (previous.isPresent()) {
						assertTrue(previous.get().large());
						assertEquals(previous.get().pockets(), body.pockets());
						assertEquals(trace(previous.get()), trace(body));
						retained++;
					}
					assertTrue(body.zones() >= 4 && body.zones() <= 5);
					assertEquals(body.zones() * 2, body.pockets().size());
					assertTrue(body.ore().size() >= 260 && body.ore().size() <= 420);
					assertEquals(body.ore().size(), new HashSet<>(body.ore()).size());
					assertEquals(body.pockets(), EmeraldDistrict.plan(definition, seed, OVERWORLD, rx, -3, STONE).get().pockets());
					assertTrue(body.ore().stream().allMatch(p -> p.y() >= 12 && p.y() <= 48));
					assertTrue(body.ore().stream().allMatch(p -> Math.floorDiv(p.x(), 1024) == body.identity().regionX()
							&& Math.floorDiv(p.z(), 1024) == -3));
					assertCluesConnectZones(body);
					Optional<AbandonedMinePlan> mine = RichEmeraldMinePlanner.plan(body, definition.abandonedMine(),
							(a, b) -> 64, candidate -> true);
					if (mine.isPresent()) {
						assertEquals(1, mine.get().chunkKeys().size());
						assertTrue(body.ore().stream().noneMatch(p -> mine.get().blocks().containsKey(p.packed())));
						assertTrue(RichEmeraldMinePlanner.exposedPockets(body, mine.get(), p -> true) >= 2);
						MinePoint portal = mine.get().entrance();
						int[] considered = {0};
						Optional<AbandonedMinePlan> alternative = RichEmeraldMinePlanner.planInChunk(body,
								definition.abandonedMine(), portal.x() >> 4, portal.z() >> 4,
								(a, b) -> 64, candidate -> {
									considered[0]++;
									return !candidate.blocks().containsKey(portal.packed());
								});
						assertTrue(considered[0] > 0 && considered[0] <= 48);
						if (alternative.isPresent()) {
							assertEquals(mine.get().chunkKeys(), alternative.get().chunkKeys());
							assertFalse(alternative.get().blocks().containsKey(portal.packed()));
							assertTrue(RichEmeraldMinePlanner.exposedPockets(body, alternative.get(), p -> true) >= 2);
							reroutes++;
						}
						workings++;
					}
					large++;
				}
			}
		}
		// All-stone terrain removes the stricter multi-zone geology fit.
		// The actual rich/large ordering is checked on natural terrain.
		assertTrue(large > retained && large < small && medium < small);
		assertTrue(retained >= 2, "The earlier large-mine nominations must remain");
		assertTrue(workings > 0, "A district should fit a complete starter working in safe terrain");
		assertTrue(reroutes > 0, "A blocked starter route should have a safe in-chunk alternative at some sites");
	}

	private static Set<MinePoint> trace(EmeraldDistrict body) {
		Set<MinePoint> result = new HashSet<>();
		for (int x = body.bounds().minimumX() >> 4; x <= body.bounds().maximumX() >> 4; x++)
			for (int z = body.bounds().minimumZ() >> 4; z <= body.bounds().maximumZ() >> 4; z++)
				result.addAll(body.traceInChunk(x, z));
		return result;
	}

	private static void assertCluesConnectZones(EmeraldDistrict body) {
		Set<MinePoint> remaining = trace(body);
		assertFalse(remaining.isEmpty());
		assertTrue(java.util.Collections.disjoint(remaining, body.ore()));
		remaining.addAll(body.ore());
		Set<MinePoint> reached = new HashSet<>();
		ArrayDeque<MinePoint> queue = new ArrayDeque<>();
		queue.add(body.ore().get(0));
		int[][] directions = {{1,0,0},{-1,0,0},{0,1,0},{0,-1,0},{0,0,1},{0,0,-1}};
		while (!queue.isEmpty()) {
			MinePoint current = queue.removeFirst();
			if (!remaining.remove(current)) continue;
			reached.add(current);
			for (int[] d : directions) queue.addLast(new MinePoint(current.x() + d[0], current.y() + d[1], current.z() + d[2]));
		}
		for (int i = 0; i < body.pockets().size(); i += 2)
			assertTrue(body.pockets().get(i).stream().anyMatch(reached::contains), "Unconnected clue trail at zone " + (i / 2));
	}

	@Test
	void noGeomeOrHostRejectsWithoutSubstitution() {
		for (boolean geome : new boolean[] {false, true}) {
			EmeraldDistrict.Site rejected = new EmeraldDistrict.Site() {
				@Override public boolean acceptsGeome(int x, int z) { return geome; }
				@Override public boolean eligible(int x, int y, int z, int cover) {
					assertTrue(geome, "The planner must check geology before sampling pockets");
					return false;
				}
			};
			for (int rx = 0; rx < 16; rx++) {
				assertFalse(EmeraldDistrict.plan(defaults(), 19, OVERWORLD, rx, 0, rejected).isPresent());
			}
		}
	}

	@Test
	void linkedMineFitsOneChunkAndExposesMultiplePocketsWithoutCuttingOre() {
		DepositDefinition definition = defaults();
		assertTrue(definition.emerald().rich().workings());
		JsonObject old = definition.toJson();
		old.getAsJsonObject("shape").getAsJsonObject("rich_find").remove("workings");
		assertFalse(DepositCatalogLoader.loadDefinition(old, ID).emerald().rich().workings());
		int found = 0;
		for (int x = -50; x < 50; x++) {
			for (int z = -2; z <= 2; z++) {
				Optional<EmeraldDistrict> district = EmeraldDistrict.plan(definition, 19, OVERWORLD, x, z, STONE);
				if (!district.isPresent() || !district.get().rich()) continue;
				EmeraldDistrict body = district.get();
				Optional<AbandonedMinePlan> proposed = RichEmeraldMinePlanner.plan(body, definition.abandonedMine(),
						(a, b) -> 64, candidate -> true);
				if (!proposed.isPresent()) continue;
				AbandonedMinePlan mine = proposed.get();
				assertEquals(1, mine.chunkKeys().size());
				assertTrue(body.ore().stream().noneMatch(ore -> mine.blocks().containsKey(ore.packed())));
				assertTrue(RichEmeraldMinePlanner.exposedPockets(body, mine, p -> true) >= 2);
				assertEquals(0, RichEmeraldMinePlanner.exposedPockets(body, mine, p -> false));
				found++;
				if (found >= 2) return;
			}
		}
		assertTrue(found >= 2, "Expected distinct rich sites with linked workings");
	}

	@Test
	void workingsApproachAnOreFaceWithoutConsumingTheReserve() {
		DepositDefinition definition = defaults();
		int safe = 0;
		for (int rx = -20; rx < 20; rx++) {
			Optional<EmeraldDistrict> district = EmeraldDistrict.plan(definition, 19, OVERWORLD, rx, 0, STONE);
			if (!district.isPresent()) continue;
			EmeraldDistrict body = district.get();
			Optional<AbandonedMinePlan> candidate = EmeraldMinePlanner.plan(body, definition.abandonedMine(), (x, z) -> 64);
			assertFalse(EmeraldMinePlanner.plan(body, definition.abandonedMine(), (x, z) -> -1).isPresent());
			if (!candidate.isPresent()) continue;
			safe++;
			AbandonedMinePlan plan = candidate.get();
			assertEquals(plan.blocks().size(), plan.cellsInChunk(plan.entrance().x() >> 4, plan.entrance().z() >> 4).size());
			assertTrue(plan.blocks().keySet().stream().map(MinePoint::unpack).allMatch(point ->
					(point.x() & 15) > 0 && (point.x() & 15) < 15 && (point.z() & 15) > 0 && (point.z() & 15) < 15));
			Set<Long> written = plan.blocks().keySet();
			assertTrue(body.ore().stream().noneMatch(ore -> written.contains(ore.packed())));
			MinePoint landing = plan.landing();
			assertTrue(body.ore().stream().anyMatch(ore -> ore.y() == landing.y() + 1
					&& Math.abs(ore.x() - landing.x()) + Math.abs(ore.z() - landing.z()) >= 6
					&& Math.abs(ore.x() - landing.x()) + Math.abs(ore.z() - landing.z()) <= 12));
		}
		assertTrue(safe >= 5);
	}
}
