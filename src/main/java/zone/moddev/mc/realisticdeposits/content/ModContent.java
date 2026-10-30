package zone.moddev.mc.realisticdeposits.content;

import net.minecraft.item.ItemBlock;
import net.minecraftforge.fml.common.registry.GameRegistry;

/** Registry identity stays the same even when every diamond rule is disabled. */
public final class ModContent {
	public static final DiamondGravelBlock DIAMOND_GRAVEL = new DiamondGravelBlock();

	private ModContent() { }

	public static void register() {
		GameRegistry.register(DIAMOND_GRAVEL);
		GameRegistry.register(new ItemBlock(DIAMOND_GRAVEL).setRegistryName(DIAMOND_GRAVEL.getRegistryName()));
	}

}
