package zone.moddev.mc.realisticdeposits.proxy;

import zone.moddev.mc.orespawn.api.client.WorldSettingsExtensionRegistry;
import zone.moddev.mc.realisticdeposits.client.RealisticDepositsConfigScreen;

import net.minecraft.util.ResourceLocation;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

/** Registers the optional action in OreSpawn's world-settings screen. */
@SideOnly(Side.CLIENT)
public final class ClientProxy extends CommonProxy {
	@Override
	public void initializeClient() {
		WorldSettingsExtensionRegistry.register(
				new ResourceLocation("realisticdeposits", "settings"),
				"button.realisticdeposits.settings",
				RealisticDepositsConfigScreen::new);
	}
}
