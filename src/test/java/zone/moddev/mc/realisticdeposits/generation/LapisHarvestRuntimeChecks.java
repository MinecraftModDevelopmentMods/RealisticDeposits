package zone.moddev.mc.realisticdeposits.generation;

import com.google.gson.JsonObject;

import net.minecraft.entity.item.EntityItem;
import net.minecraft.init.Blocks;
import net.minecraft.init.Enchantments;
import net.minecraft.init.Items;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.WorldServer;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.common.util.FakePlayerFactory;

/** Uses vanilla's real harvest path, including its legacy dye metadata. */
final class LapisHarvestRuntimeChecks {
	private LapisHarvestRuntimeChecks() { }

	static JsonObject run(WorldServer world) {
		BlockPos position = new BlockPos(8, 200, 8);
		AxisAlignedBB area = new AxisAlignedBB(0, 190, 0, 16, 220, 16);
		FakePlayer player = FakePlayerFactory.getMinecraft(world);
		int harvests = 0;

		for (int fortune = 0; fortune <= 3; fortune++) {
			ItemStack tool = new ItemStack(Items.DIAMOND_PICKAXE);
			if (fortune > 0) tool.addEnchantment(Enchantments.FORTUNE, fortune);

			for (int attempt = 0; attempt < 64; attempt++) {
				world.setBlockState(position, Blocks.LAPIS_ORE.getDefaultState(), 2);
				world.setBlockToAir(position);
				Blocks.LAPIS_ORE.harvestBlock(world, player, position, Blocks.LAPIS_ORE.getDefaultState(), null, tool);
				int count = 0;

				for (EntityItem entity : world.getEntitiesWithinAABB(EntityItem.class, area, item -> !item.isDead)) {
					ItemStack drop = entity.getEntityItem();
					if (drop.getItem() != Items.DYE || drop.getMetadata() != 4) {
						throw new AssertionError("Lapis mining returned the wrong legacy item");
					}
					count += drop.stackSize;
					entity.setDead();
				}

				if (count < 4 || count > 8 * (fortune + 1)) {
					throw new AssertionError("Lapis mining returned an invalid Fortune yield: " + count);
				}
				harvests++;
			}
		}

		ItemStack silk = new ItemStack(Items.DIAMOND_PICKAXE);
		silk.addEnchantment(Enchantments.SILK_TOUCH, 1);
		Blocks.LAPIS_ORE.harvestBlock(world, player, position, Blocks.LAPIS_ORE.getDefaultState(), null, silk);
		int silkCount = 0;

		for (EntityItem entity : world.getEntitiesWithinAABB(EntityItem.class, area, item -> !item.isDead)) {
			if (entity.getEntityItem().getItem() != Item.getItemFromBlock(Blocks.LAPIS_ORE)) {
				throw new AssertionError("Silk Touch extracted lapis instead of its ore block");
			}
			silkCount += entity.getEntityItem().stackSize;
			entity.setDead();
		}

		if (silkCount != 1) throw new AssertionError("Silk Touch must return one lapis ore block");
		JsonObject report = new JsonObject();
		report.addProperty("native_harvests", harvests);
		report.addProperty("silk_touch", true);
		report.addProperty("legacy_dye_metadata", 4);

		return report;
	}
}
