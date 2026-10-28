package zone.moddev.mc.realisticdeposits.model;

import com.google.gson.JsonObject;

/** Optional abandoned workings attached to a deposit, not another ore source. */
public final class AbandonedMineSettings {
	public static final double DEFAULT_CHANCE = 0.15D;
	public static final AbandonedMineSettings DISABLED = new AbandonedMineSettings(false, DEFAULT_CHANCE);

	private final boolean enabled;
	private final double chance;

	public AbandonedMineSettings(boolean enabled, double chance) {
		if (!Double.isFinite(chance) || chance < 0.0D || chance > 1.0D) {
			throw new IllegalArgumentException("Abandoned mine chance must be within [0,1]");
		}

		this.enabled = enabled;
		this.chance = chance;
	}

	public boolean enabled() {
		return enabled;
	}

	public double chance() {
		return chance;
	}

	public JsonObject toJson() {
		JsonObject json = new JsonObject();
		json.addProperty("enabled", enabled);
		json.addProperty("chance", chance);
		return json;
	}
}
