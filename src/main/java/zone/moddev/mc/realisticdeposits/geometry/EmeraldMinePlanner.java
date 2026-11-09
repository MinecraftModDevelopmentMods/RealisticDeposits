package zone.moddev.mc.realisticdeposits.geometry;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Predicate;
import java.util.function.ToIntBiFunction;

import zone.moddev.mc.realisticdeposits.model.AbandonedMineSettings;

/** A single short approach to the edge of an emerald pocket. */
public final class EmeraldMinePlanner {
	private static final int[][] DIRECTIONS = {{1, 0}, {0, 1}, {-1, 0}, {0, -1}};

	private EmeraldMinePlanner() { }

	public static Optional<AbandonedMinePlan> plan(EmeraldDistrict body, AbandonedMineSettings settings,
			ToIntBiFunction<Integer, Integer> surface) {
		return plan(body, settings, surface, candidate -> true);
	}

	public static Optional<AbandonedMinePlan> plan(EmeraldDistrict body, AbandonedMineSettings settings,
			ToIntBiFunction<Integer, Integer> surface, Predicate<AbandonedMinePlan> terrain) {
		if (!AbandonedMinePlanner.selected(body, settings)) return Optional.empty();
		int lengths = 7;
		int firstDirection = (int) (body.identity().seed() & 3);
		for (List<MinePoint> pocket : body.pockets()) {
			for (int direction = 0; direction < DIRECTIONS.length; direction++) {
				int[] step = DIRECTIONS[(firstDirection + direction) % DIRECTIONS.length];
				int dx = step[0], dz = step[1];
				Map<Long, MinePoint> edges = new LinkedHashMap<>();
				for (MinePoint point : pocket) {
					long row = MinePoint.chunkKey(point.x() * dz + point.z() * dx, point.y());
					edges.merge(row, point, (left, right) -> left.x() * dx + left.z() * dz
							<= right.x() * dx + right.z() * dz ? left : right);
				}
				for (MinePoint face : edges.values()) {
					int firstLength = (int) Math.floorMod(StableHash.combine(body.identity().seed(), face.packed()), (long) lengths);
					for (int attempt = 0; attempt < lengths; attempt++) {
						int length = 6 + (firstLength + attempt) % lengths;
						MinePoint landing = new MinePoint(face.x() - dx * length, face.y() - 1, face.z() - dz * length);
						if ((landing.x() >> 4) != (face.x() >> 4) || (landing.z() >> 4) != (face.z() >> 4)
								|| !interior(landing.x() & 15) || !interior(landing.z() & 15)) continue;
						Optional<AbandonedMinePlan> candidate = access(body, surface, terrain, landing, face, dx, dz, length);
						if (candidate.isPresent()) return candidate;
					}
				}
			}
		}
		return Optional.empty();
	}

	private static Optional<AbandonedMinePlan> access(EmeraldDistrict body,
			ToIntBiFunction<Integer, Integer> surface, Predicate<AbandonedMinePlan> terrain,
			MinePoint landing, MinePoint face, int dx, int dz, int length) {
		int initial = surface.applyAsInt(landing.x(), landing.z());
		if (initial < 0) return Optional.empty();
		int firstFlight = (int) ((body.identity().seed() >>> 2) & 3);
		for (int span = 8; span >= 6; span -= 2) {
			for (int attempt = 0; attempt < 4; attempt++) {
				int forward = (firstFlight + attempt) % 4;
				for (int turn = 1; turn <= 3; turn += 2) {
					int[] a = DIRECTIONS[forward], b = DIRECTIONS[(forward + turn) % 4];
					int farX = (landing.x() & 15) + (a[0] + b[0]) * span;
					int farZ = (landing.z() & 15) + (a[1] + b[1]) * span;
					// Two blocks for supports, one more for the final liquid check.
					if (!interior(landing.x() & 15) || !interior(landing.z() & 15)
							|| !interior(farX) || !interior(farZ)) continue;
					for (int offset = 0; offset <= 16; offset++) {
						for (int sign = 1; sign >= -1; sign -= 2) {
							int height = initial + offset * sign;
							if (height >= landing.y() + 6 && height <= 245
									&& height - landing.y() <= AbandonedMinePlanner.MAXIMUM_SHAFT_DEPTH) {
								MineDecline access = MineDecline.compact(landing, height, forward, turn, span);
								MinePoint portal = access.entrance();
								if (surface.applyAsInt(portal.x(), portal.z()) == height) {
									Optional<AbandonedMinePlan> candidate = build(body, landing, face, dx, dz, length, access);
									if (candidate.isPresent() && terrain.test(candidate.get())) return candidate;
								}
							}
							if (offset == 0) break;
						}
					}
				}
			}
		}
		return Optional.empty();
	}

	private static boolean interior(int local) { return local >= 3 && local <= 12; }

	private static Optional<AbandonedMinePlan> build(EmeraldDistrict body, MinePoint landing,
			MinePoint face, int dx, int dz, int length, MineDecline access) {
		Map<Long, MineBlock> blocks = new LinkedHashMap<>();
		for (int along = 0; along < length; along++) {
			for (int across = -1; across <= 1; across++) {
				int x = landing.x() + along * dx + across * dz;
				int z = landing.z() + along * dz - across * dx;
				blocks.put(new MinePoint(x, landing.y() - 1, z).packed(), MineBlock.PLANKS);
				for (int height = 0; height < 3; height++) {
					blocks.put(new MinePoint(x, landing.y() + height, z).packed(), MineBlock.AIR);
				}
			}
		}
		access.build(blocks);
		if (!AbandonedMinePlanner.sealClosedPockets(blocks, access.entrance(), landing)
				|| !access.hasClearSteps(blocks)) return Optional.empty();
		for (int along = 0; along < length; along++) {
			for (int height = 0; height < 2; height++) {
				if (blocks.get(new MinePoint(landing.x() + along * dx, landing.y() + height,
						landing.z() + along * dz).packed()) != MineBlock.AIR) return Optional.empty();
			}
		}
		for (long position : blocks.keySet()) {
			MinePoint point = MinePoint.unpack(position);
			int x = point.x() & 15, z = point.z() & 15;
			if ((point.x() >> 4) != (face.x() >> 4) || (point.z() >> 4) != (face.z() >> 4)
					|| x == 0 || x == 15 || z == 0 || z == 15) return Optional.empty();
		}
		for (MinePoint ore : body.ore()) {
			if (blocks.containsKey(ore.packed())) return Optional.empty();
		}
		return Optional.of(new AbandonedMinePlan(body.identity().seed(), access.entrance(), landing, blocks));
	}
}
