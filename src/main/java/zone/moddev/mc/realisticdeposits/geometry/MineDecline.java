package zone.moddev.mc.realisticdeposits.geometry;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;

/** Stepped access with turning landings instead of an open vertical shaft. */
public final class MineDecline {
	private static final long ACCESS_SALT = 0x6A09E667F3BCC909L;
	private static final int[] DX = {1, 0, -1, 0};
	private static final int[] DZ = {0, 1, 0, -1};
	private final List<Step> steps;

	private MineDecline(List<Step> steps) {
		this.steps = Collections.unmodifiableList(steps);
	}

	public static MineDecline plan(MinePoint landing, int surfaceY, long identity) {
		if (surfaceY <= landing.y() || surfaceY - landing.y() > AbandonedMinePlanner.MAXIMUM_SHAFT_DEPTH) {
			throw new IllegalArgumentException("Mine access depth is outside the supported range");
		}

		Random random = new Random(StableHash.mix(identity ^ ACCESS_SALT));
		int forward = random.nextInt(4);
		int across = (forward + (random.nextBoolean() ? 1 : 3)) % 4;
		int x = landing.x();
		int y = landing.y();
		int z = landing.z();
		List<Step> steps = new ArrayList<>();
		steps.add(new Step(new MinePoint(x, y, z), forward, false));

		while (y < surfaceY) {
			int rise = Math.min(10 + random.nextInt(5), surfaceY - y);

			for (int level = 0; level < rise; level++) {
				x += DX[forward];
				z += DZ[forward];
				steps.add(new Step(new MinePoint(x, y, z), forward, true));
				x += DX[forward];
				z += DZ[forward];
				y++;
				steps.add(new Step(new MinePoint(x, y, z), forward, false));
			}

			if (y < surfaceY) {
				for (int turn = 0; turn < 8; turn++) {
					x += DX[across];
					z += DZ[across];
					steps.add(new Step(new MinePoint(x, y, z), across, false));
				}
				forward = (forward + 2) % 4;
			}
		}

		return new MineDecline(steps);
	}

	public List<Step> steps() {
		return steps;
	}

	public MinePoint entrance() {
		return steps.get(steps.size() - 1).point();
	}

	void build(Map<Long, MineBlock> blocks) {
		Map<Long, MineBlock> floors = new LinkedHashMap<>();
		Set<Long> air = new LinkedHashSet<>();

		for (Step step : steps) {
			MinePoint point = step.point;
			int side = (step.direction + 1) % 4;
			int floorY = point.y() - (step.stairs ? 0 : 1);
			int firstAir = point.y() + (step.stairs ? 1 : 0);

			for (int width = -1; width <= 1; width++) {
				int x = point.x() + DX[side] * width;
				int z = point.z() + DZ[side] * width;
				floors.put(new MinePoint(x, floorY, z).packed(), step.stairs ? stair(step.direction) : MineBlock.PLANKS);
				if (step.stairs) floors.put(new MinePoint(x, floorY - 1, z).packed(), MineBlock.PLANKS);

				for (int y = firstAir; y <= point.y() + 3; y++) {
					air.add(new MinePoint(x, y, z).packed());
				}
			}
		}

		// Lay the footing last so a bend cannot cut away its own steps.
		for (long position : air) blocks.put(position, MineBlock.AIR);
		blocks.putAll(floors);

		for (int index = 0; index < steps.size(); index++) {
			Step step = steps.get(index);
			if (index % 12 == 0 || index == steps.size() - 1) frame(blocks, air, floors.keySet(), step);

			if (step.point.y() >= entrance().y() - 4) {
				int side = (step.direction + 1) % 4;
				for (int edge : new int[] {-2, 2}) {
					long position = new MinePoint(step.point.x() + DX[side] * edge, step.point.y(),
							step.point.z() + DZ[side] * edge).packed();
					if (!air.contains(position) && !floors.containsKey(position)) blocks.put(position, MineBlock.FENCE);
				}
			}
		}
	}

	private static void frame(Map<Long, MineBlock> blocks, Set<Long> air, Set<Long> floors, Step step) {
		int side = (step.direction + 1) % 4;
		for (int width = -2; width <= 2; width++) {
			int x = step.point.x() + DX[side] * width;
			int z = step.point.z() + DZ[side] * width;
			long beam = new MinePoint(x, step.point.y() + 4, z).packed();
			if (!air.contains(beam) && !floors.contains(beam)) blocks.put(beam, MineBlock.PLANKS);

			if (Math.abs(width) == 2) {
				for (int y = step.point.y(); y < step.point.y() + 4; y++) {
					long post = new MinePoint(x, y, z).packed();
					if (!air.contains(post) && !floors.contains(post)) blocks.put(post, MineBlock.FENCE);
				}
			}
		}
	}

	private static MineBlock stair(int direction) {
		switch (direction) {
			case 0: return MineBlock.STAIRS_EAST;
			case 1: return MineBlock.STAIRS_SOUTH;
			case 2: return MineBlock.STAIRS_WEST;
			case 3: return MineBlock.STAIRS_NORTH;
			default: throw new IllegalArgumentException("Unknown mine direction");
		}
	}

	public static final class Step {
		private final MinePoint point;
		private final int direction;
		private final boolean stairs;

		private Step(MinePoint point, int direction, boolean stairs) {
			this.point = point;
			this.direction = direction;
			this.stairs = stairs;
		}

		public MinePoint point() {
			return point;
		}

		public boolean stairs() {
			return stairs;
		}
	}
}
