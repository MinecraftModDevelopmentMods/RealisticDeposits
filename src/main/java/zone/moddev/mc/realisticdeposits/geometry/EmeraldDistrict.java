package zone.moddev.mc.realisticdeposits.geometry;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Random;
import java.util.Set;

import zone.moddev.mc.realisticdeposits.model.DepositDefinition;
import zone.moddev.mc.realisticdeposits.model.EmeraldPocketSettings;

/** A small, immutable set of ore positions shared by all intersecting chunks. */
public final class EmeraldDistrict implements MineableDepositBody {
	public interface Site {
		boolean acceptsGeome(int x, int z);
		boolean eligible(int x, int y, int z, int cover);
	}

	private static final int POCKET_RADIUS = 3;
	private static final long RICH_CHANCE_SALT = 0x793ABEE3DF7494D1L;
	private static final long RICH_LAYOUT_SALT = 0x0B11A8D2984584B9L;
	private static final long LARGE_CHANCE_SALT = 0xA153BE71D8EFA90BL;
	private static final long LARGE_LAYOUT_SALT = 0x851A9B34679F03E1L;
	private final DepositIdentity identity;
	private final List<List<MinePoint>> pockets;
	private final List<MinePoint> ore;
	private final Map<Long, List<MinePoint>> oreChunks;
	private final Set<Long> positions;
	private final BlockBounds bounds;
	private final int strike;
	private final int cover;
	private final boolean rich;
	private final int zones;
	private final Map<Long, List<MinePoint>> trace;

	private EmeraldDistrict(DepositIdentity identity, List<List<MinePoint>> pockets, int strike, int cover,
			boolean rich, int zones, boolean veinTrace, Site site) {
		this.identity = identity;
		this.pockets = Collections.unmodifiableList(new ArrayList<>(pockets));
		this.strike = strike;
		this.cover = cover;
		this.rich = rich;
		this.zones = zones;
		List<MinePoint> points = new ArrayList<>();
		Map<Long, List<MinePoint>> slices = new LinkedHashMap<>();
		Set<Long> packed = new HashSet<>();
		int minX = Integer.MAX_VALUE, minY = 255, minZ = Integer.MAX_VALUE;
		int maxX = Integer.MIN_VALUE, maxY = 0, maxZ = Integer.MIN_VALUE;
		for (List<MinePoint> pocket : pockets) {
			for (MinePoint point : pocket) {
				points.add(point);
				slices.computeIfAbsent(MinePoint.chunkKey(point.x() >> 4, point.z() >> 4), key -> new ArrayList<>()).add(point);
				packed.add(point.packed());
				minX = Math.min(minX, point.x());
				minY = Math.min(minY, point.y());
				minZ = Math.min(minZ, point.z());
				maxX = Math.max(maxX, point.x());
				maxY = Math.max(maxY, point.y());
				maxZ = Math.max(maxZ, point.z());
			}
		}
		ore = Collections.unmodifiableList(points);
		slices.replaceAll((key, cells) -> Collections.unmodifiableList(cells));
		oreChunks = Collections.unmodifiableMap(slices);
		positions = Collections.unmodifiableSet(packed);
		bounds = new BlockBounds(minX, minY, minZ, maxX, maxY, maxZ);
		trace = veinTrace ? planTrace(site) : Collections.emptyMap();
	}

	public static int searchRadius(DepositDefinition definition) {
		return definition.emerald().maximumStrike() / 2 + POCKET_RADIUS + 2;
	}

	public static Optional<EmeraldDistrict> plan(DepositDefinition definition, long worldSeed,
			String dimension, int rx, int rz, Site site) {
		if (!definition.isEmerald() || !definition.enabled() || !definition.dimensions().contains(dimension)) {
			return Optional.empty();
		}
		DepositIdentity identity = new DepositIdentity(worldSeed, dimension, definition.id(), rx, rz, definition.salt());
		if (StableHash.unit(identity.seed() ^ 0x48EA4B0F83C16B99L) >= definition.selectionChance()) {
			EmeraldPocketSettings.LargeMine large = definition.emerald().large();
			return large != null && StableHash.unit(identity.seed() ^ LARGE_CHANCE_SALT) < large.chance()
					? fitLarge(definition, identity, rx, rz, large, site) : Optional.empty();
		}
		Random random = new Random(identity.seed());
		EmeraldPocketSettings settings = definition.emerald();
		int size = definition.regionSpacingChunks() * 16;
		long x = (long) rx * size + random.nextInt(size);
		long z = (long) rz * size + random.nextInt(size);
		// Keep coordinate packing and terrain sampling inside Minecraft's horizontal limits.
		if (Math.abs(x) > 29_999_900 || Math.abs(z) > 29_999_900
				|| !site.acceptsGeome((int) x, (int) z)) return Optional.empty();

		int count = settings.pocketCount().sample(random);
		int strike = settings.strikeLength().sample(random);
		int budget = settings.oreBlocks().sample(random);
		int cover = settings.surfaceCover().sample(random);
		int targetY = definition.minimumY() + random.nextInt(definition.maximumY() - definition.minimumY() + 1);
		double direction = random.nextDouble() * 2 * Math.PI;
		EmeraldPocketSettings.RichFind rich = settings.rich();
		if (rich != null && StableHash.unit(identity.seed() ^ RICH_CHANCE_SALT) < rich.chance()) {
			for (int attempt = 0; attempt < rich.fitAttempts(); attempt++) {
				double bearing = direction + attempt * Math.PI / 4;
				int px = (int) x, pz = (int) z;
				if (attempt >= 2) {
					px += (int) Math.round(Math.cos(bearing) * 24);
					pz += (int) Math.round(Math.sin(bearing) * 24);
				}
				if (Math.floorDiv(px, size) != rx || Math.floorDiv(pz, size) != rz) continue;
				Random richer = new Random(identity.seed() ^ RICH_LAYOUT_SALT);
				Optional<EmeraldDistrict> larger = fitDistrict(definition, identity, site, px, pz,
						rich.pocketCount().sample(richer), rich.strikeLength().sample(richer), rich.oreBlocks().sample(richer),
						cover, targetY, bearing, richer, true);
				if (larger.isPresent()) return larger;
			}
		}
		return fitDistrict(definition, identity, site, (int) x, (int) z, count, strike, budget,
				cover, targetY, direction, random, false);
	}

	private static Optional<EmeraldDistrict> fitDistrict(DepositDefinition definition, DepositIdentity identity,
			Site site, int x, int z, int count, int strike, int budget, int cover, int targetY,
			double direction, Random random, boolean rich) {
		List<List<MinePoint>> pockets = new ArrayList<>();
		Set<Long> reserved = new HashSet<>();
		for (int i = 0; i < count; i++) {
			double offset = strike * (i / (double) (count - 1) - 0.5);
			// Keep the full strike but bring one interior pair closer in the
			// linked-workings variant. The normal and captured older layouts
			// keep their original spacing and random sequence.
			if (rich && definition.emerald().rich().workings() && count >= 4) {
				int pair = (count - 2) / 2;
				if (i == pair || i == pair + 1) {
					double middle = strike * ((pair + 0.5) / (count - 1) - 0.5);
					double halfGap = Math.min(4.0, strike / (2.0 * (count - 1)));
					offset = middle + (i == pair ? -halfGap : halfGap);
				}
			}
			int px = x + (int) Math.round(Math.cos(direction) * offset);
			int pz = z + (int) Math.round(Math.sin(direction) * offset);
			if (rich && !site.acceptsGeome(px, pz)) return Optional.empty();
			int py = targetY + random.nextInt(7) - 3;
			int quota = budget / count + (i < budget % count ? 1 : 0);
			List<MinePoint> pocket = fit(definition, site, px, py, pz, cover, quota,
					StableHash.combine(identity.seed(), i), reserved);
			if (pocket.isEmpty()) return Optional.empty();
			pockets.add(Collections.unmodifiableList(pocket));
			reserve(pocket, reserved);
		}
		return Optional.of(new EmeraldDistrict(identity, pockets, strike, cover, rich, 0,
				definition.emerald().veinTrace(), site));
	}

	private static Optional<EmeraldDistrict> fitLarge(DepositDefinition definition, DepositIdentity identity,
			int rx, int rz, EmeraldPocketSettings.LargeMine large, Site site) {
		for (int attempt = 0; attempt < large.fitAttempts(); attempt++) {
			Optional<EmeraldDistrict> result = fitLargeAttempt(definition, identity, rx, rz, large, site, attempt);
			if (result.isPresent()) return result;
		}
		return Optional.empty();
	}

	private static Optional<EmeraldDistrict> fitLargeAttempt(DepositDefinition definition, DepositIdentity identity,
			int rx, int rz, EmeraldPocketSettings.LargeMine large, Site site, int attempt) {
		Random random = new Random(identity.seed() ^ LARGE_LAYOUT_SALT);
		int strike = large.strikeLength().sample(random), zones = large.zones().sample(random);
		int budget = large.oreBlocks().sample(random), cover = definition.emerald().surfaceCover().sample(random);
		int size = definition.regionSpacingChunks() * 16;
		// Keep every zone, cue and starter-working chunk clear of the region edge
		// and of neighbouring small/medium deposits that may cross that edge.
		int margin = strike / 2 + 32 + searchRadius(definition);
		if (size <= 2 * margin) return Optional.empty();
		long centerX = (long) rx * size + margin + random.nextInt(size - 2 * margin);
		long centerZ = (long) rz * size + margin + random.nextInt(size - 2 * margin);
		Random alternative = attempt == 0 ? null : new Random(StableHash.combine(identity.seed() ^ LARGE_LAYOUT_SALT, attempt));
		if (alternative != null) {
			centerX = (long) rx * size + margin + alternative.nextInt(size - 2 * margin);
			centerZ = (long) rz * size + margin + alternative.nextInt(size - 2 * margin);
		}
		if (Math.abs(centerX) > 29_999_800 || Math.abs(centerZ) > 29_999_800
				|| !site.acceptsGeome((int) centerX, (int) centerZ)) return Optional.empty();
		int depth = definition.maximumY() - definition.minimumY() + 1;
		int targetY = definition.minimumY() + depth / 3 + random.nextInt(Math.max(1, depth / 3));
		double direction = random.nextDouble() * 2 * Math.PI;
		if (alternative != null) direction = alternative.nextDouble() * 2 * Math.PI;
		double alongX = Math.cos(direction), alongZ = Math.sin(direction);
		boolean alongEastWest = Math.abs(alongX) >= Math.abs(alongZ);
		int stepX = alongEastWest ? (alongX >= 0 ? 4 : -4) : 0;
		int stepZ = alongEastWest ? 0 : (alongZ >= 0 ? 4 : -4);
		List<List<MinePoint>> pockets = new ArrayList<>(zones * 2);
		Set<Long> reserved = new HashSet<>();
		for (int zone = 0; zone < zones; zone++) {
			double offset = strike * (zone / (double) (zones - 1) - 0.5);
			int bend = random.nextInt(11) - 5;
			int x = (int) centerX + (int) Math.round(alongX * offset - alongZ * bend);
			int z = (int) centerZ + (int) Math.round(alongZ * offset + alongX * bend);
			int y = targetY + random.nextInt(13) - 6;
			// At least the pocket centres share a chunk: a complete safe starter
			// gallery may serve this zone without committing to a multi-chunk cut.
			if (alongEastWest) x = clampZoneAxis(x); else z = clampZoneAxis(z);
			for (int side = -1; side <= 1; side += 2) {
				int px = x + stepX * side, pz = z + stepZ * side;
				if (!site.acceptsGeome(px, pz)) return Optional.empty();
				int i = pockets.size(), count = zones * 2;
				int quota = budget / count + (i < budget % count ? 1 : 0);
				List<MinePoint> pocket = fit(definition, site, px, y, pz, cover, quota,
						StableHash.combine(identity.seed() ^ LARGE_LAYOUT_SALT, i), reserved);
				if (pocket.isEmpty()) return Optional.empty();
				pockets.add(Collections.unmodifiableList(pocket));
				reserve(pocket, reserved);
			}
		}
		return Optional.of(new EmeraldDistrict(identity, pockets, strike, cover, false, zones, true, site));
	}

	private static int clampZoneAxis(int coordinate) {
		return (coordinate & ~15) + Math.max(5, Math.min(10, coordinate & 15));
	}

	private static void reserve(List<MinePoint> pocket, Set<Long> reserved) {
		for (MinePoint point : pocket) {
			// Leave at least one block between pockets even after rounding the strike.
			for (int dx = -1; dx <= 1; dx++) for (int dy = -1; dy <= 1; dy++) for (int dz = -1; dz <= 1; dz++) {
				reserved.add(new MinePoint(point.x() + dx, point.y() + dy, point.z() + dz).packed());
			}
		}
	}

	// A one-block-wide non-ore trace joins the fitted pockets. Ore can interrupt
	// the trace, but never gets replaced by it.
	private Map<Long, List<MinePoint>> planTrace(Site site) {
		Map<Long, List<MinePoint>> result = new LinkedHashMap<>();
		Set<Long> seen = new HashSet<>();
		MinePoint from = middle(pockets.get(0));
		for (int i = 1; i < pockets.size(); i++) {
			MinePoint to = middle(pockets.get(i));
			int dx = to.x() - from.x(), dy = to.y() - from.y(), dz = to.z() - from.z();
			int steps = Math.max(Math.abs(dx), Math.max(Math.abs(dy), Math.abs(dz)));
			int x = from.x(), y = from.y(), z = from.z();
			tracePoint(result, seen, site, x, y, z);
			for (int s = 1; s <= steps; s++) {
				int nx = from.x() + (int) Math.round(dx * (s / (double) steps));
				int ny = from.y() + (int) Math.round(dy * (s / (double) steps));
				int nz = from.z() + (int) Math.round(dz * (s / (double) steps));
				if (x != nx) tracePoint(result, seen, site, x = nx, y, z);
				if (z != nz) tracePoint(result, seen, site, x, y, z = nz);
				if (y != ny) tracePoint(result, seen, site, x, y = ny, z);
			}
			from = to;
		}
		result.replaceAll((chunk, cells) -> Collections.unmodifiableList(cells));
		return Collections.unmodifiableMap(result);
	}

	private void tracePoint(Map<Long, List<MinePoint>> result, Set<Long> seen, Site site, int x, int y, int z) {
		MinePoint point = new MinePoint(x, y, z);
		long key = point.packed();
		if (!positions.contains(key) && seen.add(key) && site.eligible(x, y, z, cover)) {
			result.computeIfAbsent(MinePoint.chunkKey(x >> 4, z >> 4), unused -> new ArrayList<>()).add(point);
		}
	}

	private static MinePoint middle(List<MinePoint> pocket) {
		long x = 0, y = 0, z = 0;
		for (MinePoint point : pocket) {
			x += point.x();
			y += point.y();
			z += point.z();
		}
		int n = pocket.size();
		return new MinePoint((int) Math.round(x / (double) n), (int) Math.round(y / (double) n),
				(int) Math.round(z / (double) n));
	}

	private static List<MinePoint> fit(DepositDefinition definition, Site site, int x, int y,
			int z, int cover, int quota, long salt, Set<Long> reserved) {
		int target = Math.max(definition.minimumY(), Math.min(definition.maximumY(), y));
		List<MinePoint> offsets = new ArrayList<>();
		for (int dx = -POCKET_RADIUS; dx <= POCKET_RADIUS; dx++) {
			for (int dz = -POCKET_RADIUS; dz <= POCKET_RADIUS; dz++) {
				for (int dy = -2; dy <= 2; dy++) {
					if (dx * dx + dz * dz + 2 * dy * dy <= 11) offsets.add(new MinePoint(dx, dy, dz));
				}
			}
		}
		offsets.sort(Comparator.comparingDouble(point -> point.x() * point.x() + point.z() * point.z()
				+ 2 * point.y() * point.y() + 7 * StableHash.unit(StableHash.combine(salt, point.packed()))));
		for (int distance = 0; distance <= definition.maximumY() - definition.minimumY(); distance++) {
			for (int direction = 1; direction >= -1; direction -= 2) {
				int center = target + direction * distance;
				if (center < definition.minimumY() || center > definition.maximumY()) continue;
				List<MinePoint> result = new ArrayList<>(quota);
				for (MinePoint offset : offsets) {
					MinePoint point = new MinePoint(x + offset.x(), center + offset.y(), z + offset.z());
					if (point.y() < definition.minimumY() || point.y() > definition.maximumY()
							|| reserved.contains(point.packed()) || !site.eligible(point.x(), point.y(), point.z(), cover)) {
						continue;
					}
					result.add(point);
					if (result.size() == quota) return result;
				}
				if (distance == 0) break;
			}
		}
		return Collections.emptyList();
	}

	public List<List<MinePoint>> pockets() { return pockets; }
	public boolean rich() { return rich; }
	public boolean large() { return zones != 0; }
	public int zones() { return zones; }
	public boolean linkedWorkings(DepositDefinition definition) {
		return large() || rich && definition.emerald().rich().workings();
	}
	public List<MinePoint> oreInChunk(int chunkX, int chunkZ) {
		return oreChunks.getOrDefault(MinePoint.chunkKey(chunkX, chunkZ), Collections.emptyList());
	}
	public List<MinePoint> traceInChunk(int chunkX, int chunkZ) {
		return trace.getOrDefault(MinePoint.chunkKey(chunkX, chunkZ), Collections.emptyList());
	}
	public List<MinePoint> ore() { return ore; }
	public int surfaceCover() { return cover; }
	@Override public DepositIdentity identity() { return identity; }
	@Override public int centerX() { return ore.get(0).x(); }
	@Override public int centerY() { return ore.get(0).y(); }
	@Override public int centerZ() { return ore.get(0).z(); }
	@Override public int strikeLength() { return strike; }
	@Override public BlockBounds bounds() { return bounds; }
	@Override public int minimumCandidateYAt(int x, int z) { return bounds.minimumY(); }
	@Override public int maximumCandidateYAt(int x, int z) { return bounds.maximumY(); }
	@Override public boolean isOreAt(int x, int y, int z) {
		return y >= bounds.minimumY() && y <= bounds.maximumY() && positions.contains(new MinePoint(x, y, z).packed());
	}

	@Override
	public MinePoint miningRoutePoint(double along, double lateralOffset) {
		// The generic planner uses this only to nominate nearby chunks. Emerald workings choose their own landing.
		return new MinePoint(centerX(), Math.max(9, centerY() - 1), centerZ());
	}
}
