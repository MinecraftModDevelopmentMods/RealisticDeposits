package zone.moddev.mc.realisticdeposits.geometry;

/** Stable palette used by the world save; numbers must not be reassigned. */
public enum MineBlock {
	AIR(0), PLANKS(1), FENCE(2), RAIL_NORTH_SOUTH(3), RAIL_EAST_WEST(4),
	COBWEB(5), LADDER_WEST(6), COBBLESTONE(7),
	STAIRS_NORTH(8), STAIRS_SOUTH(9), STAIRS_EAST(10), STAIRS_WEST(11);

	private final int savedId;

	MineBlock(int savedId) {
		this.savedId = savedId;
	}

	public int savedId() {
		return savedId;
	}

	public boolean passable() {
		return this == AIR || this == RAIL_NORTH_SOUTH || this == RAIL_EAST_WEST
				|| this == LADDER_WEST || this == COBWEB;
	}

	public static MineBlock fromSavedId(int id) {
		for (MineBlock block : values()) {
			if (block.savedId == id) return block;
		}

		throw new IllegalArgumentException("Unknown saved mine block " + id);
	}
}
