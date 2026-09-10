package zone.moddev.mc.realisticdeposits.geometry;

import java.util.Optional;
import java.util.Random;

import zone.moddev.mc.realisticdeposits.model.DepositDefinition;
import zone.moddev.mc.realisticdeposits.model.StratiformSeamSettings;

/** Selects and constructs region-scale deposits independently of chunk order. */
public final class RegionDepositPlanner {
	private RegionDepositPlanner() { }

	public static Optional<StratiformSeamBody> plan(DepositDefinition definition,
			long worldSeed, String dimension, int regionX, int regionZ) {
		if (!definition.enabled() || !definition.dimensions().contains(dimension)) {
			return Optional.empty();
		}
		DepositIdentity identity = new DepositIdentity(worldSeed, dimension, definition.id(),
				regionX, regionZ, definition.salt());
		if (StableHash.unit(identity.seed() ^ 0x43A5C85C97CB3127L) >= definition.selectionChance()) {
			return Optional.empty();
		}
		Random random = new Random(StableHash.mix(identity.seed() ^ 0x5DEECE66DL));
		int regionBlocks = definition.regionSpacingChunks() * 16;
		int regionOriginX = multiplyExact(regionX, regionBlocks);
		int regionOriginZ = multiplyExact(regionZ, regionBlocks);
		int centerX = regionOriginX + random.nextInt(regionBlocks);
		int centerZ = regionOriginZ + random.nextInt(regionBlocks);
		int centerY = definition.minimumY()
				+ random.nextInt(definition.maximumY() - definition.minimumY() + 1);
		StratiformSeamSettings settings = definition.seam();
		int strikeLength = settings.strikeLength().sample(random);
		int downDipWidth = settings.downDipWidth().sample(random);
		double strike = random.nextDouble() * Math.PI * 2.0D;
		double dip = between(random, settings.minimumDipDegrees(), settings.maximumDipDegrees());
		return Optional.of(new StratiformSeamBody(identity, centerX, centerY, centerZ,
				strikeLength, downDipWidth, strike, dip, settings,
				random.nextDouble() * Math.PI * 2.0D,
				random.nextDouble() * Math.PI * 2.0D,
				random.nextDouble() * Math.PI * 2.0D,
				definition.minimumY(), definition.maximumY()));
	}

	private static int multiplyExact(int left, int right) {
		long result = (long) left * right;
		if (result < Integer.MIN_VALUE || result > Integer.MAX_VALUE) {
			throw new IllegalArgumentException("Deposit region lies outside Minecraft coordinates");
		}
		return (int) result;
	}

	private static double between(Random random, double min, double max) {
		return min == max ? min : min + random.nextDouble() * (max - min);
	}
}
