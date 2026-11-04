package zone.moddev.mc.realisticdeposits.model;

import java.util.Objects;

import com.google.gson.JsonObject;

/** Dimensions of compact lapis lenses, rather than whole ore-filled rock layers. */
public final class LapisLensSettings implements DepositShapeSettings {
	private final IntRange lenses;
	private final IntRange strike;
	private final IntRange width;
	private final IntRange thickness;
	private final IntRange cover;
	private final double legacyScale;

	public LapisLensSettings(IntRange lenses, IntRange strike, IntRange width,
			IntRange thickness, IntRange cover, double legacyScale) {
		this.lenses = checked(lenses, 1, 6, "lens count");
		this.strike = checked(strike, 32, 256, "strike length");
		this.width = checked(width, 6, 48, "width");
		this.thickness = checked(thickness, 1, 6, "thickness");
		this.cover = checked(cover, 1, 64, "surface cover");

		if (!Double.isFinite(legacyScale) || legacyScale <= 0 || legacyScale > 1) {
			throw new IllegalArgumentException("Lapis legacy selection scale must be within (0,1]");
		}

		this.legacyScale = legacyScale;
	}

	@Override
	public String type() {
		return "lapis_lens";
	}

	public IntRange lensCount() { return lenses; }
	public IntRange strikeLength() { return strike; }
	public IntRange width() { return width; }
	public IntRange thickness() { return thickness; }
	public IntRange surfaceCover() { return cover; }
	public double legacySelectionScale() { return legacyScale; }

	@Override
	public JsonObject toJson() {
		JsonObject json = new JsonObject();
		json.addProperty("type", type());
		json.add("lenses", DiamondPipeSettings.array(lenses));
		json.add("strike_length", DiamondPipeSettings.array(strike));
		json.add("width", DiamondPipeSettings.array(width));
		json.add("thickness", DiamondPipeSettings.array(thickness));
		json.add("surface_cover", DiamondPipeSettings.array(cover));
		json.addProperty("legacy_selection_scale", legacyScale);

		return json;
	}

	private static IntRange checked(IntRange value, int minimum, int maximum, String name) {
		Objects.requireNonNull(value, name);

		if (value.minimum() < minimum || value.maximum() > maximum) {
			throw new IllegalArgumentException("Invalid lapis " + name);
		}

		return value;
	}
}
