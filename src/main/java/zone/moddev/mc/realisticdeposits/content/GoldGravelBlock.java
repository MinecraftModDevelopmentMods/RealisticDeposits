package zone.moddev.mc.realisticdeposits.content;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import net.minecraft.block.BlockGravel;
import net.minecraft.block.state.IBlockState;
import net.minecraft.creativetab.CreativeTabs;
import net.minecraft.init.Blocks;
import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.IBlockAccess;

/** Mining consumes the rich sediment; the ordinary gravel left behind is spent. */
public final class GoldGravelBlock extends BlockGravel {
	public GoldGravelBlock() {
		setRegistryName("realisticdeposits", "gold_gravel");
		setUnlocalizedName("realisticdeposits.gold_gravel");
		setCreativeTab(CreativeTabs.BUILDING_BLOCKS);
	}

	@Override
	public List<ItemStack> getDrops(IBlockAccess world, BlockPos pos, IBlockState state, int fortune) {
		List<ItemStack> drops = new ArrayList<>();
		drops.add(new ItemStack(Blocks.GRAVEL));
		int nuggets = nuggetYield(RANDOM, fortune);
		if (nuggets > 0) drops.add(new ItemStack(Items.GOLD_NUGGET, nuggets));
		return drops;
	}

	public static int nuggetYield(Random random, int fortune) {
		if (random.nextInt(4) != 0) return 0;
		int bonus = fortune > 0 ? Math.max(0, random.nextInt(Math.min(fortune, 255) + 2) - 1) : 0;
		return 1 + bonus;
	}
}
