package zone.moddev.mc.realisticdeposits.geometry;

import java.util.Optional;
import java.util.Random;

import zone.moddev.mc.realisticdeposits.model.CoalMeasuresSettings;
import zone.moddev.mc.realisticdeposits.model.DepositDefinition;

/** Several bedded coal seams, with the same folding and faulting in every bed. */
public final class CoalMeasuresBody implements MineableDepositBody {
	private final DepositIdentity identity;
	private final int x;
	private final int y;
	private final int z;
	private final int length;
	private final int width;
	private final int minY;
	private final int maxY;
	private final double cosine;
	private final double sine;
	private final double dip;
	private final double fold;
	private final double phase;
	private final double[] offsets;
	private final int[] thickness;
	private final double[] faults;
	private final int[] throwsBySegment;
	private final BeddingGrid[] bedding;
	private final BlockBounds bounds;

	private CoalMeasuresBody(DepositIdentity identity, int x, int y, int z, int length, int width,
			int minY, int maxY, double cosine, double sine, double dip, double fold, double phase,
			double[] offsets, int[] thickness, double[] faults, int[] throwsBySegment, BeddingGrid[] bedding) {
		this.identity = identity;
		this.x = x;
		this.y = y;
		this.z = z;
		this.length = length;
		this.width = width;
		this.minY = minY;
		this.maxY = maxY;
		this.cosine = cosine;
		this.sine = sine;
		this.dip = dip;
		this.fold = fold;
		this.phase = phase;
		this.offsets = offsets;
		this.thickness = thickness;
		this.faults = faults;
		this.throwsBySegment = throwsBySegment;
		this.bedding = bedding == null ? null : bedding.clone();
		int radius = (int) Math.ceil(length * 0.5D + width * 0.5D + 2);
		bounds = new BlockBounds(x - radius, minY, z - radius, x + radius, maxY, z + radius);
	}

	public static Optional<CoalMeasuresBody> plan(DepositDefinition definition,
			long worldSeed, String dimension, int regionX, int regionZ) {
		if (!definition.enabled() || !definition.isCoal() || !definition.dimensions().contains(dimension)) {
			return Optional.empty();
		}

		DepositIdentity identity = new DepositIdentity(worldSeed, dimension, definition.id(),
				regionX, regionZ, definition.salt());
		if (StableHash.unit(identity.seed() ^ 0x43A5C85C97CB3127L) >= definition.selectionChance()) {
			return Optional.empty();
		}

		Random random = new Random(StableHash.mix(identity.seed() ^ 0xC6BC279692B5CC83L));
		CoalMeasuresSettings settings = definition.coal();
		int region = definition.regionSpacingChunks() * 16;
		int x = Math.toIntExact((long) regionX * region + random.nextInt(region));
		int z = Math.toIntExact((long) regionZ * region + random.nextInt(region));
		int length = settings.strikeLength().sample(random);
		int width = settings.downDipWidth().sample(random);
		double strike = random.nextDouble() * Math.PI * 2;
		double dip = Math.tan(Math.toRadians((random.nextDouble() * 2 - 1) * settings.maximumDip()));
		int[] thickness = new int[settings.beds().sample(random)];
		double[] offsets = new double[thickness.length];
		int span = 0;

		for (int bed = 0; bed < thickness.length; bed++) {
			thickness[bed] = settings.thickness().sample(random);
			offsets[bed] = span + thickness[bed] * 0.5D;
			span += thickness[bed];
			if (bed + 1 < thickness.length) span += settings.separation().sample(random);
		}
		for (int bed = 0; bed < offsets.length; bed++) offsets[bed] -= span * 0.5D;

		double[] faults = {-length * (0.12D + random.nextDouble() * 0.18D),
				length * (0.12D + random.nextDouble() * 0.18D)};
		int[] throwsBySegment = {0, 0, 0};
		for (int segment = 1; segment < 3; segment++) {
			throwsBySegment[segment] = throwsBySegment[segment - 1]
					+ random.nextInt(settings.maximumFaultThrow() * 2 + 1) - settings.maximumFaultThrow();
		}

		int radiusY = (int) Math.ceil(span * 0.5D + Math.abs(dip) * width * 0.5D
				+ settings.foldAmplitude() + settings.maximumFaultThrow() * 2 + 2);
		int bottom = definition.minimumY() + radiusY;
		int top = definition.maximumY() - radiusY;
		if (bottom > top) return Optional.empty();
		int y = bottom + random.nextInt(top - bottom + 1);

		return Optional.of(new CoalMeasuresBody(identity, x, y, z, length, width,
				definition.minimumY(), definition.maximumY(), Math.cos(strike), Math.sin(strike),
				dip, settings.foldAmplitude(), random.nextDouble() * Math.PI * 2,
				offsets, thickness, faults, throwsBySegment, null));
	}

	/** Takes only immutable sampled heights; no world or geology sampler is retained. */
	public CoalMeasuresBody withBedding(BeddingGrid[] grid) {
		if (grid.length != bedCount()) throw new IllegalArgumentException("Each coal bed needs a bedding surface");
		return new CoalMeasuresBody(identity, x, y, z, length, width, minY, maxY, cosine, sine,
				dip, fold, phase, offsets, thickness, faults, throwsBySegment, grid);
	}

	@Override public DepositIdentity identity() { return identity; }
	@Override public int centerX() { return x; }
	@Override public int centerY() { return y; }
	@Override public int centerZ() { return z; }
	@Override public int strikeLength() { return length; }
	public int downDipWidth() { return width; }
	public int bedCount() { return offsets.length; }
	public boolean selectedOutsideBasin() {
		return StableHash.unit(identity.seed() ^ 0xDB4F0B9175AE2165L) < 0.25D;
	}
	public double bedOffset(int bed) { return offsets[bed]; }
	public int bedThickness(int bed) { return thickness[bed]; }
	public double halfStackHeight() { return (offsets[offsets.length - 1] - offsets[0]) * 0.5D + 2; }
	@Override public BlockBounds bounds() { return bounds; }

	public boolean containsColumn(int blockX, int blockZ) {
		double along = along(blockX, blockZ) / (length * 0.5D);
		double across = across(blockX, blockZ) / (width * 0.5D);
		return along * along + across * across < 1.0D;
	}

	public double bedY(int bed, int blockX, int blockZ) {
		double along = along(blockX, blockZ);
		double across = across(blockX, blockZ);
		double base = bedding == null ? y + across * dip + offsets[bed]
				: bedding[bed].heightAt(blockX, blockZ);
		int segment = along < faults[0] ? 0 : along < faults[1] ? 1 : 2;
		return base + throwsBySegment[segment]
				+ fold * Math.sin(along / 140.0D + phase) * Math.cos(across / 110.0D);
	}

	public int minimumCandidateYAt(int blockX, int blockZ) {
		if (!containsColumn(blockX, blockZ)) return 1;
		int minimum = Integer.MAX_VALUE;
		for (int bed = 0; bed < offsets.length; bed++) {
			double base = bedY(bed, blockX, blockZ);
			if (Double.isFinite(base)) minimum = Math.min(minimum, (int) Math.floor(base - thickness[bed]));
		}
		return minimum == Integer.MAX_VALUE ? 1 : Math.max(minY, minimum);
	}

	public int maximumCandidateYAt(int blockX, int blockZ) {
		if (!containsColumn(blockX, blockZ)) return 0;
		int maximum = Integer.MIN_VALUE;
		for (int bed = 0; bed < offsets.length; bed++) {
			double base = bedY(bed, blockX, blockZ);
			if (Double.isFinite(base)) maximum = Math.max(maximum, (int) Math.ceil(base + thickness[bed]));
		}
		return maximum == Integer.MIN_VALUE ? 0 : Math.min(maxY, maximum);
	}

	public boolean isOreAt(int blockX, int blockY, int blockZ) {
		if (blockY < minY || blockY > maxY || !containsColumn(blockX, blockZ)) return false;
		double along = along(blockX, blockZ);
		double across = across(blockX, blockZ);
		double edge = 1 - Math.sqrt(Math.pow(along / (length * 0.5D), 2)
				+ Math.pow(across / (width * 0.5D), 2));
		double taper = Math.min(1, edge / 0.12D);

		for (int bed = 0; bed < offsets.length; bed++) {
			double centre = bedY(bed, blockX, blockZ);
			if (!Double.isFinite(centre)) continue;
			double half = thickness[bed] * 0.5D * taper;
			if (half < 0.3D || blockY + 0.5D < centre - half || blockY + 0.5D >= centre + half) continue;

			// A local rock parting splits thicker beds; it never fills the gap with more coal.
			boolean split = thickness[bed] >= 3 && along > -length * 0.22D && along < length * 0.18D
					&& across > width * 0.08D && across < width * 0.30D;
			if (!split || Math.abs(blockY + 0.5D - centre) >= 0.5D) return true;
		}
		return false;
	}

	@Override
	public MinePoint miningRoutePoint(double along, double lateralOffset) {
		int blockX = (int) Math.floor(x + along * cosine - lateralOffset * sine);
		int blockZ = (int) Math.floor(z + along * sine + lateralOffset * cosine);
		return new MinePoint(blockX, (int) Math.floor(bedY(offsets.length - 1, blockX, blockZ)), blockZ);
	}

	private double along(int blockX, int blockZ) { return (blockX - x) * cosine + (blockZ - z) * sine; }
	private double across(int blockX, int blockZ) { return -(blockX - x) * sine + (blockZ - z) * cosine; }

	/** Coarse, read-only bedding surface shared by all beds of one coalfield. */
	public static final class BeddingGrid {
		private final int x;
		private final int z;
		private final int step;
		private final int side;
		private final float[] heights;

		public BeddingGrid(int x, int z, int step, int side, float[] heights) {
			if (step < 1 || side < 2 || heights.length != side * side) {
				throw new IllegalArgumentException("Invalid coal bedding grid");
			}
			this.x = x;
			this.z = z;
			this.step = step;
			this.side = side;
			this.heights = heights.clone();
		}

		public double heightAt(int blockX, int blockZ) {
			double fx = (blockX - x) / (double) step;
			double fz = (blockZ - z) / (double) step;
			int ix = (int) Math.floor(fx);
			int iz = (int) Math.floor(fz);
			if (ix < 0 || iz < 0 || ix + 1 >= side || iz + 1 >= side) return Double.NaN;
			double a = heights[ix + iz * side];
			double b = heights[ix + 1 + iz * side];
			double c = heights[ix + (iz + 1) * side];
			double d = heights[ix + 1 + (iz + 1) * side];
			return (a * (1 - (fx - ix)) + b * (fx - ix)) * (1 - (fz - iz))
					+ (c * (1 - (fx - ix)) + d * (fx - ix)) * (fz - iz);
		}
	}
}
