package zone.moddev.mc.realisticdeposits.model;

import java.util.Objects;

import com.google.gson.JsonObject;

/** Shape and concentration of a district of steep gold-bearing fractures. */
public final class GoldLodeSettings implements DepositShapeSettings {
	private final IntRange veins;
	private final IntRange strike;
	private final IntRange thickness;
	private final IntRange cover;
	private final double minimumDip;
	private final double maximumDip;
	private final double legacyScale;

	public GoldLodeSettings(IntRange veins, IntRange strike, IntRange thickness,
			IntRange cover, double minimumDip, double maximumDip, double legacyScale) {
		this.veins = checked(veins, 1, 8, "vein count");
		this.strike = checked(strike, 64, 1024, "strike length");
		this.thickness = checked(thickness, 1, 8, "vein width");
		this.cover = checked(cover, 1, 64, "surface cover");

		if (!Double.isFinite(minimumDip) || !Double.isFinite(maximumDip)
				|| minimumDip < 45 || maximumDip > 89 || minimumDip > maximumDip
				|| !Double.isFinite(legacyScale) || legacyScale <= 0 || legacyScale > 1) {
			throw new IllegalArgumentException("Invalid gold dip or legacy selection scale");
		}

		this.minimumDip = minimumDip;
		this.maximumDip = maximumDip;
		this.legacyScale = legacyScale;
	}

	@Override public String type() { return "gold_lode"; }
	public IntRange veinCount() { return veins; }
	public IntRange strikeLength() { return strike; }
	public IntRange thickness() { return thickness; }
	public IntRange surfaceCover() { return cover; }
	public double minimumDip() { return minimumDip; }
	public double maximumDip() { return maximumDip; }
	public double legacySelectionScale() { return legacyScale; }

	@Override
	public JsonObject toJson() {
		JsonObject json = new JsonObject();
		json.addProperty("type", type());
		json.add("veins", DiamondPipeSettings.array(veins));
		json.add("strike_length", DiamondPipeSettings.array(strike));
		json.add("thickness", DiamondPipeSettings.array(thickness));
		json.add("surface_cover", DiamondPipeSettings.array(cover));
		com.google.gson.JsonArray dip = new com.google.gson.JsonArray();
		dip.add(new com.google.gson.JsonPrimitive(minimumDip));
		dip.add(new com.google.gson.JsonPrimitive(maximumDip));
		json.add("dip_degrees", dip);
		json.addProperty("legacy_selection_scale", legacyScale);
		return json;
	}

	private static IntRange checked(IntRange value, int min, int max, String name) {
		Objects.requireNonNull(value, name);
		if (value.minimum() < min || value.maximum() > max) {
			throw new IllegalArgumentException("Invalid gold " + name);
		}
		return value;
	}
}
