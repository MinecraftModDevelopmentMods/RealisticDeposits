package zone.moddev.mc.realisticdeposits.model;

import java.util.Objects;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;

/** A small stack of coal beds with shared bedding and faults. */
public final class CoalMeasuresSettings implements DepositShapeSettings {
	private final IntRange length;
	private final IntRange width;
	private final IntRange beds;
	private final IntRange thickness;
	private final IntRange separation;
	private final double dip;
	private final double fold;
	private final int faultThrow;

	public CoalMeasuresSettings(IntRange length, IntRange width, IntRange beds,
			IntRange thickness, IntRange separation, double dip, double fold, int faultThrow) {
		this.length = range(length, 32, 4096, "strike length");
		this.width = range(width, 16, 1024, "down-dip width");
		this.beds = range(beds, 1, 8, "bed count");
		this.thickness = range(thickness, 1, 8, "bed thickness");
		this.separation = range(separation, 2, 32, "rock interval");

		if (!Double.isFinite(dip) || dip < 0 || dip > 15
				|| !Double.isFinite(fold) || fold < 0 || fold > 12
				|| faultThrow < 0 || faultThrow > 12) {
			throw new IllegalArgumentException("Coal bedding or fault values are outside their supported range");
		}

		this.dip = dip;
		this.fold = fold;
		this.faultThrow = faultThrow;
	}

	@Override public String type() { return "coal_measures"; }
	public IntRange strikeLength() { return length; }
	public IntRange downDipWidth() { return width; }
	public IntRange beds() { return beds; }
	public IntRange thickness() { return thickness; }
	public IntRange separation() { return separation; }
	public double maximumDip() { return dip; }
	public double foldAmplitude() { return fold; }
	public int maximumFaultThrow() { return faultThrow; }

	@Override
	public JsonObject toJson() {
		JsonObject json = new JsonObject();
		json.addProperty("type", type());
		json.add("strike_length", array(length));
		json.add("down_dip_width", array(width));
		json.add("beds", array(beds));
		json.add("thickness", array(thickness));
		json.add("rock_interval", array(separation));
		json.addProperty("maximum_dip_degrees", dip);
		json.addProperty("fold_amplitude", fold);
		json.addProperty("maximum_fault_throw", faultThrow);
		return json;
	}

	private static IntRange range(IntRange value, int min, int max, String name) {
		Objects.requireNonNull(value, name);
		if (value.minimum() < min || value.maximum() > max) {
			throw new IllegalArgumentException("Invalid coal " + name);
		}
		return value;
	}

	private static JsonArray array(IntRange value) {
		JsonArray array = new JsonArray();
		array.add(new JsonPrimitive(value.minimum()));
		array.add(new JsonPrimitive(value.maximum()));
		return array;
	}
}
