package zone.moddev.mc.realisticdeposits.geometry;

/** A block coordinate in a saved mine plan. */
public final class MinePoint {
	private final int x;
	private final int y;
	private final int z;

	public MinePoint(int x, int y, int z) {
		this.x = x;
		this.y = y;
		this.z = z;
	}

	public int x() { return x; }
	public int y() { return y; }
	public int z() { return z; }

	public long packed() {
		return ((long) x & 0x3FFFFFFL) << 34 | ((long) z & 0x3FFFFFFL) << 8 | y & 255L;
	}

	public static MinePoint unpack(long value) {
		return new MinePoint((int) (value << 4 >> 38), (int) (value & 255L),
				(int) (value << 30 >> 38));
	}

	public static long chunkKey(int x, int z) {
		return (long) x << 32 | z & 0xFFFFFFFFL;
	}

	@Override
	public boolean equals(Object other) {
		if (!(other instanceof MinePoint)) return false;
		MinePoint point = (MinePoint) other;
		return x == point.x && y == point.y && z == point.z;
	}

	@Override
	public int hashCode() {
		return Long.hashCode(packed());
	}

	@Override
	public String toString() {
		return x + "," + y + "," + z;
	}
}
