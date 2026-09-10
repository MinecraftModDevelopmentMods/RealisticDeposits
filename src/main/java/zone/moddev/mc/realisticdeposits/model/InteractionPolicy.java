package zone.moddev.mc.realisticdeposits.model;

import java.util.Locale;

/** Relationship between a large deposit and OreSpawn's ordinary ore rules. */
public enum InteractionPolicy {
	ADDITIVE,
	HYBRID,
	REPLACEMENT;

	public static InteractionPolicy parse(String value) {
		return valueOf(value.trim().toUpperCase(Locale.ROOT));
	}

	public String configName() {
		return name().toLowerCase(Locale.ROOT);
	}
}
