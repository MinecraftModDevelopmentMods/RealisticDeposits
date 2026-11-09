package zone.moddev.mc.realisticdeposits.geometry;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Predicate;
import java.util.function.ToIntBiFunction;

import zone.moddev.mc.realisticdeposits.model.AbandonedMineSettings;

/** Extends compact access with timbered, level headings to two or three pockets. */
public final class RichEmeraldMinePlanner {
	private static final int[][] SIDES = {{1, 0}, {0, 1}, {-1, 0}, {0, -1}};
	private static final int MAX_GALLERY = 64;

	private RichEmeraldMinePlanner() { }

	public static Optional<AbandonedMinePlan> plan(EmeraldDistrict district, AbandonedMineSettings settings,
			ToIntBiFunction<Integer, Integer> portalSurface, Predicate<AbandonedMinePlan> safe) {
		return plan(district, settings, portalSurface, level -> true, new CandidateChecks(safe, Integer.MAX_VALUE));
	}

	/** Recheck a bounded number of alternatives inside the already nominated fresh chunk. */
	public static Optional<AbandonedMinePlan> planInChunk(EmeraldDistrict district, AbandonedMineSettings settings,
			int chunkX, int chunkZ, ToIntBiFunction<Integer, Integer> portalSurface, Predicate<AbandonedMinePlan> safe) {
		return plan(district, settings, portalSurface, level -> (level.x() >> 4) == chunkX && (level.z() >> 4) == chunkZ,
				new CandidateChecks(safe, 48));
	}

	private static Optional<AbandonedMinePlan> plan(EmeraldDistrict district, AbandonedMineSettings settings,
			ToIntBiFunction<Integer, Integer> portalSurface, Predicate<MinePoint> eligibleLevel, CandidateChecks checks) {
		if (!(district.rich() || district.large()) || !AbandonedMinePlanner.selected(district, settings)) return Optional.empty();
		// Nominate only chunk levels where two distinct pockets could share a gallery.
		Map<MinePoint, Integer> levels = new LinkedHashMap<>();
		for (List<MinePoint> pocket : district.pockets()) {
			Set<MinePoint> faces = new LinkedHashSet<>();
			for (MinePoint ore : pocket) {
				int x = (ore.x() >> 4) << 4, z = (ore.z() >> 4) << 4;
				faces.add(new MinePoint(x, ore.y() - 1, z));
				faces.add(new MinePoint(x, ore.y(), z));
			}
			for (MinePoint face : faces) levels.merge(face, 1, Integer::sum);
		}
		for (Map.Entry<MinePoint, Integer> level : levels.entrySet()) {
			if (checks.remaining == 0) break;
			if (level.getValue() < 2 || !eligibleLevel.test(level.getKey())) continue;
			Optional<AbandonedMinePlan> plan = access(district, level.getKey(), portalSurface, checks);
			if (plan.isPresent()) return plan;
		}
		return Optional.empty();
	}

	private static Optional<AbandonedMinePlan> access(EmeraldDistrict body, MinePoint level,
			ToIntBiFunction<Integer, Integer> portalSurface, CandidateChecks checks) {
		int firstDirection = (int) (body.identity().seed() & 3);
		int[][] heights = new int[13][13];
		for (int x = 3; x <= 12; x++) for (int z = 3; z <= 12; z++) {
			heights[x][z] = portalSurface.applyAsInt(level.x() + x, level.z() + z);
		}
		// Project compact flights back from an eligible portal. Landings and
		// headings are free to turn, unlike the small-find straight crosscut.
		for (int x = 3; x <= 12; x++) for (int z = 3; z <= 12; z++) {
			int surface = heights[x][z];
			if (surface < level.y() + 6 || surface > 245
					|| surface - level.y() > AbandonedMinePlanner.MAXIMUM_SHAFT_DEPTH) continue;
			for (int span : new int[] {8, 6}) for (int attempt = 0; attempt < 4; attempt++) {
				int forward = (firstDirection + attempt) & 3;
				for (int turn : new int[] {1, 3}) {
					MinePoint origin = new MinePoint(0, level.y(), 0);
					MinePoint relativePortal = MineDecline.compact(origin, surface, forward, turn, span).entrance();
					int lx = x - relativePortal.x(), lz = z - relativePortal.z();
					int across = (forward + turn) & 3;
					int farX = lx + (SIDES[forward][0] + SIDES[across][0]) * span;
					int farZ = lz + (SIDES[forward][1] + SIDES[across][1]) * span;
					if (!interior(lx) || !interior(lz) || !interior(farX) || !interior(farZ)) continue;
					MinePoint landing = new MinePoint(level.x() + lx, level.y(), level.z() + lz);
					if (body.isOreAt(landing.x(), landing.y(), landing.z())) continue;
					MineDecline stairs = MineDecline.compact(landing, surface, forward, turn, span);
					Map<Long, MineBlock> blocks = new LinkedHashMap<>();
					stairs.build(blocks);
					if (!AbandonedMinePlanner.sealClosedPockets(blocks, stairs.entrance(), landing)
							|| !stairs.hasClearSteps(blocks) || !validAccess(body, landing, blocks)) continue;
					AbandonedMinePlan linked = extend(body, new AbandonedMinePlan(body.identity().seed(),
							stairs.entrance(), landing, blocks));
					if (linked != null) {
						if (checks.accept(linked)) return Optional.of(linked);
						if (checks.remaining == 0) return Optional.empty();
					}
				}
			}
		}
		return Optional.empty();
	}

	private static final class CandidateChecks {
		private final Predicate<AbandonedMinePlan> safe;
		private int remaining;

		private CandidateChecks(Predicate<AbandonedMinePlan> safe, int remaining) {
			this.safe = safe;
			this.remaining = remaining;
		}

		private boolean accept(AbandonedMinePlan plan) {
			remaining--;
			return safe.test(plan);
		}
	}

	private static boolean interior(int local) { return local >= 3 && local <= 12; }

	private static boolean validAccess(EmeraldDistrict body, MinePoint landing, Map<Long, MineBlock> blocks) {
		for (long packed : blocks.keySet()) {
			MinePoint point = MinePoint.unpack(packed);
			int x = point.x() & 15, z = point.z() & 15;
			if (!sameChunk(point, landing) || x == 0 || x == 15 || z == 0 || z == 15
					|| body.isOreAt(point.x(), point.y(), point.z())) return false;
		}
		return true;
	}

	private static AbandonedMinePlan extend(EmeraldDistrict body, AbandonedMinePlan base) {
		MinePoint landing = base.landing();
		int y = landing.y();
		Map<Long, MineBlock> blocks = new LinkedHashMap<>(base.blocks());
		Predicate<MinePoint> inMineChunk = ore -> sameChunk(ore, landing);
		int length = 0;
		while (length < MAX_GALLERY) {
			Set<Long> floor = reachableFloor(blocks, landing);
			if (exposedPockets(body, floor, y, inMineChunk) >= 3) break;
			Set<Long> targets = new LinkedHashSet<>();
			for (List<MinePoint> pocket : body.pockets()) {
				if (exposes(pocket, floor, y, inMineChunk)) continue;
				for (MinePoint ore : pocket) {
					if (!sameChunk(ore, landing) || ore.y() != y && ore.y() != y + 1) continue;
					for (int[] side : SIDES) {
						targets.add(new MinePoint(ore.x() + side[0], y, ore.z() + side[1]).packed());
					}
				}
			}
			List<MinePoint> route = findRoute(body, landing, blocks, floor, targets, MAX_GALLERY - length);
			if (route.isEmpty()) break;
			for (MinePoint step : route) {
				put(blocks, step, -1, MineBlock.PLANKS);
				put(blocks, step, 0, MineBlock.AIR);
				put(blocks, step, 1, MineBlock.AIR);
				put(blocks, step, 2, ++length % 7 == 0 ? MineBlock.PLANKS : MineBlock.AIR);
			}
		}
		if (length == 0 || exposedPockets(body, reachableFloor(blocks, landing), y, inMineChunk) < 2) return null;
		return new AbandonedMinePlan(base.identity(), base.entrance(), base.landing(), blocks);
	}

	private static void put(Map<Long, MineBlock> blocks, MinePoint step, int dy, MineBlock block) {
		blocks.putIfAbsent(new MinePoint(step.x(), step.y() + dy, step.z()).packed(), block);
	}

	private static List<MinePoint> findRoute(EmeraldDistrict body, MinePoint landing,
			Map<Long, MineBlock> blocks, Set<Long> floor, Set<Long> targets, int maximum) {
		ArrayDeque<Long> queue = new ArrayDeque<>(floor);
		Map<Long, Long> parent = new LinkedHashMap<>();
		for (long root : floor) parent.put(root, root);
		int depth = 0;
		while (!queue.isEmpty() && depth <= maximum && !targets.isEmpty()) {
			for (int width = queue.size(); width > 0; width--) {
				long here = queue.removeFirst();
				if (targets.contains(here)) {
					List<MinePoint> path = new ArrayList<>();
					for (long cursor = here; parent.get(cursor) != cursor; cursor = parent.get(cursor)) {
						path.add(MinePoint.unpack(cursor));
					}
					Collections.reverse(path);
					return path;
				}
				if (depth == maximum) continue;
				MinePoint point = MinePoint.unpack(here);
				for (int[] side : SIDES) {
					int x = point.x() + side[0], z = point.z() + side[1];
					MinePoint next = new MinePoint(x, point.y(), z);
					// Leave the existing one-block safety buffer at the chunk boundary.
					if (!sameChunk(next, landing) || (x & 15) == 0 || (x & 15) == 15
							|| (z & 15) == 0 || (z & 15) == 15) continue;
					if (!parent.containsKey(next.packed()) && canRoute(body, blocks, next)) {
						parent.put(next.packed(), here);
						queue.addLast(next.packed());
					}
				}
			}
			depth++;
		}
		return Collections.emptyList();
	}

	private static Set<Long> reachableFloor(Map<Long, MineBlock> blocks, MinePoint landing) {
		Set<Long> floor = new LinkedHashSet<>();
		ArrayDeque<MinePoint> queue = new ArrayDeque<>();
		floor.add(landing.packed());
		queue.add(landing);
		while (!queue.isEmpty()) {
			MinePoint here = queue.removeFirst();
			int y = here.y();
			for (int[] side : SIDES) {
				int x = here.x() + side[0], z = here.z() + side[1];
				MinePoint p = new MinePoint(x, y, z);
				if (blocks.get(new MinePoint(x, y - 1, z).packed()) == MineBlock.PLANKS
						&& blocks.get(p.packed()) == MineBlock.AIR
						&& blocks.get(new MinePoint(x, y + 1, z).packed()) == MineBlock.AIR
						&& floor.add(p.packed())) queue.addLast(p);
			}
		}
		return floor;
	}

	private static boolean sameChunk(MinePoint p, MinePoint q) {
		return (p.x() >> 4) == (q.x() >> 4) && (p.z() >> 4) == (q.z() >> 4);
	}

	private static boolean canRoute(EmeraldDistrict body, Map<Long, MineBlock> blocks, MinePoint p) {
		for (int dy = -1; dy <= 2; dy++) {
			int y = p.y() + dy;
			if (body.isOreAt(p.x(), y, p.z())) return false;
			MineBlock existing = blocks.get(new MinePoint(p.x(), y, p.z()).packed());
			if (existing != null && (dy == -1 ? existing != MineBlock.PLANKS
					: dy < 2 ? existing != MineBlock.AIR : existing != MineBlock.AIR && existing != MineBlock.PLANKS)) {
				return false;
			}
		}
		return true;
	}

	public static int exposedPockets(EmeraldDistrict body, AbandonedMinePlan plan, Predicate<MinePoint> orePresent) {
		return exposedPockets(body, reachableFloor(plan.blocks(), plan.landing()), plan.landing().y(), orePresent);
	}

	private static int exposedPockets(EmeraldDistrict body, Set<Long> floor, int y,
			Predicate<MinePoint> orePresent) {
		int count = 0;
		for (List<MinePoint> pocket : body.pockets()) if (exposes(pocket, floor, y, orePresent)) count++;
		return count;
	}

	private static boolean exposes(List<MinePoint> pocket, Set<Long> floor, int y,
			Predicate<MinePoint> orePresent) {
		for (MinePoint ore : pocket) {
			if (ore.y() != y && ore.y() != y + 1) continue;
			for (int[] side : SIDES) {
				int x = ore.x() + side[0], z = ore.z() + side[1];
				if (floor.contains(new MinePoint(x, y, z).packed()) && orePresent.test(ore)) return true;
			}
		}
		return false;
	}
}
