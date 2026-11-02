package zone.moddev.mc.realisticdeposits.geometry;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.Random;

import zone.moddev.mc.realisticdeposits.model.AlignmentMode;
import zone.moddev.mc.realisticdeposits.model.DepositDefinition;
import zone.moddev.mc.realisticdeposits.model.GoldLodeSettings;

/** Steep fractures with plunging ore shoots, planned independently of mined blocks. */
public final class GoldDistrict implements MineableDepositBody {
	public interface Site {
		int surfaceY(int x, int z);
		String geome(int x, int z, int surfaceY);
	}

	private final DepositIdentity identity;
	private final int x;
	private final int y;
	private final int z;
	private final int bottom;
	private final int top;
	private final int cover;
	private final double cosine;
	private final double sine;
	private final List<Vein> veins;
	private final BlockBounds bounds;

	private GoldDistrict(DepositIdentity identity, int x, int z, int bottom, int top,
			int cover, Random random, GoldLodeSettings settings) {
		this.identity = identity;
		this.x = x;
		this.z = z;
		this.bottom = bottom;
		this.top = top;
		this.y = (bottom + top) / 2;
		this.cover = cover;

		double angle = random.nextDouble() * Math.PI * 2;
		cosine = Math.cos(angle);
		sine = Math.sin(angle);
		List<Vein> planned = new ArrayList<>();
		int count = settings.veinCount().sample(random);
		for (int index = 0; index < count; index++) {
			planned.add(new Vein(StableHash.combine(identity.seed(), index),
					settings.strikeLength().sample(random), settings.thickness().sample(random),
					settings.minimumDip() + random.nextDouble() * (settings.maximumDip() - settings.minimumDip()),
					index == 0 ? 0 : (index % 2 == 0 ? 1 : -1) * (10 + random.nextDouble() * 22)));
		}
		veins = Collections.unmodifiableList(planned);

		double along = settings.strikeLength().maximum() * 0.5 + 2;
		double across = (top - bottom) * 0.5 / Math.tan(Math.toRadians(settings.minimumDip())) + 64;
		int reachX = (int) Math.ceil(Math.abs(cosine) * along + Math.abs(sine) * across);
		int reachZ = (int) Math.ceil(Math.abs(sine) * along + Math.abs(cosine) * across);
		bounds = new BlockBounds(x - reachX, bottom, z - reachZ, x + reachX, top, z + reachZ);
	}

	public static Optional<GoldDistrict> plan(DepositDefinition definition, long worldSeed,
			String dimension, int regionX, int regionZ, Site site) {
		if (!definition.isGoldLode() || !definition.enabled() || !definition.dimensions().contains(dimension)) {
			return Optional.empty();
		}

		DepositIdentity identity = new DepositIdentity(worldSeed, dimension, definition.id(),
				regionX, regionZ, definition.salt());
		if (StableHash.unit(identity.seed() ^ 0x41C784B2A39F518DL) >= definition.selectionChance()) {
			return Optional.empty();
		}

		Random random = new Random(identity.seed());
		int size = definition.regionSpacingChunks() * 16;
		int x = Math.toIntExact((long) regionX * size + random.nextInt(size));
		int z = Math.toIntExact((long) regionZ * size + random.nextInt(size));
		int surface = site.surfaceY(x, z);
		if (surface < 0) return Optional.empty();

		GoldLodeSettings settings = definition.lode();
		String geome = site.geome(x, z, surface);
		if (definition.alignment() != AlignmentMode.MASSIVE_RARE) {
			boolean approximate = geome == null || "orespawn:cyano".equals(geome);
			if (approximate) {
				if (StableHash.unit(identity.seed() ^ 0x11A9F6B2E8138A5CL) >= settings.legacySelectionScale()) {
					return Optional.empty();
				}
			} else if (!definition.geomes().contains(geome)) {
				return Optional.empty();
			} else if (!"orespawn:mountain_belt".equals(geome)
					&& StableHash.unit(identity.seed() ^ 0x2F54D932E77145A1L) >= 0.5) {
				return Optional.empty();
			}
		}

		int cover = settings.surfaceCover().sample(random);
		int top = Math.min(definition.maximumY(), surface - cover);
		int bottom = definition.minimumY();
		if (top - bottom < 24) return Optional.empty();

		return Optional.of(new GoldDistrict(identity, x, z, bottom, top, cover, random, settings));
	}

	@Override public DepositIdentity identity() { return identity; }
	@Override public int centerX() { return x; }
	@Override public int centerY() { return y; }
	@Override public int centerZ() { return z; }
	@Override public int strikeLength() { return veins.get(0).length; }
	@Override public BlockBounds bounds() { return bounds; }
	public List<Vein> veins() { return veins; }
	public int surfaceCover() { return cover; }

	public MinePoint pointOnVein(int index, double along, int height) {
		Vein vein = veins.get(index);
		double across = vein.midline(along, 0) + (height - y) * vein.cotangent;
		return new MinePoint((int) Math.floor(x + along * cosine - across * sine), height,
				(int) Math.floor(z + along * sine + across * cosine));
	}

	public double alongAt(double blockX, double blockZ) {
		return (blockX - x) * cosine + (blockZ - z) * sine;
	}

	public double acrossAt(double blockX, double blockZ) {
		return -(blockX - x) * sine + (blockZ - z) * cosine;
	}

	@Override
	public boolean isOreAt(int blockX, int blockY, int blockZ) {
		if (blockY < bottom || blockY > top) return false;
		double along = alongAt(blockX + 0.5, blockZ + 0.5);
		double across = acrossAt(blockX + 0.5, blockZ + 0.5);
		for (Vein vein : veins) {
			if (vein.isOreAt(along, across, blockY - y, blockY - bottom, top - blockY)) return true;
		}
		return false;
	}

	@Override public int minimumCandidateYAt(int blockX, int blockZ) {
		return candidateY(blockX, blockZ, true);
	}

	@Override public int maximumCandidateYAt(int blockX, int blockZ) {
		return candidateY(blockX, blockZ, false);
	}

	private int candidateY(int blockX, int blockZ, boolean minimum) {
		double along = alongAt(blockX + 0.5, blockZ + 0.5);
		double across = acrossAt(blockX + 0.5, blockZ + 0.5);
		double result = minimum ? 256 : -1;
		for (Vein vein : veins) {
			if (Math.abs(along) > vein.length * 0.5) continue;
			for (int branch = 0; branch < 2; branch++) {
				double centre = y + (across - vein.midline(along, branch)) / vein.cotangent;
				double margin = (vein.width * 0.5 + 0.75) / vein.cotangent;
				result = minimum ? Math.min(result, centre - margin) : Math.max(result, centre + margin);
			}
		}
		return minimum ? Math.max(bottom, (int) Math.floor(result)) : Math.min(top, (int) Math.ceil(result));
	}

	public double distanceSquared(int blockX, int blockZ) {
		double along = Math.max(0, Math.abs(alongAt(blockX, blockZ)) - strikeLength() * 0.5);
		double across = acrossAt(blockX, blockZ);
		return along * along + across * across;
	}

	@Override
	public MinePoint miningRoutePoint(double along, double lateralOffset) {
		Vein vein = veins.get(0);
		// Aim the short gallery at a rich shoot, not an arbitrary lean stretch.
		double phase = Math.atan2(Math.sin(vein.phase), Math.cos(vein.phase));
		double forward = Math.max(-vein.length * 0.45, Math.min(vein.length * 0.45, along - phase * 31));
		double side = vein.midline(forward, 0) + lateralOffset;
		return new MinePoint((int) Math.floor(x + forward * cosine - side * sine), y,
				(int) Math.floor(z + forward * sine + side * cosine));
	}

	public static boolean placerPocket(long seed, int x, int z, int depth, double coverage) {
		long patch = StableHash.combine(StableHash.combine(seed, Math.floorDiv(x, 8)), Math.floorDiv(z, 8));
		long grain = StableHash.combine(StableHash.combine(seed ^ 0x11797A38E1D72B83L, x), z);
		return StableHash.unit(patch) < coverage
				&& StableHash.unit(StableHash.combine(grain, depth)) < 0.75 - depth * 0.15;
	}

	/** Ore shoots plunge within each fracture; lean runs remain mostly country rock. */
	public static final class Vein {
		private final long seed;
		private final int length;
		private final int width;
		private final double dip;
		private final double offset;
		private final double cotangent;
		private final double phase;

		private Vein(long seed, int length, int width, double dip, double offset) {
			this.seed = seed;
			this.length = length;
			this.width = width;
			this.dip = dip;
			this.offset = offset;
			cotangent = 1 / Math.tan(Math.toRadians(dip));
			phase = StableHash.unit(seed) * Math.PI * 2;
		}

		public int length() { return length; }
		public int width() { return width; }
		public double dipDegrees() { return dip; }

		private double midline(double along, int branch) {
			double bend = 5 * Math.sin(along / 71 + phase) + 2 * Math.sin(along / 29 - phase);
			double fault = 3 * Math.tanh((along - Math.sin(phase) * length * 0.15) / 6);
			double splay = branch == 0 ? 0 : 14 * Math.pow(Math.max(0,
					Math.sin((along / length + 0.5) * Math.PI)), 2);
			return offset + bend + fault + splay;
		}

		private boolean isOreAt(double along, double across, int height, int aboveBottom, int belowTop) {
			if (Math.abs(along) >= length * 0.5) return false;
			double edge = Math.min(1, (length * 0.5 - Math.abs(along)) / 20);
			double pinch = 0.55 + 0.45 * Math.pow(Math.sin(along / 35 + phase + height / 45.0), 2);
			double halfWidth = Math.max(0.4, width * 0.5 * pinch * edge);
			boolean inside = false;
			for (int branch = 0; branch < 2; branch++) {
				if (Math.abs(across - midline(along, branch) - height * cotangent) <= halfWidth) inside = true;
			}
			if (!inside || aboveBottom < 1 || belowTop < 1) return false;

			double shoot = Math.pow(Math.max(0, Math.cos(along / 31 + height / 40.0 + phase)), 6);
			if (shoot > 0.38) return true;
			long cluster = StableHash.combine(StableHash.combine(seed, (long) Math.floor(along / 4)),
					Math.floorDiv(height, 3));
			return shoot > 0.06 && StableHash.unit(cluster) < 0.12;
		}
	}
}
