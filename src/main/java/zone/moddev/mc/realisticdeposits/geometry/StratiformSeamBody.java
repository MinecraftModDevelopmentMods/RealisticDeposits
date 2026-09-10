package zone.moddev.mc.realisticdeposits.geometry;

import zone.moddev.mc.realisticdeposits.model.DepositDefinition;
import zone.moddev.mc.realisticdeposits.model.StratiformSeamSettings;

/**
 * Deterministic folded/tabular orebody. It models strike, dip, lateral bends,
 * thickness variation, edge taper and discontinuous grade without Minecraft
 * world access.
 */
public final class StratiformSeamBody {
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
	private final double density;
	private final double edgeFalloff;
	private final int minimumY;
	private final int maximumY;
	private final BlockBounds bounds;

	StratiformSeamBody(DepositIdentity identity, int centerX, int centerY, int centerZ,
			int strikeLength, int downDipWidth, double strikeRadians, double dipDegrees,
			StratiformSeamSettings settings, double bendPhase, double foldPhase,
			double thicknessPhase, int minimumY, int maximumY) {
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
		this.density = settings.density();
		this.edgeFalloff = settings.edgeFalloff();
		this.minimumY = minimumY;
		this.maximumY = maximumY;
		double horizontalRadius = strikeLength * 0.5D + downDipWidth * 0.5D
				+ bendAmplitude + 3.0D;
		double verticalRadius = Math.abs(dipTangent) * downDipWidth * 0.5D
				+ foldAmplitude + maximumThickness * 0.5D + 3.0D;
		bounds = new BlockBounds(
				(int) Math.floor(centerX - horizontalRadius),
				Math.max(minimumY, (int) Math.floor(centerY - verticalRadius)),
				(int) Math.floor(centerZ - horizontalRadius),
				(int) Math.ceil(centerX + horizontalRadius),
				Math.min(maximumY, (int) Math.ceil(centerY + verticalRadius)),
				(int) Math.ceil(centerZ + horizontalRadius));
	}

	public DepositIdentity identity() { return identity; }
	public int centerX() { return centerX; }
	public int centerY() { return centerY; }
	public int centerZ() { return centerZ; }
	public int strikeLength() { return strikeLength; }
	public int downDipWidth() { return downDipWidth; }
	public BlockBounds bounds() { return bounds; }

	/** Returns a normalized geological grade, or zero outside the seam. */
	public double gradeAt(int x, int y, int z) {
		if (y < minimumY || y > maximumY) return 0.0D;
		double dx = x + 0.5D - centerX;
		double dz = z + 0.5D - centerZ;
		double along = dx * strikeCosine + dz * strikeSine;
		double across = -dx * strikeSine + dz * strikeCosine;
		double halfLength = strikeLength * 0.5D;
		double halfWidth = downDipWidth * 0.5D;
		if (Math.abs(along) > halfLength || Math.abs(across) > halfWidth + bendAmplitude) {
			return 0.0D;
		}
		double bentAcross = across - bendAmplitude
				* Math.sin((along / bendWavelength) * Math.PI * 2.0D + bendPhase);
		if (Math.abs(bentAcross) > halfWidth) return 0.0D;
		double planeY = centerY + dipTangent * bentAcross + foldAmplitude
				* Math.sin((along / foldWavelength) * Math.PI * 2.0D + foldPhase);
		double thicknessWave = 0.5D + 0.5D
				* Math.sin((along / (foldWavelength * 0.73D)) * Math.PI * 2.0D + thicknessPhase);
		double thickness = minimumThickness
				+ (maximumThickness - minimumThickness) * thicknessWave;
		double vertical = Math.abs(y + 0.5D - planeY) / Math.max(0.25D, thickness * 0.5D);
		if (vertical > 1.0D) return 0.0D;
		double alongEdge = 1.0D - Math.abs(along) / halfLength;
		double acrossEdge = 1.0D - Math.abs(bentAcross) / halfWidth;
		double edge = Math.min(alongEdge, acrossEdge);
		double edgeWeight = edgeFalloff == 0.0D ? 1.0D
				: clamp(edge / Math.max(0.0001D, edgeFalloff));
		return clamp((1.0D - vertical * vertical) * edgeWeight);
	}

	/** Deterministic grade sampling; true positions are the ore fraction of the body. */
	public boolean isOreAt(int x, int y, int z) {
		double grade = gradeAt(x, y, z);
		if (grade <= 0.0D) return false;
		long coordinate = StableHash.combine(identity.seed(), x);
		coordinate = StableHash.combine(coordinate, y);
		coordinate = StableHash.combine(coordinate, z);
		return StableHash.unit(coordinate) < density * (0.35D + 0.65D * grade);
	}

	private static double clamp(double value) {
		return Math.max(0.0D, Math.min(1.0D, value));
	}
}
