package zone.moddev.mc.realisticdeposits.geometry;

/** Stable hashing primitives; outputs are part of the alpha world contract. */
final class StableHash {
	private StableHash() { }

	static long string(String value) {
		long hash = 0xcbf29ce484222325L;
		for (int i = 0; i < value.length(); i++) {
			hash ^= value.charAt(i);
			hash *= 0x100000001b3L;
		}
		return mix(hash);
	}

	static long combine(long seed, long value) {
		return mix(seed ^ (value + 0x9E3779B97F4A7C15L + (seed << 6) + (seed >>> 2)));
	}

	static long mix(long value) {
		value ^= value >>> 30;
		value *= 0xBF58476D1CE4E5B9L;
		value ^= value >>> 27;
		value *= 0x94D049BB133111EBL;
		return value ^ (value >>> 31);
	}

	static double unit(long value) {
		return (mix(value) >>> 11) * 0x1.0p-53;
	}
}
