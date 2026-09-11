package zone.moddev.mc.realisticdeposits.geometry;

import java.util.Random;

import zone.moddev.mc.realisticdeposits.model.StratiformSeamSettings;

/**
 * Deterministic folded/tabular orebody. It models strike, dip, lateral bends,
 * thickness variation, edge taper, irregular ore splays, subparallel lenses,
 * fault displacement and a discontinuous lower-grade halo without Minecraft
 * world access.
 */
public final class StratiformSeamBody {
	private static final long STRUCTURE_SALT = 0x3C6EF372FE94F82BL;
	private static final int MAXIMUM_FAULT_VERTICAL_OFFSET = 12;
	private static final int EMPTY_MINIMUM_Y = 1;
	private static final int EMPTY_MAXIMUM_Y = 0;

	private final DepositIdentity identity;
	private final int centerX;
	private final int centerY;
	private final int centerZ;
	private final int strikeLength;
	private final int downDipWidth;
	private final double strikeCosine;
	private final double strikeSine;
	private final double dipTangent;
	private final double minimumThickness;
	private final double maximumThickness;
	private final double bendAmplitude;
	private final double bendWavelength;
	private final double foldAmplitude;
	private final double foldWavelength;
	private final double bendPhase;
	private final double foldPhase;
	private final double thicknessPhase;
	private final double corePhase;
	private final double density;
	private final double edgeFalloff;
	private final int minimumY;
	private final int maximumY;

	private final double[] faultAlong;
	private final double[] faultHalfGap;
	private final double[] segmentLateralOffset;
	private final double[] segmentVerticalOffset;
	private final double maximumFaultLateralOffset;
	private final double maximumFaultVerticalOffset;

	private final double[] splayStartAlong;
	private final double[] splayStartAcross;
	private final double[] splayEndAlong;
	private final double[] splayEndAcross;
	private final double[] splayHalfWidth;

	private final double[] lensCenterAlong;
	private final double[] lensCenterAcross;
	private final double[] lensHalfLength;
	private final double[] lensHalfWidth;

	private final BlockBounds bounds;
	private final boolean verticallyTruncated;

	StratiformSeamBody(DepositIdentity identity, int centerX, int centerY, int centerZ,
			int strikeLength, int downDipWidth, double strikeRadians, double dipDegrees,
			StratiformSeamSettings settings, double bendPhase, double foldPhase,
			double thicknessPhase, int minimumY, int maximumY, StructureLayout structure) {
		this.identity = identity;
		this.centerX = centerX;
		this.centerY = centerY;
		this.centerZ = centerZ;
		this.strikeLength = strikeLength;
		this.downDipWidth = downDipWidth;
		this.strikeCosine = Math.cos(strikeRadians);
		this.strikeSine = Math.sin(strikeRadians);
		this.dipTangent = Math.tan(Math.toRadians(dipDegrees));
		this.minimumThickness = settings.minimumThickness();
		this.maximumThickness = settings.maximumThickness();
		this.bendAmplitude = settings.bendAmplitude();
		this.bendWavelength = settings.bendWavelength();
		this.foldAmplitude = settings.foldAmplitude();
		this.foldWavelength = settings.foldWavelength();
		this.bendPhase = bendPhase;
		this.foldPhase = foldPhase;
		this.thicknessPhase = thicknessPhase;
		this.corePhase = StableHash.unit(identity.seed() ^ 0x6A09E667F3BCC909L)
				* Math.PI * 2.0D;
		this.density = settings.density();
		this.edgeFalloff = settings.edgeFalloff();
		this.minimumY = minimumY;
		this.maximumY = maximumY;

		faultAlong = structure.faultAlong;
		faultHalfGap = structure.faultHalfGap;
		segmentLateralOffset = structure.segmentLateralOffset;
		segmentVerticalOffset = structure.segmentVerticalOffset;
		maximumFaultLateralOffset = structure.maximumLateralOffset;
		maximumFaultVerticalOffset = structure.maximumVerticalOffset;
		splayStartAlong = structure.splayStartAlong;
		splayStartAcross = structure.splayStartAcross;
		splayEndAlong = structure.splayEndAlong;
		splayEndAcross = structure.splayEndAcross;
		splayHalfWidth = structure.splayHalfWidth;
		lensCenterAlong = structure.lensCenterAlong;
		lensCenterAcross = structure.lensCenterAcross;
		lensHalfLength = structure.lensHalfLength;
		lensHalfWidth = structure.lensHalfWidth;

		double horizontalRadius = strikeLength * 0.5D + downDipWidth * 0.5D
				+ bendAmplitude + maximumFaultLateralOffset + 3.0D;
		double verticalRadius = Math.abs(dipTangent) * downDipWidth * 0.5D
				+ foldAmplitude + maximumThickness * 0.5D
				+ maximumFaultVerticalOffset + 3.0D;
		int rawMinimumY = (int) Math.floor(centerY - verticalRadius);
		int rawMaximumY = (int) Math.ceil(centerY + verticalRadius);
		verticallyTruncated = rawMinimumY < minimumY || rawMaximumY > maximumY;
		bounds = new BlockBounds(
				(int) Math.floor(centerX - horizontalRadius),
				Math.max(minimumY, rawMinimumY),
				(int) Math.floor(centerZ - horizontalRadius),
				(int) Math.ceil(centerX + horizontalRadius),
				Math.min(maximumY, rawMaximumY),
				(int) Math.ceil(centerZ + horizontalRadius));
	}

	public DepositIdentity identity() { return identity; }
	public int centerX() { return centerX; }
	public int centerY() { return centerY; }
	public int centerZ() { return centerZ; }
	public int strikeLength() { return strikeLength; }
	public int downDipWidth() { return downDipWidth; }
	public BlockBounds bounds() { return bounds; }

	/** Lowest Y that can contain this body's folded horizon in this column. */
	public int minimumCandidateYAt(int x, int z) {
		double along = alongAt(x, z);
		double across = acrossAt(x, z);
		if (!insideHorizontalEnvelope(along, across)) return EMPTY_MINIMUM_Y;
		double planeY = planeYAt(along, adjustedAcrossAt(along, across));
		double halfThickness = thicknessAt(along) * 0.5D;
		return Math.max(minimumY, (int) Math.floor(planeY - halfThickness - 0.5D));
	}

	/** Highest Y that can contain this body's folded horizon in this column. */
	public int maximumCandidateYAt(int x, int z) {
		double along = alongAt(x, z);
		double across = acrossAt(x, z);
		if (!insideHorizontalEnvelope(along, across)) return EMPTY_MAXIMUM_Y;
		double planeY = planeYAt(along, adjustedAcrossAt(along, across));
		double halfThickness = thicknessAt(along) * 0.5D;
		return Math.min(maximumY, (int) Math.ceil(planeY + halfThickness - 0.5D));
	}

	/** Returns a normalized geological grade, or zero outside the seam. */
	public double gradeAt(int x, int y, int z) {
		if (y < minimumY || y > maximumY) return 0.0D;
		double along = alongAt(x, z);
		double across = acrossAt(x, z);
		if (!insideHorizontalEnvelope(along, across)) return 0.0D;
		double bentAcross = adjustedAcrossAt(along, across);
		double halfLength = strikeLength * 0.5D;
		double halfWidth = downDipWidth * 0.5D;
		double planeY = planeYAt(along, bentAcross);
		double thickness = thicknessAt(along);
		double vertical = Math.abs(y + 0.5D - planeY) / Math.max(0.25D, thickness * 0.5D);
		if (vertical > 1.0D) return 0.0D;
		double alongEdge = 1.0D - Math.abs(along) / halfLength;
		double acrossEdge = 1.0D - Math.abs(bentAcross) / halfWidth;
		double edge = Math.min(alongEdge, acrossEdge);
		double edgeWeight = edgeFalloff == 0.0D ? 1.0D
				: clamp(edge / Math.max(0.0001D, edgeFalloff));
		return clamp((1.0D - vertical * vertical) * edgeWeight * faultGradeWeight(along));
	}

	/**
	 * Deterministic mineralization sampling. Analytic splays and lenses form
	 * followable mining routes while the coordinate-stable grade mask forms the
	 * sparse halo, including clues around displaced fault segments.
	 */
	public boolean isOreAt(int x, int y, int z) {
		double grade = gradeAt(x, y, z);
		if (grade <= 0.0D) return false;
		if (isConnectedCoreAt(x, y, z, grade)) return true;
		long coordinate = StableHash.combine(identity.seed(), x);
		coordinate = StableHash.combine(coordinate, y);
		coordinate = StableHash.combine(coordinate, z);
		double threshold = density * (0.35D + 0.65D * grade);
		if (isNearFault(alongAt(x, z), 7.0D)) {
			threshold = Math.max(threshold, density * 0.56D * Math.max(0.35D, grade));
		}
		return StableHash.unit(coordinate) < threshold;
	}

	private boolean isConnectedCoreAt(int x, int y, int z, double grade) {
		if (grade < 0.08D) return false;
		double along = alongAt(x, z);
		if (insideFaultGap(along)) return false;
		double bentAcross = adjustedAcrossAt(along, acrossAt(x, z));
		double planeY = planeYAt(along, bentAcross);
		double thickness = thicknessAt(along);
		double coreHalfHeight = Math.min(thickness * 0.5D,
				Math.max(0.92D, thickness * (0.38D + density * 0.50D)));
		if (Math.abs(y + 0.5D - planeY) > coreHalfHeight) return false;

		double trunkHalfWidth = 4.0D + Math.min(5.0D, density * 18.0D);
		if (Math.abs(bentAcross - meanderAt(along)) <= trunkHalfWidth) return true;

		for (int i = 0; i < splayStartAlong.length; i++) {
			if (distanceSquaredToSegment(along, bentAcross,
					splayStartAlong[i], splayStartAcross[i],
					splayEndAlong[i], splayEndAcross[i])
					<= splayHalfWidth[i] * splayHalfWidth[i]) return true;
		}
		for (int i = 0; i < lensCenterAlong.length; i++) {
			double normalizedAlong = (along - lensCenterAlong[i]) / lensHalfLength[i];
			double normalizedAcross = (bentAcross - lensCenterAcross[i]) / lensHalfWidth[i];
			if (normalizedAlong * normalizedAlong + normalizedAcross * normalizedAcross <= 1.0D) {
				return true;
			}
		}
		return false;
	}

	private double alongAt(int x, int z) {
		double dx = x + 0.5D - centerX;
		double dz = z + 0.5D - centerZ;
		return dx * strikeCosine + dz * strikeSine;
	}

	private double acrossAt(int x, int z) {
		double dx = x + 0.5D - centerX;
		double dz = z + 0.5D - centerZ;
		return -dx * strikeSine + dz * strikeCosine;
	}

	private boolean insideHorizontalEnvelope(double along, double across) {
		double halfLength = strikeLength * 0.5D;
		double halfWidth = downDipWidth * 0.5D;
		if (Math.abs(along) > halfLength
				|| Math.abs(across) > halfWidth + bendAmplitude + maximumFaultLateralOffset) {
			return false;
		}
		return Math.abs(adjustedAcrossAt(along, across)) <= halfWidth;
	}

	private double adjustedAcrossAt(double along, double across) {
		return across - segmentLateralOffset[segmentAt(along)] - bendAmplitude
				* Math.sin((along / bendWavelength) * Math.PI * 2.0D + bendPhase);
	}

	private double planeYAt(double along, double bentAcross) {
		return centerY + segmentVerticalOffset[segmentAt(along)] + dipTangent * bentAcross
				+ foldAmplitude
				* Math.sin((along / foldWavelength) * Math.PI * 2.0D + foldPhase);
	}

	private double thicknessAt(double along) {
		double thicknessWave = 0.5D + 0.5D
				* Math.sin((along / (foldWavelength * 0.73D)) * Math.PI * 2.0D
						+ thicknessPhase);
		return minimumThickness + (maximumThickness - minimumThickness) * thicknessWave;
	}

	private double meanderAt(double along) {
		return downDipWidth * 0.08D
				* Math.sin((along / Math.max(24.0D, bendWavelength * 0.82D))
						* Math.PI * 2.0D + corePhase);
	}

	private int segmentAt(double along) {
		int segment = 0;
		while (segment < faultAlong.length && along >= faultAlong[segment]) segment++;
		return segment;
	}

	private boolean insideFaultGap(double along) {
		for (int i = 0; i < faultAlong.length; i++) {
			if (Math.abs(along - faultAlong[i]) <= faultHalfGap[i]) return true;
		}
		return false;
	}

	private boolean isNearFault(double along, double additionalDistance) {
		for (int i = 0; i < faultAlong.length; i++) {
			if (Math.abs(along - faultAlong[i]) <= faultHalfGap[i] + additionalDistance) return true;
		}
		return false;
	}

	private double faultGradeWeight(double along) {
		for (int i = 0; i < faultAlong.length; i++) {
			double distance = Math.abs(along - faultAlong[i]);
			if (distance <= faultHalfGap[i]) return 0.22D;
			double fringe = distance - faultHalfGap[i];
			if (fringe < 6.0D) return 0.45D + fringe * (0.55D / 6.0D);
		}
		return 1.0D;
	}

	private static double distanceSquaredToSegment(double x, double z,
			double startX, double startZ, double endX, double endZ) {
		double deltaX = endX - startX;
		double deltaZ = endZ - startZ;
		double lengthSquared = deltaX * deltaX + deltaZ * deltaZ;
		double progress = lengthSquared == 0.0D ? 0.0D
				: ((x - startX) * deltaX + (z - startZ) * deltaZ) / lengthSquared;
		progress = Math.max(0.0D, Math.min(1.0D, progress));
		double nearestX = startX + progress * deltaX;
		double nearestZ = startZ + progress * deltaZ;
		double distanceX = x - nearestX;
		double distanceZ = z - nearestZ;
		return distanceX * distanceX + distanceZ * distanceZ;
	}

	private static double clamp(double value) {
		return Math.max(0.0D, Math.min(1.0D, value));
	}

	static StructureLayout createStructure(DepositIdentity identity, int strikeLength,
			int downDipWidth, StratiformSeamSettings settings) {
		Random random = new Random(StableHash.mix(identity.seed() ^ STRUCTURE_SALT));
		double halfLength = strikeLength * 0.5D;
		double halfWidth = downDipWidth * 0.5D;

		int faultCount = 2 + random.nextInt(2);
		double[] faultAlong = new double[faultCount];
		double[] faultHalfGap = new double[faultCount];
		double[] lateralOffsets = new double[faultCount + 1];
		double[] verticalOffsets = new double[faultCount + 1];
		double faultCell = strikeLength / (double) (faultCount + 1);
		double maximumLateral = 0.0D;
		double maximumVertical = 0.0D;
		for (int i = 0; i < faultCount; i++) {
			double nominal = -halfLength + faultCell * (i + 1);
			faultAlong[i] = nominal + (random.nextDouble() - 0.5D) * faultCell * 0.42D;
			faultHalfGap[i] = 2.0D + random.nextDouble() * 4.0D;
			lateralOffsets[i + 1] = nextOffset(random, lateralOffsets[i], 3.0D, 8.0D, 10.0D);
			verticalOffsets[i + 1] = nextOffset(random, verticalOffsets[i], 4.0D, 12.0D,
					MAXIMUM_FAULT_VERTICAL_OFFSET);
			maximumLateral = Math.max(maximumLateral, Math.abs(lateralOffsets[i + 1]));
			maximumVertical = Math.max(maximumVertical, Math.abs(verticalOffsets[i + 1]));
		}

		int splayCount = 5 + random.nextInt(3);
		double[] splayStartAlong = new double[splayCount];
		double[] splayStartAcross = new double[splayCount];
		double[] splayEndAlong = new double[splayCount];
		double[] splayEndAcross = new double[splayCount];
		double[] splayHalfWidth = new double[splayCount];
		double splayCell = strikeLength / (double) (splayCount + 1);
		int firstSide = random.nextBoolean() ? 1 : -1;
		double corePhase = StableHash.unit(identity.seed() ^ 0x6A09E667F3BCC909L)
				* Math.PI * 2.0D;
		for (int i = 0; i < splayCount; i++) {
			double nominal = -halfLength + splayCell * (i + 1);
			double anchor = nominal + (random.nextDouble() - 0.5D) * splayCell * 0.68D;
			double startAcross = halfWidth * 0.16D
					* Math.sin((anchor / Math.max(24.0D, settings.bendWavelength() * 0.82D))
							* Math.PI * 2.0D + corePhase);
			int side = ((i + (firstSide > 0 ? 0 : 1)) & 1) == 0 ? 1 : -1;
			double reach = halfWidth * (0.62D + random.nextDouble() * 0.30D);
			double alongDeflection = reach * (0.16D + random.nextDouble() * 0.48D)
					* (random.nextBoolean() ? 1.0D : -1.0D);
			splayStartAlong[i] = anchor;
			splayStartAcross[i] = startAcross;
			splayEndAlong[i] = clampToRange(anchor + alongDeflection,
					-halfLength + 2.0D, halfLength - 2.0D);
			splayEndAcross[i] = side * reach;
			splayHalfWidth[i] = 2.5D + random.nextDouble() * 1.80D;
		}

		int lensCount = 2 + random.nextInt(2);
		double[] lensCenterAlong = new double[lensCount];
		double[] lensCenterAcross = new double[lensCount];
		double[] lensHalfLength = new double[lensCount];
		double[] lensHalfWidth = new double[lensCount];
		for (int i = 0; i < lensCount; i++) {
			int base = (int) Math.floor((i + 0.5D) * splayCount / lensCount);
			int splay = Math.min(splayCount - 1, Math.max(0,
					base + (random.nextBoolean() ? 0 : -1)));
			double attachment = 0.72D + random.nextDouble() * 0.18D;
			lensCenterAlong[i] = splayStartAlong[splay]
					+ (splayEndAlong[splay] - splayStartAlong[splay]) * attachment;
			lensCenterAcross[i] = splayStartAcross[splay]
					+ (splayEndAcross[splay] - splayStartAcross[splay]) * attachment;
			lensHalfLength[i] = Math.max(8.0D,
					strikeLength * (0.080D + random.nextDouble() * 0.070D));
			lensHalfWidth[i] = Math.max(2.25D,
					downDipWidth * (0.060D + random.nextDouble() * 0.025D));
		}

		return new StructureLayout(faultAlong, faultHalfGap, lateralOffsets, verticalOffsets,
				maximumLateral, maximumVertical, splayStartAlong, splayStartAcross,
				splayEndAlong, splayEndAcross, splayHalfWidth, lensCenterAlong,
				lensCenterAcross, lensHalfLength, lensHalfWidth);
	}

	static int requiredVerticalRadius(double dipDegrees, int downDipWidth,
			StratiformSeamSettings settings, StructureLayout structure) {
		return (int) Math.ceil(Math.abs(Math.tan(Math.toRadians(dipDegrees)))
				* downDipWidth * 0.5D + settings.foldAmplitude()
				+ settings.maximumThickness() * 0.5D + structure.maximumVerticalOffset + 3.0D);
	}

	private static double nextOffset(Random random, double previous, double minimumStep,
			double maximumStep, double maximumAbsolute) {
		for (int attempt = 0; attempt < 16; attempt++) {
			double step = minimumStep + random.nextDouble() * (maximumStep - minimumStep);
			double candidate = previous + (random.nextBoolean() ? step : -step);
			if (Math.abs(candidate) <= maximumAbsolute) return candidate;
		}
		return previous >= 0.0D ? previous - minimumStep : previous + minimumStep;
	}

	private static double clampToRange(double value, double minimum, double maximum) {
		return Math.max(minimum, Math.min(maximum, value));
	}

	int splayCount() { return splayStartAlong.length; }
	int lensCount() { return lensCenterAlong.length; }
	int faultCount() { return faultAlong.length; }
	double splayAnchor(int index) { return splayStartAlong[index]; }
	double splayLength(int index) {
		double dx = splayEndAlong[index] - splayStartAlong[index];
		double dz = splayEndAcross[index] - splayStartAcross[index];
		return Math.sqrt(dx * dx + dz * dz);
	}
	double lensCenterAlong(int index) { return lensCenterAlong[index]; }
	double lensCenterAcross(int index) { return lensCenterAcross[index]; }
	double lensHalfLength(int index) { return lensHalfLength[index]; }
	double lensHalfWidth(int index) { return lensHalfWidth[index]; }
	double lensRecoveryDistance(int index) {
		double nearestSquared = Double.POSITIVE_INFINITY;
		for (int splay = 0; splay < splayStartAlong.length; splay++) {
			nearestSquared = Math.min(nearestSquared, distanceSquaredToSegment(
					lensCenterAlong[index], lensCenterAcross[index],
					splayStartAlong[splay], splayStartAcross[splay],
					splayEndAlong[splay], splayEndAcross[splay]));
		}
		return Math.sqrt(nearestSquared);
	}
	double faultAlong(int index) { return faultAlong[index]; }
	double faultGap(int index) { return faultHalfGap[index] * 2.0D; }
	double faultLateralOffset(int index) {
		return segmentLateralOffset[index + 1] - segmentLateralOffset[index];
	}
	double faultVerticalOffset(int index) {
		return segmentVerticalOffset[index + 1] - segmentVerticalOffset[index];
	}
	double localAlongAt(int x, int z) { return alongAt(x, z); }
	int worldXAt(double along, double bentAcross) {
		double across = bentAcross + segmentLateralOffset[segmentAt(along)] + bendAmplitude
				* Math.sin((along / bendWavelength) * Math.PI * 2.0D + bendPhase);
		return (int) Math.round(centerX + along * strikeCosine - across * strikeSine - 0.5D);
	}
	int worldZAt(double along, double bentAcross) {
		double across = bentAcross + segmentLateralOffset[segmentAt(along)] + bendAmplitude
				* Math.sin((along / bendWavelength) * Math.PI * 2.0D + bendPhase);
		return (int) Math.round(centerZ + along * strikeSine + across * strikeCosine - 0.5D);
	}
	boolean isStructuralCoreAt(int x, int y, int z) {
		double grade = gradeAt(x, y, z);
		return grade > 0.0D && isConnectedCoreAt(x, y, z, grade);
	}
	boolean verticallyTruncated() { return verticallyTruncated; }

	static final class StructureLayout {
		private final double[] faultAlong;
		private final double[] faultHalfGap;
		private final double[] segmentLateralOffset;
		private final double[] segmentVerticalOffset;
		private final double maximumLateralOffset;
		private final double maximumVerticalOffset;
		private final double[] splayStartAlong;
		private final double[] splayStartAcross;
		private final double[] splayEndAlong;
		private final double[] splayEndAcross;
		private final double[] splayHalfWidth;
		private final double[] lensCenterAlong;
		private final double[] lensCenterAcross;
		private final double[] lensHalfLength;
		private final double[] lensHalfWidth;

		private StructureLayout(double[] faultAlong, double[] faultHalfGap,
				double[] segmentLateralOffset, double[] segmentVerticalOffset,
				double maximumLateralOffset, double maximumVerticalOffset,
				double[] splayStartAlong, double[] splayStartAcross,
				double[] splayEndAlong, double[] splayEndAcross, double[] splayHalfWidth,
				double[] lensCenterAlong, double[] lensCenterAcross,
				double[] lensHalfLength, double[] lensHalfWidth) {
			this.faultAlong = faultAlong;
			this.faultHalfGap = faultHalfGap;
			this.segmentLateralOffset = segmentLateralOffset;
			this.segmentVerticalOffset = segmentVerticalOffset;
			this.maximumLateralOffset = maximumLateralOffset;
			this.maximumVerticalOffset = maximumVerticalOffset;
			this.splayStartAlong = splayStartAlong;
			this.splayStartAcross = splayStartAcross;
			this.splayEndAlong = splayEndAlong;
			this.splayEndAcross = splayEndAcross;
			this.splayHalfWidth = splayHalfWidth;
			this.lensCenterAlong = lensCenterAlong;
			this.lensCenterAcross = lensCenterAcross;
			this.lensHalfLength = lensHalfLength;
			this.lensHalfWidth = lensHalfWidth;
		}
	}
}
