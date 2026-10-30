package zone.moddev.mc.realisticdeposits.proxy;

import zone.moddev.mc.orespawn.api.client.WorldSettingsExtensionRegistry;
import zone.moddev.mc.realisticdeposits.client.RealisticDepositsConfigScreen;

import net.minecraft.util.ResourceLocation;
import net.minecraft.block.Block;
import net.minecraft.item.Item;
import net.minecraft.client.renderer.block.model.ModelResourceLocation;
import net.minecraftforge.client.model.ModelLoader;
import zone.moddev.mc.realisticdeposits.content.ModContent;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

/** Registers the optional action in OreSpawn's world-settings screen. */
@SideOnly(Side.CLIENT)
public final class ClientProxy extends CommonProxy {
	@Override
	public void registerClientModels() {
		Block block = ModContent.DIAMOND_GRAVEL;
		ModelLoader.setCustomModelResourceLocation(Item.getItemFromBlock(block), 0,
				new ModelResourceLocation(block.getRegistryName(), "inventory"));
	}

	@Override
	public void initializeClient() {
		WorldSettingsExtensionRegistry.register(
				new ResourceLocation("realisticdeposits", "settings"),
				"button.realisticdeposits.settings",
				RealisticDepositsConfigScreen::new);
	}
}
