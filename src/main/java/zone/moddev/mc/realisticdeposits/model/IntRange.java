package zone.moddev.mc.realisticdeposits.model;

import java.util.Random;

/** Inclusive integer range used by deterministic deposit settings. */
public final class IntRange {
	private final int minimum;
	private final int maximum;

	public IntRange(int minimum, int maximum) {
		if (minimum > maximum) {
			throw new IllegalArgumentException("Range minimum exceeds maximum: " + minimum + ".." + maximum);
		}
		this.minimum = minimum;
		this.maximum = maximum;
	}

	public int minimum() {
		return minimum;
	}

	public int maximum() {
		return maximum;
	}

	public int sample(Random random) {
		if (minimum == maximum) return minimum;
		long width = (long) maximum - minimum + 1L;
		if (width > Integer.MAX_VALUE) {
			throw new IllegalStateException("Range is too wide to sample: " + minimum + ".." + maximum);
		}
		return minimum + random.nextInt((int) width);
	}

	@Override
	public String toString() {
		return minimum + ".." + maximum;
	}
}
