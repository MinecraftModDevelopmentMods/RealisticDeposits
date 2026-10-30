package zone.moddev.mc.realisticdeposits.model;

import com.google.gson.JsonObject;

/** River pockets supplied by one captured diamond-pipe definition. */
public final class DiamondPlacerSettings implements DepositShapeSettings {
	private final String source;
	private final int distance;
	private final IntRange thickness;
	private final double coverage;

	public DiamondPlacerSettings(String source, int distance, IntRange thickness, double coverage) {
		this.source = DepositDefinition.identifier(source, "placer source");
		if (distance < 32 || distance > 2048 || thickness == null
				|| thickness.minimum() < 1 || thickness.maximum() > 3
				|| !Double.isFinite(coverage) || coverage <= 0 || coverage > 1) {
			throw new IllegalArgumentException("Invalid diamond placer distance, thickness or coverage");
		}

		this.distance = distance;
		this.thickness = thickness;
		this.coverage = coverage;
	}

	@Override public String type() { return "diamond_placer"; }
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
