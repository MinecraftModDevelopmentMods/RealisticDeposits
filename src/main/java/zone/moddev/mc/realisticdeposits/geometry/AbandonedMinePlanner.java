package zone.moddev.mc.realisticdeposits.geometry;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Random;
import java.util.Set;

import zone.moddev.mc.realisticdeposits.model.AbandonedMineSettings;

/** Plans a small worked section while leaving the deposit's own random stream alone. */
public final class AbandonedMinePlanner {
	private static final long SELECTION_SALT = 0x13198A2E03707344L;
	private static final long LAYOUT_SALT = 0xA4093822299F31D0L;
	public static final int MAXIMUM_SHAFT_DEPTH = 96;

	private AbandonedMinePlanner() { }

	public static boolean selected(MineableDepositBody body, AbandonedMineSettings settings) {
		return settings.enabled()
				&& StableHash.unit(body.identity().seed() ^ SELECTION_SALT) < settings.chance();
	}

	public static MinePoint landing(MineableDepositBody body) {
		return body.miningRoutePoint(anchorAlong(body), 0.0D);
	}

	public static Optional<AbandonedMinePlan> plan(MineableDepositBody body,
			AbandonedMineSettings settings, int surfaceY) {
		if (!selected(body, settings)) return Optional.empty();

		MinePoint landing = landing(body);
		if (surfaceY < landing.y() + 6 || surfaceY - landing.y() > MAXIMUM_SHAFT_DEPTH
				|| landing.y() < 9 || surfaceY > 246) {
			return Optional.empty();
		}

		Random random = new Random(StableHash.mix(body.identity().seed() ^ LAYOUT_SALT));
		double anchor = anchorAlong(body);
		int halfLength = 24 + random.nextInt(9);
		List<List<MinePoint>> routes = new ArrayList<>();
		List<MinePoint> main = new ArrayList<>();

		for (int along = -halfLength; along <= halfLength; along += 2) {
			append(main, body.miningRoutePoint(anchor + along, 0.0D));
		}

		routes.add(main);
		for (int branch = 0; branch < 2; branch++) {
			int junction = branch == 0 ? main.size() / 3 : main.size() * 2 / 3;
			MinePoint start = main.get(junction);
			double along = anchor + (branch == 0 ? -halfLength / 3.0D : halfLength / 3.0D);
			double offset = (branch == 0 ? -1.0D : 1.0D) * (12 + random.nextInt(9));
			List<MinePoint> side = new ArrayList<>();
			side.add(start);
			append(side, body.miningRoutePoint(along, offset));
			routes.add(side);
		}

		List<MinePoint> connection = new ArrayList<>();
		connection.add(landing);
		append(connection, main.get(main.size() / 2));
		routes.add(connection);

		Map<Long, MineBlock> blocks = new LinkedHashMap<>();
		Set<Long> air = new LinkedHashSet<>();
		for (List<MinePoint> route : routes) {
			for (MinePoint point : route) {
				if (point.y() < 8 || point.y() > 245) return Optional.empty();

				for (int dx = -1; dx <= 1; dx++) {
					for (int dz = -1; dz <= 1; dz++) {
						put(blocks, point.x() + dx, point.y() - 1, point.z() + dz, MineBlock.PLANKS);
						for (int dy = 0; dy < 3; dy++) {
							air.add(new MinePoint(point.x() + dx, point.y() + dy, point.z() + dz).packed());
						}
					}
				}
			}
		}

		for (long position : air) blocks.put(position, MineBlock.AIR);
		for (List<MinePoint> route : routes) decorate(blocks, air, route, body.identity().seed());
		for (int branch = 1; branch <= 2; branch++) {
			List<MinePoint> route = routes.get(branch);
			MinePoint end = route.get(route.size() - 1);
			for (int dy = 0; dy <= 1; dy++) {
				long rubble = new MinePoint(end.x() + 1, end.y() + dy, end.z() + 1).packed();
				if (air.contains(rubble)) blocks.put(rubble, MineBlock.COBBLESTONE);
			}
		}
		shaft(blocks, landing, surfaceY);

		return Optional.of(new AbandonedMinePlan(body.identity().seed(),
				new MinePoint(landing.x(), surfaceY, landing.z()), landing, blocks));
	}

	private static double anchorAlong(MineableDepositBody body) {
		return (StableHash.unit(body.identity().seed() ^ LAYOUT_SALT) - 0.5D)
				* Math.min(64.0D, body.strikeLength() * 0.16D);
	}

	private static void append(List<MinePoint> route, MinePoint end) {
		if (route.isEmpty()) {
			route.add(end);
			return;
		}

		MinePoint current = route.get(route.size() - 1);
		int distance = Math.abs(end.x() - current.x()) + Math.abs(end.z() - current.z());
		for (int step = 0; step < distance; step++) {
			int dx = end.x() - current.x();
			int dz = end.z() - current.z();
			boolean moveX = Math.abs(dx) >= Math.abs(dz);
			current = new MinePoint(current.x() + (moveX ? Integer.signum(dx) : 0),
					current.y() + Integer.signum(end.y() - current.y()),
					current.z() + (moveX ? 0 : Integer.signum(dz)));
			route.add(current);
		}
	}

	private static void decorate(Map<Long, MineBlock> blocks, Set<Long> air,
			List<MinePoint> route, long identity) {
		for (int i = 2; i < route.size() - 2; i++) {
			MinePoint point = route.get(i);
			MinePoint previous = route.get(i - 1);
			MinePoint next = route.get(i + 1);
			boolean eastWest = previous.z() == next.z();
			boolean straight = eastWest || previous.x() == next.x();
			boolean flat = previous.y() == point.y() && next.y() == point.y();
			long detail = StableHash.combine(identity ^ LAYOUT_SALT, point.packed());

			if (straight && flat && StableHash.unit(detail) < 0.55D
					&& blocks.get(new MinePoint(point.x(), point.y() - 1, point.z()).packed()) == MineBlock.PLANKS) {
				put(blocks, point.x(), point.y(), point.z(),
						eastWest ? MineBlock.RAIL_EAST_WEST : MineBlock.RAIL_NORTH_SOUTH);
			}

			if (straight && i % 8 == 0) {
				for (int offset = -2; offset <= 2; offset++) {
					int x = point.x() + (eastWest ? 0 : offset);
					int z = point.z() + (eastWest ? offset : 0);
					long beam = new MinePoint(x, point.y() + 3, z).packed();
					if (!air.contains(beam)) blocks.put(beam, MineBlock.PLANKS);

					if (Math.abs(offset) == 2) {
						for (int dy = 0; dy < 3; dy++) {
							long post = new MinePoint(x, point.y() + dy, z).packed();
							if (!air.contains(post)) blocks.put(post, MineBlock.FENCE);
						}
					}
				}
			}

			if (StableHash.unit(detail ^ SELECTION_SALT) < 0.12D) {
				put(blocks, point.x() + (eastWest ? 0 : 1), point.y() + 2,
						point.z() + (eastWest ? 1 : 0), MineBlock.COBWEB);
			}
		}
	}

	private static void shaft(Map<Long, MineBlock> blocks, MinePoint landing, int surfaceY) {
		for (int y = landing.y(); y <= surfaceY + 2; y++) {
			for (int dx = -1; dx <= 1; dx++) {
				for (int dz = -1; dz <= 1; dz++) {
					put(blocks, landing.x() + dx, y, landing.z() + dz, MineBlock.AIR);
				}
			}

			put(blocks, landing.x() + 2, y, landing.z(), MineBlock.PLANKS);
			if (y <= surfaceY) put(blocks, landing.x() + 1, y, landing.z(), MineBlock.LADDER_WEST);

			if ((y - landing.y()) % 7 == 0 || y == surfaceY + 2) {
				for (int offset = -2; offset <= 2; offset++) {
					put(blocks, landing.x() - 2, y, landing.z() + offset, MineBlock.PLANKS);
					put(blocks, landing.x() + 2, y, landing.z() + offset, MineBlock.PLANKS);
					put(blocks, landing.x() + offset, y, landing.z() - 2, MineBlock.PLANKS);
					put(blocks, landing.x() + offset, y, landing.z() + 2, MineBlock.PLANKS);
				}
			}
		}

		for (int dx = -2; dx <= 2; dx++) {
			for (int dz = -2; dz <= 2; dz++) {
				if (Math.abs(dx) == 2 || Math.abs(dz) == 2) {
					put(blocks, landing.x() + dx, surfaceY - 1, landing.z() + dz, MineBlock.PLANKS);
				}
			}
		}
	}

	private static void put(Map<Long, MineBlock> blocks, int x, int y, int z, MineBlock block) {
		blocks.put(new MinePoint(x, y, z).packed(), block);
	}
}
