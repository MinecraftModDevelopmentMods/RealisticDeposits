package zone.moddev.mc.realisticdeposits.content;

import java.util.List;
import java.util.Random;

import net.minecraft.block.BlockGravel;
import net.minecraft.block.state.IBlockState;
import net.minecraft.creativetab.CreativeTabs;
import net.minecraft.init.Blocks;
import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;
import net.minecraft.util.BlockRenderLayer;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.IBlockAccess;

/** Rich sediment is consumed once; the ordinary gravel left behind is spent. */
public final class DiamondGravelBlock extends BlockGravel {
	public DiamondGravelBlock() {
		setRegistryName("realisticdeposits", "diamond_gravel");
		setUnlocalizedName("realisticdeposits.diamond_gravel");
		setCreativeTab(CreativeTabs.BUILDING_BLOCKS);
	}

	@Override
	public List<ItemStack> getDrops(IBlockAccess world, BlockPos pos, IBlockState state, int fortune) {
		List<ItemStack> drops = new java.util.ArrayList<>();
		drops.add(new ItemStack(Blocks.GRAVEL));
		int diamonds = diamondYield(RANDOM, fortune);
		if (diamonds > 0) drops.add(new ItemStack(Items.DIAMOND, diamonds));
		return drops;
	}

	public static int diamondYield(Random random, int fortune) {
		if (random.nextInt(16) != 0) return 0;
		int bonus = fortune > 0 ? Math.max(0, random.nextInt(Math.min(fortune, 255) + 2) - 1) : 0;
		return bonus + 1;
	}

	@Override
	public BlockRenderLayer getBlockLayer() {
		return BlockRenderLayer.CUTOUT_MIPPED;
	}
}
