package zone.moddev.mc.realisticdeposits.geometry;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.Random;

import zone.moddev.mc.realisticdeposits.model.AlignmentMode;
import zone.moddev.mc.realisticdeposits.model.DepositDefinition;
import zone.moddev.mc.realisticdeposits.model.LapisLensSettings;

/** Compact mineralised lenses, fitted before any chunk is written. */
public final class LapisDistrict implements MineableDepositBody {
	public interface Site {
		int surfaceY(int x, int z);
		String geome(int x, int z, int surfaceY);

		/** Returns a suitable lens centre, or NaN when the host interval is too thin. */
		double fitHeight(int x, int z, double targetY, int thickness);
		default boolean hasPhysicalStrata() { return false; }
	}

	private final DepositIdentity identity;
	private final List<Lens> lenses;
	private final BlockBounds bounds;
	private final int cover;

	private LapisDistrict(DepositIdentity identity, List<Lens> lenses, int cover) {
		this.identity = identity;
		this.lenses = Collections.unmodifiableList(new ArrayList<>(lenses));
		this.cover = cover;

		int minX = Integer.MAX_VALUE;
		int minY = 255;
		int minZ = Integer.MAX_VALUE;
		int maxX = Integer.MIN_VALUE;
		int maxY = 0;
		int maxZ = Integer.MIN_VALUE;

		for (Lens lens : lenses) {
			minX = Math.min(minX, lens.bounds.minimumX());
			minY = Math.min(minY, lens.bounds.minimumY());
			minZ = Math.min(minZ, lens.bounds.minimumZ());
			maxX = Math.max(maxX, lens.bounds.maximumX());
			maxY = Math.max(maxY, lens.bounds.maximumY());
			maxZ = Math.max(maxZ, lens.bounds.maximumZ());
		}

		bounds = new BlockBounds(minX, minY, minZ, maxX, maxY, maxZ);
	}

	public static Optional<LapisDistrict> plan(DepositDefinition definition, long seed,
			String dimension, int regionX, int regionZ, Site site) {
		if (!definition.isLapis() || !definition.enabled() || !definition.dimensions().contains(dimension)) {
			return Optional.empty();
		}

		DepositIdentity identity = new DepositIdentity(seed, dimension, definition.id(),
				regionX, regionZ, definition.salt());

		if (StableHash.unit(identity.seed() ^ 0x35718A42CE906B1DL) >= definition.selectionChance()) {
			return Optional.empty();
		}

		Random random = new Random(identity.seed());
		LapisLensSettings settings = definition.lapis();
		int size = definition.regionSpacingChunks() * 16;
		int originX = Math.toIntExact((long) regionX * size);
		int originZ = Math.toIntExact((long) regionZ * size);
		int x = Math.toIntExact((long) originX + random.nextInt(size));
		int z = Math.toIntExact((long) originZ + random.nextInt(size));
		Optional<LapisDistrict> original = planAt(definition, identity, settings, site, x, z, random);
		if (original.isPresent() || !site.hasPhysicalStrata() || settings.marbleSearchRadius() == 0) return original;

		// Look for nearby natural marble only after the original proposal fails.
		// An alternative stays in the same region and uses the same lens settings.
		double bearing = StableHash.unit(identity.seed() ^ 0x339F7B6EC5F255DAL) * Math.PI * 2;
		for (int ring = 1; ring <= 2; ring++) {
			double radius = ring * settings.marbleSearchRadius() / 2.0;
			for (int step = 0; step < 8; step++) {
				double angle = bearing + step * Math.PI / 4;
				int px = x + (int) Math.round(radius * Math.cos(angle));
				int pz = z + (int) Math.round(radius * Math.sin(angle));
				if (Math.floorDiv(px, size) != regionX || Math.floorDiv(pz, size) != regionZ) continue;
				Random retry = new Random(identity.seed());
				retry.nextInt(size);
				retry.nextInt(size);
				Optional<LapisDistrict> fitted = planAt(definition, identity, settings, site, px, pz, retry);
				if (fitted.isPresent()) return fitted;
			}
		}
		return Optional.empty();
	}

	private static Optional<LapisDistrict> planAt(DepositDefinition definition, DepositIdentity identity,
			LapisLensSettings settings, Site site, int x, int z, Random random) {
		int surface = site.surfaceY(x, z);
		int cover = settings.surfaceCover().sample(random);
		int maximum = Math.min(definition.maximumY(), surface - cover);

		if (surface < 0 || maximum - definition.minimumY() < 6) {
			return Optional.empty();
		}

		String geome = site.geome(x, z, surface);
		if (definition.alignment() != AlignmentMode.MASSIVE_RARE) {
			if (geome == null || "orespawn:cyano".equals(geome)) {
				if (StableHash.unit(identity.seed() ^ 0x76DF1939812B072AL) >= settings.legacySelectionScale()) {
					return Optional.empty();
				}
			} else if (!definition.geomes().contains(geome)) {
				return Optional.empty();
			}
		}

		double angle = random.nextDouble() * Math.PI * 2;
		double target = definition.minimumY() + 3
				+ random.nextInt(maximum - definition.minimumY() - 5);
		int count = settings.lensCount().sample(random);
		List<Lens> lenses = new ArrayList<>();

		for (int index = 0; index < count; index++) {
			int offset = index == 0 ? 0 : (index % 2 == 0 ? -1 : 1) * (14 + random.nextInt(16));
			int lensX = x - (int) Math.round(Math.sin(angle) * offset);
			int lensZ = z + (int) Math.round(Math.cos(angle) * offset);
			int length = settings.strikeLength().sample(random);
			int width = settings.width().sample(random);
			int thickness = settings.thickness().sample(random);
			double fitted = site.fitHeight(lensX, lensZ, target, thickness);

			if (!Double.isFinite(fitted)) continue;

			Lens lens = new Lens(lensX, lensZ, fitted, length, width, thickness,
					angle, StableHash.combine(identity.seed(), index), definition, cover, site);
			if (lens.productive()) lenses.add(lens);
		}

		return lenses.isEmpty() ? Optional.empty() : Optional.of(new LapisDistrict(identity, lenses, cover));
	}

	@Override public DepositIdentity identity() { return identity; }
	@Override public int centerX() { return lenses.get(0).x; }
	@Override public int centerY() { return lenses.get(0).routePoint(0, 0).y(); }
	@Override public int centerZ() { return lenses.get(0).z; }
	@Override public int strikeLength() { return lenses.get(0).length; }
	@Override public BlockBounds bounds() { return bounds; }
	public List<Lens> lenses() { return lenses; }
	public int surfaceCover() { return cover; }

	@Override
	public boolean isOreAt(int x, int y, int z) {
		for (Lens lens : lenses) {
			if (lens.isOreAt(x, y, z)) return true;
		}

		return false;
	}

	@Override
	public int minimumCandidateYAt(int x, int z) {
		int minimum = 256;

		for (Lens lens : lenses) {
			double height = lens.heightAt(x + 0.5, z + 0.5);
			if (Double.isFinite(height)) minimum = Math.min(minimum, (int) Math.floor(height) - lens.thickness);
		}

		return Math.max(bounds.minimumY(), minimum);
	}

	@Override
	public int maximumCandidateYAt(int x, int z) {
		int maximum = -1;

		for (Lens lens : lenses) {
			double height = lens.heightAt(x + 0.5, z + 0.5);
			if (Double.isFinite(height)) maximum = Math.max(maximum, (int) Math.ceil(height) + lens.thickness);
		}

		return Math.min(bounds.maximumY(), maximum);
	}

	@Override
	public MinePoint miningRoutePoint(double along, double lateralOffset) {
		// Work the lower edge of the lens, leaving the richer roof and side pods.
		return lenses.get(0).routePoint(along, lateralOffset);
	}

	public static final class Lens {
		private static final int GRID_STEP = 8;
		private final int x;
		private final int z;
		private final int length;
		private final int width;
		private final int thickness;
		private final double cosine;
		private final double sine;
		private final double phase;
		private final int gridX;
		private final int gridZ;
		private final int columns;
		private final int rows;
		private final double[] heights;
		private final BlockBounds bounds;

		private Lens(int x, int z, double centreY, int length, int width, int thickness,
				double angle, long seed, DepositDefinition definition, int cover, Site site) {
			this.x = x;
			this.z = z;
			this.length = length;
			this.width = width;
			this.thickness = thickness;
			cosine = Math.cos(angle);
			sine = Math.sin(angle);
			phase = StableHash.unit(seed) * Math.PI * 2;

			int reachX = (int) Math.ceil(Math.abs(cosine) * length / 2 + Math.abs(sine) * (width / 2 + 3));
			int reachZ = (int) Math.ceil(Math.abs(sine) * length / 2 + Math.abs(cosine) * (width / 2 + 3));
			bounds = new BlockBounds(x - reachX, definition.minimumY(), z - reachZ,
					x + reachX, definition.maximumY(), z + reachZ);
			gridX = bounds.minimumX() - GRID_STEP;
			gridZ = bounds.minimumZ() - GRID_STEP;
			columns = (bounds.maximumX() - gridX) / GRID_STEP + 3;
			rows = (bounds.maximumZ() - gridZ) / GRID_STEP + 3;
			heights = new double[columns * rows];

			for (int gz = 0; gz < rows; gz++) {
				for (int gx = 0; gx < columns; gx++) {
					int blockX = gridX + gx * GRID_STEP;
					int blockZ = gridZ + gz * GRID_STEP;
					double along = (blockX - x) * cosine + (blockZ - z) * sine;
					double target = centreY + 1.5 * Math.sin(along / 37 + phase)
							- 1.5 * Math.sin(phase);
					double fitted = site.fitHeight(blockX, blockZ, target, thickness);
					int top = Math.min(definition.maximumY(), site.surfaceY(blockX, blockZ) - cover);

					if (Math.abs(fitted - centreY) > 12 || fitted - thickness / 2.0 < definition.minimumY()
							|| fitted + thickness / 2.0 > top) fitted = Double.NaN;

					heights[gx + gz * columns] = fitted;
				}
			}
		}

		public int length() { return length; }
		public int width() { return width; }
		public int thickness() { return thickness; }

		private boolean productive() {
			for (int offset = -1; offset <= 1; offset++) {
				double along = offset * length * 0.25;
				if (!Double.isFinite(heightAt(x + along * cosine, z + along * sine))) return false;
			}

			return true;
		}

		private double heightAt(double blockX, double blockZ) {
			double gx = (blockX - gridX) / GRID_STEP;
			double gz = (blockZ - gridZ) / GRID_STEP;
			int ix = (int) Math.floor(gx);
			int iz = (int) Math.floor(gz);

			if (ix < 0 || iz < 0 || ix + 1 >= columns || iz + 1 >= rows) return Double.NaN;

			double dx = gx - ix;
			double dz = gz - iz;

			return (heights[ix + iz * columns] * (1 - dx) + heights[ix + 1 + iz * columns] * dx) * (1 - dz)
					+ (heights[ix + (iz + 1) * columns] * (1 - dx)
							+ heights[ix + 1 + (iz + 1) * columns] * dx) * dz;
		}

		private double bend(double along) {
			return 1.6 * Math.sin(along / 23 + phase);
		}

		private boolean isOreAt(int blockX, int blockY, int blockZ) {
			double along = (blockX + 0.5 - x) * cosine + (blockZ + 0.5 - z) * sine;
			double across = -(blockX + 0.5 - x) * sine + (blockZ + 0.5 - z) * cosine - bend(along);
			double a = along / (length * 0.5);
			double b = across / (width * 0.5);
			double edge = 1 - a * a - b * b;

			if (edge <= 0) return false;

			double height = heightAt(blockX + 0.5, blockZ + 0.5);
			if (!Double.isFinite(height)) return false;

			int depth = Math.max(1, (int) Math.ceil(thickness * Math.sqrt(edge)));
			int bottom = (int) Math.floor(height - (depth - 1) / 2.0);
			if (blockY < bottom || blockY >= bottom + depth) return false;

			double rich = 0.5 + 0.5 * Math.cos(along / 13 + phase);
			double halfWidth = 0.9 + width * 0.5 * Math.pow(rich, 3) * Math.sqrt(edge);

			return Math.abs(across) <= halfWidth;
		}

		private MinePoint routePoint(double along, double lateralOffset) {
			double forward = Math.max(-length * 0.35, Math.min(length * 0.35, along));
			double across = bend(forward) - width * 0.3 + lateralOffset;
			int blockX = (int) Math.floor(x + forward * cosine - across * sine);
			int blockZ = (int) Math.floor(z + forward * sine + across * cosine);
			double height = heightAt(blockX + 0.5, blockZ + 0.5);
			if (!Double.isFinite(height)) height = heightAt(x, z);

			return new MinePoint(blockX, (int) Math.floor(height) - 2, blockZ);
		}
	}
}
