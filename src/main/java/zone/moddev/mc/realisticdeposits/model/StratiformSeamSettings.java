package zone.moddev.mc.realisticdeposits.model;

import java.util.Objects;

/** Bounded shape and grade settings for a folded, laterally extensive seam. */
public final class StratiformSeamSettings {
	private final IntRange strikeLength;
	private final IntRange downDipWidth;
	private final double minimumThickness;
	private final double maximumThickness;
	private final double minimumDipDegrees;
	private final double maximumDipDegrees;
	private final double bendAmplitude;
	private final double bendWavelength;
	private final double foldAmplitude;
	private final double foldWavelength;
	private final double density;
	private final double edgeFalloff;

	public StratiformSeamSettings(IntRange strikeLength, IntRange downDipWidth,
			double minimumThickness, double maximumThickness,
			double minimumDipDegrees, double maximumDipDegrees,
			double bendAmplitude, double bendWavelength,
			double foldAmplitude, double foldWavelength,
			double density, double edgeFalloff) {
		this.strikeLength = Objects.requireNonNull(strikeLength, "strikeLength");
		this.downDipWidth = Objects.requireNonNull(downDipWidth, "downDipWidth");
		if (strikeLength.minimum() < 32 || strikeLength.maximum() > 4096
				|| downDipWidth.minimum() < 8 || downDipWidth.maximum() > 1024) {
			throw new IllegalArgumentException("Seam dimensions are outside supported alpha bounds");
		}
		requireRange(minimumThickness, maximumThickness, 0.5D, 32.0D, "thickness");
		requireRange(minimumDipDegrees, maximumDipDegrees, -75.0D, 75.0D, "dip");
		requireFiniteRange(bendAmplitude, 0.0D, 128.0D, "bend amplitude");
		requireFiniteRange(bendWavelength, 8.0D, 2048.0D, "bend wavelength");
		requireFiniteRange(foldAmplitude, 0.0D, 128.0D, "fold amplitude");
		requireFiniteRange(foldWavelength, 8.0D, 2048.0D, "fold wavelength");
		requireFiniteRange(density, 0.001D, 1.0D, "density");
		requireFiniteRange(edgeFalloff, 0.0D, 1.0D, "edge falloff");
		this.minimumThickness = minimumThickness;
		this.maximumThickness = maximumThickness;
		this.minimumDipDegrees = minimumDipDegrees;
		this.maximumDipDegrees = maximumDipDegrees;
		this.bendAmplitude = bendAmplitude;
		this.bendWavelength = bendWavelength;
		this.foldAmplitude = foldAmplitude;
		this.foldWavelength = foldWavelength;
		this.density = density;
		this.edgeFalloff = edgeFalloff;
	}

	public IntRange strikeLength() { return strikeLength; }
	public IntRange downDipWidth() { return downDipWidth; }
	public double minimumThickness() { return minimumThickness; }
	public double maximumThickness() { return maximumThickness; }
	public double minimumDipDegrees() { return minimumDipDegrees; }
	public double maximumDipDegrees() { return maximumDipDegrees; }
	public double bendAmplitude() { return bendAmplitude; }
	public double bendWavelength() { return bendWavelength; }
	public double foldAmplitude() { return foldAmplitude; }
	public double foldWavelength() { return foldWavelength; }
	public double density() { return density; }
	public double edgeFalloff() { return edgeFalloff; }

	private static void requireRange(double min, double max, double floor, double ceiling,
			String name) {
		if (!Double.isFinite(min) || !Double.isFinite(max) || min < floor || max > ceiling || min > max) {
			throw new IllegalArgumentException("Invalid seam " + name + ": " + min + ".." + max);
		}
	}

	private static void requireFiniteRange(double value, double min, double max, String name) {
		if (!Double.isFinite(value) || value < min || value > max) {
			throw new IllegalArgumentException("Invalid seam " + name + ": " + value);
		}
	}
}
