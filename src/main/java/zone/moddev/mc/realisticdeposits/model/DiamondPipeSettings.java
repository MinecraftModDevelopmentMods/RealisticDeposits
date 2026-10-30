package zone.moddev.mc.realisticdeposits.model;

import java.util.Objects;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

/** Dimensions and grade of a small district of steep diamond pipes. */
public final class DiamondPipeSettings implements DepositShapeSettings {
	private final IntRange pipes;
	private final IntRange diameter;
	private final IntRange rootY;
	private final IntRange cover;
	private final int radius;
	private final double density;
	private final double legacyScale;

	public DiamondPipeSettings(IntRange pipes, IntRange diameter, IntRange rootY,
			IntRange cover, int radius, double density, double legacyScale) {
		this.pipes = checked(pipes, 1, 8, "pipe count");
		this.diameter = checked(diameter, 8, 256, "upper diameter");
		this.rootY = checked(rootY, 1, 128, "root height");
		this.cover = checked(cover, 1, 64, "surface cover");

		if (radius < 32 || radius > 512 || !Double.isFinite(density) || density <= 0 || density > 1
				|| !Double.isFinite(legacyScale) || legacyScale <= 0 || legacyScale > 1) {
			throw new IllegalArgumentException("Invalid diamond district radius, density or legacy selection scale");
		}

		this.radius = radius;
		this.density = density;
		this.legacyScale = legacyScale;
	}

	@Override public String type() { return "diamond_pipe"; }
	public IntRange pipeCount() { return pipes; }
	public IntRange upperDiameter() { return diameter; }
	public IntRange rootY() { return rootY; }
	public IntRange surfaceCover() { return cover; }
	public int districtRadius() { return radius; }
	public double oreDensity() { return density; }
	public double legacySelectionScale() { return legacyScale; }

	@Override
	public JsonObject toJson() {
		JsonObject json = new JsonObject();
		json.addProperty("type", type());
		json.add("pipes", array(pipes));
		json.add("upper_diameter", array(diameter));
		json.add("root_y", array(rootY));
		json.add("surface_cover", array(cover));
		json.addProperty("district_radius", radius);
		json.addProperty("ore_density", density);
		json.addProperty("legacy_selection_scale", legacyScale);
		return json;
	}

	private static IntRange checked(IntRange value, int min, int max, String name) {
		Objects.requireNonNull(value, name);
		if (value.minimum() < min || value.maximum() > max) {
			throw new IllegalArgumentException("Invalid diamond " + name);
		}
		return value;
	}

	static JsonArray array(IntRange value) {
		JsonArray array = new JsonArray();
		array.add(new com.google.gson.JsonPrimitive(value.minimum()));
		array.add(new com.google.gson.JsonPrimitive(value.maximum()));
		return array;
	}
}
