package zone.moddev.mc.realisticdeposits.geometry;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.Random;

import zone.moddev.mc.realisticdeposits.model.AlignmentMode;
import zone.moddev.mc.realisticdeposits.model.DepositDefinition;
import zone.moddev.mc.realisticdeposits.model.DiamondPipeSettings;

/** A district is planned without reading generated ore or loading any chunks. */
public final class DiamondDistrict {
	public interface Site {
		int surfaceY(int x, int z);
		String geome(int x, int z, int surfaceY);
	}

	private final DepositIdentity identity;
	private final int x;
	private final int z;
	private final List<Pipe> pipes;

	private DiamondDistrict(DepositIdentity identity, int x, int z, List<Pipe> pipes) {
		this.identity = identity;
		this.x = x;
		this.z = z;
		this.pipes = Collections.unmodifiableList(new ArrayList<>(pipes));
	}

	public static Optional<DiamondDistrict> plan(DepositDefinition definition, long seed, String dimension,
			int regionX, int regionZ, Site site) {
		if (!definition.isDiamondPipe() || !definition.enabled()
				|| !definition.dimensions().contains(dimension)) return Optional.empty();

		DepositIdentity identity = new DepositIdentity(seed, dimension, definition.id(),
				regionX, regionZ, definition.salt());
		if (StableHash.unit(identity.seed() ^ 0x3BACB2B415F019D1L) >= definition.selectionChance()) {
			return Optional.empty();
		}

		Random random = new Random(identity.seed());
		int size = definition.regionSpacingChunks() * 16;
		int x = Math.toIntExact((long) regionX * size + random.nextInt(size));
		int z = Math.toIntExact((long) regionZ * size + random.nextInt(size));
		int surface = site.surfaceY(x, z);
		if (surface < 0) return Optional.empty();

		DiamondPipeSettings settings = definition.pipe();
		String geome = site.geome(x, z, surface);
		if (definition.alignment() != AlignmentMode.MASSIVE_RARE) {
			boolean approximate = geome == null || "orespawn:cyano".equals(geome);
			if (approximate) {
				if (StableHash.unit(identity.seed() ^ 0x50B195A874F34111L)
						>= settings.legacySelectionScale()) return Optional.empty();
			} else if (!definition.geomes().contains(geome)) {
				return Optional.empty();
			}
		}

		List<Pipe> pipes = new ArrayList<>();
		int count = settings.pipeCount().sample(random);
		for (int index = 0; index < count; index++) {
			double angle = random.nextDouble() * Math.PI * 2;
			double distance = index == 0 ? 0 : settings.districtRadius() * (0.35 + random.nextDouble() * 0.65);
			int px = x + (int) Math.round(Math.cos(angle) * distance);
			int pz = z + (int) Math.round(Math.sin(angle) * distance);
			int localSurface = site.surfaceY(px, pz);
			int bottom = Math.max(definition.minimumY(), settings.rootY().sample(random));
			int top = Math.min(definition.maximumY(), localSurface - settings.surfaceCover().sample(random));
			if (localSurface < 0 || top - bottom < 16) continue;

			pipes.add(new Pipe(StableHash.combine(identity.seed(), index), px, pz, bottom, top,
					settings.upperDiameter().sample(random) * 0.5, settings.oreDensity()));
		}

		return pipes.isEmpty() ? Optional.empty() : Optional.of(new DiamondDistrict(identity, x, z, pipes));
	}

	public DepositIdentity identity() { return identity; }
	public int centerX() { return x; }
	public int centerZ() { return z; }
	public List<Pipe> pipes() { return pipes; }

	public double distanceSquared(int blockX, int blockZ) {
		double nearest = Double.POSITIVE_INFINITY;
		for (Pipe pipe : pipes) {
			double dx = (double) blockX - pipe.x;
			double dz = (double) blockZ - pipe.z;
			nearest = Math.min(nearest, dx * dx + dz * dz);
		}
		return nearest;
	}

	/** One irregular breccia envelope; only its mineralised cells become ore. */
	public static final class Pipe {
		private final long seed;
		private final int x;
		private final int z;
		private final int bottom;
		private final int top;
		private final double radius;
		private final double density;
		private final double phase;
		private final double driftX;
		private final double driftZ;

		private Pipe(long seed, int x, int z, int bottom, int top, double radius, double density) {
			this.seed = seed;
			this.x = x;
			this.z = z;
			this.bottom = bottom;
			this.top = top;
			this.radius = radius;
			this.density = density;
			phase = StableHash.unit(seed) * Math.PI * 2;
			driftX = Math.cos(phase) * radius * 0.25;
			driftZ = Math.sin(phase) * radius * 0.25;
		}

		public long seed() { return seed; }
		public int centerX() { return x; }
		public int centerZ() { return z; }
		public int bottomY() { return bottom; }
		public int topY() { return top; }
		public double upperRadius() { return radius; }

		public BlockBounds bounds() {
			int reach = (int) Math.ceil(radius * 1.5 + 4);
			return new BlockBounds(x - reach, bottom, z - reach, x + reach, top, z + reach);
		}

		public boolean inside(int blockX, int y, int blockZ) {
			if (y < bottom || y > top) return false;
			double t = (double) (y - bottom) / (top - bottom);
			double dx = blockX + 0.5 - x - driftX * (1 - t);
			double dz = blockZ + 0.5 - z - driftZ * (1 - t);
			double angle = Math.atan2(dz, dx);
			double wall = 1 + 0.10 * Math.sin(angle * 3 + phase + t * 2)
					+ 0.06 * Math.sin(angle * 7 - phase + t * 4);
			double r = (3 + (radius - 3) * Math.pow(t, 0.75)) * wall;
			if (dx * dx + dz * dz <= r * r) return true;

			// A narrow oblique feeder joins the lower third of the main pipe.
			if (t > 0.35) return false;
			double along = dx * Math.cos(phase) + dz * Math.sin(phase);
			double across = -dx * Math.sin(phase) + dz * Math.cos(phase);
			return Math.abs(across) <= 1.25 && along >= 0 && along <= radius * (0.35 - t);
		}

		public boolean isOreAt(int blockX, int y, int blockZ) {
			if (!inside(blockX, y, blockZ)) return false;
			double t = (double) (y - bottom) / (top - bottom);
			long pocket = cellSeed(seed, Math.floorDiv(blockX, 8), Math.floorDiv(y, 6), Math.floorDiv(blockZ, 8));
			double grade = (0.2 + 1.6 * StableHash.unit(pocket)) * (1 - 0.65 * t);
			return StableHash.unit(cellSeed(seed ^ 0x3C67B146EFA02491L, blockX, y, blockZ))
					< Math.min(1, density * grade);
		}
	}

	public static boolean placerPocket(long seed, int x, int z, int depth, double coverage) {
		long pocket = cellSeed(seed, Math.floorDiv(x, 8), 0, Math.floorDiv(z, 8));
		double threshold = coverage * (0.7 + 0.6 * StableHash.unit(pocket));
		return StableHash.unit(cellSeed(seed ^ 0x2A1384DC931165B7L,
				Math.floorDiv(x, 2), depth, Math.floorDiv(z, 2))) < threshold;
	}

	private static long cellSeed(long seed, int x, int y, int z) {
		return StableHash.combine(StableHash.combine(StableHash.combine(seed, x), y), z);
	}
}
