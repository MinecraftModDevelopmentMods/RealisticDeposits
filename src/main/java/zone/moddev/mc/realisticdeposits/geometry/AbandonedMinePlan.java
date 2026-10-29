package zone.moddev.mc.realisticdeposits.geometry;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** A frozen, bounded set of workings, indexed by the chunks that build them. */
public final class AbandonedMinePlan {
	public static final int LAYOUT_VERSION = 1;
	public static final int MAXIMUM_BLOCKS = 16384;

	private final long identity;
	private final MinePoint entrance;
	private final MinePoint landing;
	private final Map<Long, MineBlock> blocks;
	private final Map<Long, List<Cell>> chunks;
	private final BlockBounds bounds;

	public AbandonedMinePlan(long identity, MinePoint entrance, MinePoint landing,
			Map<Long, MineBlock> blocks) {
		if (blocks.isEmpty() || blocks.size() > MAXIMUM_BLOCKS) {
			throw new IllegalArgumentException("Mine plan has an invalid block count");
		}

		this.identity = identity;
		this.entrance = entrance;
		this.landing = landing;
		this.blocks = Collections.unmodifiableMap(new LinkedHashMap<>(blocks));
		Map<Long, List<Cell>> slices = new LinkedHashMap<>();
		int minX = Integer.MAX_VALUE;
		int minY = Integer.MAX_VALUE;
		int minZ = Integer.MAX_VALUE;
		int maxX = Integer.MIN_VALUE;
		int maxY = Integer.MIN_VALUE;
		int maxZ = Integer.MIN_VALUE;

		for (Map.Entry<Long, MineBlock> entry : blocks.entrySet()) {
			MinePoint point = MinePoint.unpack(entry.getKey());
			if (point.packed() != entry.getKey() || point.y() < 5 || point.y() > 250
					|| Math.abs((long) point.x() - landing.x()) > 80L
					|| Math.abs((long) point.z() - landing.z()) > 80L) {
				throw new IllegalArgumentException("Mine plan exceeds its safe bounds");
			}

			minX = Math.min(minX, point.x());
			minY = Math.min(minY, point.y());
			minZ = Math.min(minZ, point.z());
			maxX = Math.max(maxX, point.x());
			maxY = Math.max(maxY, point.y());
			maxZ = Math.max(maxZ, point.z());
			long key = MinePoint.chunkKey(Math.floorDiv(point.x(), 16), Math.floorDiv(point.z(), 16));
			slices.computeIfAbsent(key, ignored -> new ArrayList<>()).add(new Cell(point, entry.getValue()));
		}

		Map<Long, List<Cell>> immutable = new LinkedHashMap<>();
		for (Map.Entry<Long, List<Cell>> slice : slices.entrySet()) {
			immutable.put(slice.getKey(), Collections.unmodifiableList(slice.getValue()));
		}

		chunks = Collections.unmodifiableMap(immutable);
		bounds = new BlockBounds(minX, minY, minZ, maxX, maxY, maxZ);
	}

	public long identity() { return identity; }
	public MinePoint entrance() { return entrance; }
	public MinePoint landing() { return landing; }
	public Map<Long, MineBlock> blocks() { return blocks; }
	public BlockBounds bounds() { return bounds; }

	public List<Cell> cellsInChunk(int x, int z) {
		return chunks.getOrDefault(MinePoint.chunkKey(x, z), Collections.emptyList());
	}

	public static final class Cell {
		private final MinePoint point;
		private final MineBlock block;

		private Cell(MinePoint point, MineBlock block) {
			this.point = point;
			this.block = block;
		}

		public MinePoint point() { return point; }
		public MineBlock block() { return block; }
	}
}
