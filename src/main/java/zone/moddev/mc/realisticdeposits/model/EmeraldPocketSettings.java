package zone.moddev.mc.realisticdeposits.model;

import com.google.gson.JsonObject;

/** Bounded geometry for a group of separated emerald pockets. */
public final class EmeraldPocketSettings implements DepositShapeSettings {
	private final IntRange pockets;
	private final IntRange strike;
	private final IntRange oreBlocks;
	private final IntRange cover;
	private final RichFind rich;
	private final LargeMine large;
	private final boolean veinTrace;

	public EmeraldPocketSettings(IntRange pockets, IntRange strike, IntRange oreBlocks, IntRange cover) {
		this(pockets, strike, oreBlocks, cover, null, false);
	}

	public EmeraldPocketSettings(IntRange pockets, IntRange strike, IntRange oreBlocks, IntRange cover,
			RichFind rich, boolean veinTrace) {
		this(pockets, strike, oreBlocks, cover, rich, veinTrace, null);
	}

	public EmeraldPocketSettings(IntRange pockets, IntRange strike, IntRange oreBlocks, IntRange cover,
			RichFind rich, boolean veinTrace, LargeMine large) {
		this.pockets = checked(pockets, 2, 6, "pocket count");
		this.strike = checked(strike, 8, 96, "strike length");
		this.oreBlocks = checked(oreBlocks, 2, 120, "ore budget");
		this.cover = checked(cover, 1, 64, "surface cover");
		this.rich = rich;
		this.large = large;
		this.veinTrace = veinTrace;
		validateSpacing(pockets, strike, oreBlocks);
		if (rich != null && (rich.oreBlocks().minimum() <= oreBlocks.maximum()
				|| rich.strikeLength().minimum() < strike.maximum() || rich.pocketCount().minimum() < pockets.maximum())) {
			throw new IllegalArgumentException("Rich emerald finds must increase the budget without shrinking the trend");
		}
	}

	@Override public String type() { return "emerald_pockets"; }
	public IntRange pocketCount() { return pockets; }
	public IntRange strikeLength() { return strike; }
	public IntRange oreBlocks() { return oreBlocks; }
	public IntRange surfaceCover() { return cover; }
	public RichFind rich() { return rich; }
	public LargeMine large() { return large; }
	public boolean veinTrace() { return veinTrace; }
	public int maximumStrike() { return rich == null ? strike.maximum() : rich.strikeLength().maximum(); }

	@Override
	public JsonObject toJson() {
		JsonObject json = new JsonObject();
		json.addProperty("type", type());
		json.add("pockets", DiamondPipeSettings.array(pockets));
		json.add("strike_length", DiamondPipeSettings.array(strike));
		json.add("ore_blocks", DiamondPipeSettings.array(oreBlocks));
		json.add("surface_cover", DiamondPipeSettings.array(cover));
		if (rich != null) json.add("rich_find", rich.toJson());
		if (large != null) json.add("large_mine", large.toJson());
		if (veinTrace) json.addProperty("vein_trace", true);
		return json;
	}

	private static void validateSpacing(IntRange pockets, IntRange strike, IntRange oreBlocks) {
		if (strike.minimum() < 8 * (pockets.maximum() - 1) || oreBlocks.minimum() < pockets.maximum()) {
			throw new IllegalArgumentException("Emerald pockets need room to separate and ore in every pocket");
		}
	}

	public static final class RichFind {
		private final double chance;
		private final IntRange pockets;
		private final IntRange strike;
		private final IntRange oreBlocks;
		private final boolean workings;
		private final int fitAttempts;

		public RichFind(double chance, IntRange pockets, IntRange strike, IntRange oreBlocks) {
			this(chance, pockets, strike, oreBlocks, false);
		}

		public RichFind(double chance, IntRange pockets, IntRange strike, IntRange oreBlocks, boolean workings) {
			this(chance, pockets, strike, oreBlocks, workings, 1);
		}

		public RichFind(double chance, IntRange pockets, IntRange strike, IntRange oreBlocks, boolean workings, int fitAttempts) {
			if (!Double.isFinite(chance) || chance <= 0 || chance > 1) {
				throw new IllegalArgumentException("Invalid rich emerald find chance");
			}
			this.chance = chance;
			this.pockets = checked(pockets, 2, 6, "rich pocket count");
			this.strike = checked(strike, 8, 96, "rich strike length");
			this.oreBlocks = checked(oreBlocks, 2, 120, "rich ore budget");
			this.workings = workings;
			this.fitAttempts = checkedFitAttempts(fitAttempts);
			validateSpacing(pockets, strike, oreBlocks);
		}

		public double chance() { return chance; }
		public IntRange pocketCount() { return pockets; }
		public IntRange strikeLength() { return strike; }
		public IntRange oreBlocks() { return oreBlocks; }
		public boolean workings() { return workings; }
		public int fitAttempts() { return fitAttempts; }

		private JsonObject toJson() {
			JsonObject json = new JsonObject();
			json.addProperty("chance", chance);
			json.add("pockets", DiamondPipeSettings.array(pockets));
			json.add("strike_length", DiamondPipeSettings.array(strike));
			json.add("ore_blocks", DiamondPipeSettings.array(oreBlocks));
			if (workings) json.addProperty("workings", true);
			if (fitAttempts > 1) json.addProperty("fit_attempts", fitAttempts);
			return json;
		}
	}

	public static final class LargeMine {
		private final double chance;
		private final IntRange zones;
		private final IntRange strike;
		private final IntRange oreBlocks;
		private final int fitAttempts;

		public LargeMine(double chance, IntRange zones, IntRange strike, IntRange oreBlocks) {
			this(chance, zones, strike, oreBlocks, 1);
		}

		public LargeMine(double chance, IntRange zones, IntRange strike, IntRange oreBlocks, int fitAttempts) {
			if (!Double.isFinite(chance) || chance <= 0 || chance > 1) {
				throw new IllegalArgumentException("Invalid large emerald mine chance");
			}
			this.chance = chance;
			this.zones = checked(zones, 3, 6, "large zone count");
			this.strike = checked(strike, 96, 192, "large strike length");
			this.oreBlocks = checked(oreBlocks, 200, 500, "large ore budget");
			this.fitAttempts = checkedFitAttempts(fitAttempts);
			if (strike.minimum() < 24 * (zones.maximum() - 1)
					|| oreBlocks.maximum() > 108 * zones.minimum()) {
				throw new IllegalArgumentException("Large emerald zones need separation and at most 54 ore per pocket");
			}
		}

		public double chance() { return chance; }
		public IntRange zones() { return zones; }
		public IntRange strikeLength() { return strike; }
		public IntRange oreBlocks() { return oreBlocks; }
		public int fitAttempts() { return fitAttempts; }

		private JsonObject toJson() {
			JsonObject json = new JsonObject();
			json.addProperty("chance", chance);
			json.add("zones", DiamondPipeSettings.array(zones));
			json.add("strike_length", DiamondPipeSettings.array(strike));
			json.add("ore_blocks", DiamondPipeSettings.array(oreBlocks));
			if (fitAttempts > 1) json.addProperty("fit_attempts", fitAttempts);
			return json;
		}
	}

	private static int checkedFitAttempts(int value) {
		if (value < 1 || value > 8) throw new IllegalArgumentException("Emerald fitting requires 1 to 8 attempts");
		return value;
	}

	private static IntRange checked(IntRange value, int minimum, int maximum, String name) {
		if (value.minimum() < minimum || value.maximum() > maximum) {
			throw new IllegalArgumentException("Invalid emerald " + name);
		}
		return value;
	}
}
