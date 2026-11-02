package zone.moddev.mc.realisticdeposits.model;

import java.util.Objects;

import com.google.gson.JsonObject;

/** A river pocket linked to a captured goldfield, not to unmined ore blocks. */
public final class GoldPlacerSettings implements DepositShapeSettings {
	private final String source;
	private final int distance;
	private final IntRange thickness;
	private final double coverage;

	public GoldPlacerSettings(String source, int distance, IntRange thickness, double coverage) {
		this.source = DepositDefinition.identifier(source, "gold placer source");
		this.thickness = Objects.requireNonNull(thickness, "thickness");

		if (distance < 16 || distance > 2048 || thickness.minimum() < 1 || thickness.maximum() > 3
				|| !Double.isFinite(coverage) || coverage <= 0 || coverage > 1) {
			throw new IllegalArgumentException("Invalid gold placer distance, thickness or coverage");
		}

		this.distance = distance;
		this.coverage = coverage;
	}

	@Override public String type() { return "gold_placer"; }
	public String sourceDeposit() { return source; }
	public int maximumSourceDistance() { return distance; }
	public IntRange thickness() { return thickness; }
	public double coverage() { return coverage; }

	@Override
	public JsonObject toJson() {
		JsonObject json = new JsonObject();
		json.addProperty("type", type());
		json.addProperty("source_deposit", source);
		json.addProperty("maximum_source_distance", distance);
		json.add("thickness", DiamondPipeSettings.array(thickness));
		json.addProperty("coverage", coverage);
		return json;
	}
}
