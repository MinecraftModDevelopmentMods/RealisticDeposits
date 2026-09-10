package zone.moddev.mc.realisticdeposits;

import net.minecraftforge.fml.common.Mod;

/**
 * Forge entry point for Realistic Deposits.
 *
 * <p>The initial framework deliberately registers no deposits. Generation is
 * added only after its deterministic world contract and OreSpawn integration
 * have focused regression coverage.</p>
 */
@Mod(modid = RealisticDeposits.MOD_ID, name = RealisticDeposits.NAME,
		version = RealisticDeposits.VERSION, acceptedMinecraftVersions = "[1.10.2]",
		dependencies = "required-after:orespawn@[4.0.16,5.0.0)")
public final class RealisticDeposits {
	public static final String MOD_ID = "realisticdeposits";
	public static final String NAME = "Realistic Deposits";
	public static final String VERSION = "0.1.0.110021";
}
