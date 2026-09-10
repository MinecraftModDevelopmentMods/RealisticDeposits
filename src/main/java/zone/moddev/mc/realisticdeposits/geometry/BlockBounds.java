package zone.moddev.mc.realisticdeposits.geometry;

/** Inclusive block bounds for one abstract deposit body. */
public final class BlockBounds {
	private final int minimumX;
	private final int minimumY;
	private final int minimumZ;
	private final int maximumX;
	private final int maximumY;
	private final int maximumZ;

	public BlockBounds(int minimumX, int minimumY, int minimumZ,
			int maximumX, int maximumY, int maximumZ) {
		this.minimumX = minimumX;
		this.minimumY = minimumY;
		this.minimumZ = minimumZ;
		this.maximumX = maximumX;
		this.maximumY = maximumY;
		this.maximumZ = maximumZ;
	}

	public int minimumX() { return minimumX; }
	public int minimumY() { return minimumY; }
	public int minimumZ() { return minimumZ; }
	public int maximumX() { return maximumX; }
	public int maximumY() { return maximumY; }
	public int maximumZ() { return maximumZ; }

	public boolean intersectsChunk(int chunkX, int chunkZ) {
		int minX = chunkX << 4;
		int minZ = chunkZ << 4;
		return maximumX >= minX && minimumX <= minX + 15
				&& maximumZ >= minZ && minimumZ <= minZ + 15;
	}
}
