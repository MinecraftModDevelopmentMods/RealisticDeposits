package zone.moddev.mc.realisticdeposits.model;

import java.util.Locale;

/** Geological context used to qualify a deposit candidate. */
public enum AlignmentMode {
	MASSIVE_RARE,
	GEOME_ALIGNED,
	ROCK_ALIGNED;

	public static AlignmentMode parse(String value) {
		return valueOf(value.trim().toUpperCase(Locale.ROOT));
	}

	public String configName() {
		return name().toLowerCase(Locale.ROOT);
	}
}
