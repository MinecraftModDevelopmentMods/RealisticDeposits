package zone.moddev.mc.realisticdeposits.content;

import net.minecraft.item.ItemBlock;
import net.minecraftforge.fml.common.registry.GameRegistry;

/** Sediment blocks remain registered even when their generation rules are disabled. */
public final class ModContent {
	public static final DiamondGravelBlock DIAMOND_GRAVEL = new DiamondGravelBlock();
	public static final GoldGravelBlock GOLD_GRAVEL = new GoldGravelBlock();

	private ModContent() { }

	public static void register() {
		GameRegistry.register(DIAMOND_GRAVEL);
		GameRegistry.register(new ItemBlock(DIAMOND_GRAVEL).setRegistryName(DIAMOND_GRAVEL.getRegistryName()));
		GameRegistry.register(GOLD_GRAVEL);
		GameRegistry.register(new ItemBlock(GOLD_GRAVEL).setRegistryName(GOLD_GRAVEL.getRegistryName()));
	}

}
