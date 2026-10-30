package zone.moddev.mc.realisticdeposits;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;

import zone.moddev.mc.realisticdeposits.config.DepositCatalogLoader;
import zone.moddev.mc.realisticdeposits.model.DepositDefinition;

/** Shared defaults for diamond tests, not a second set of generation settings. */
public final class DiamondFixtures {
	private DiamondFixtures() { }

	public static List<DepositDefinition> definitions() {
		try (InputStreamReader source = new InputStreamReader(DiamondFixtures.class.getResourceAsStream(
				"/assets/realisticdeposits/defaults/diamonds.json"), StandardCharsets.UTF_8)) {
			return DepositCatalogLoader.load(source, "diamond fixture").definitions();
		} catch (java.io.IOException e) {
			throw new IllegalStateException("Could not read the diamond fixture", e);
		}
	}

	public static DepositDefinition pipe() { return definitions().get(0); }
	public static DepositDefinition placer() { return definitions().get(1); }
}
