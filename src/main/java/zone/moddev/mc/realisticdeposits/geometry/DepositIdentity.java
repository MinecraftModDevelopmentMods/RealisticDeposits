package zone.moddev.mc.realisticdeposits.geometry;

import java.util.Objects;

/** Order-independent identity shared by every chunk intersecting one deposit. */
public final class DepositIdentity {
	private final long worldSeed;
	private final String dimension;
	private final String definition;
	private final int regionX;
	private final int regionZ;
	private final long salt;
	private final long seed;

	public DepositIdentity(long worldSeed, String dimension, String definition,
			int regionX, int regionZ, long salt) {
		this.worldSeed = worldSeed;
		this.dimension = Objects.requireNonNull(dimension, "dimension");
		this.definition = Objects.requireNonNull(definition, "definition");
		this.regionX = regionX;
		this.regionZ = regionZ;
		this.salt = salt;
		long mixed = StableHash.combine(worldSeed, StableHash.string(dimension));
		mixed = StableHash.combine(mixed, StableHash.string(definition));
		mixed = StableHash.combine(mixed, regionX);
		mixed = StableHash.combine(mixed, regionZ);
		seed = StableHash.combine(mixed, salt);
	}

	public long worldSeed() { return worldSeed; }
	public String dimension() { return dimension; }
	public String definition() { return definition; }
	public int regionX() { return regionX; }
	public int regionZ() { return regionZ; }
	public long salt() { return salt; }
	public long seed() { return seed; }

	@Override
	public String toString() {
		return definition + "@" + dimension + "/" + regionX + "," + regionZ;
	}
}
