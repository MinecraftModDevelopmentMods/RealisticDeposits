package zone.moddev.mc.realisticdeposits.model;

import java.util.Objects;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;

/** Bounded shape and grade settings for a folded, laterally extensive seam. */
public final class StratiformSeamSettings implements DepositShapeSettings {
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
	@Override public String type() { return "stratiform_seam"; }
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

	public JsonObject toJson() {
		JsonObject json = new JsonObject();
		json.addProperty("type", "stratiform_seam");
		json.add("strike_length", range(strikeLength.minimum(), strikeLength.maximum()));
		json.add("down_dip_width", range(downDipWidth.minimum(), downDipWidth.maximum()));
		json.add("thickness", range(minimumThickness, maximumThickness));
		json.add("dip_degrees", range(minimumDipDegrees, maximumDipDegrees));
		json.addProperty("bend_amplitude", bendAmplitude);
		json.addProperty("bend_wavelength", bendWavelength);
		json.addProperty("fold_amplitude", foldAmplitude);
		json.addProperty("fold_wavelength", foldWavelength);
		json.addProperty("density", density);
		json.addProperty("edge_falloff", edgeFalloff);
		return json;
	}

	private static JsonArray range(Number minimum, Number maximum) {
		JsonArray result = new JsonArray();
		result.add(new JsonPrimitive(minimum));
		result.add(new JsonPrimitive(maximum));
		return result;
	}

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
