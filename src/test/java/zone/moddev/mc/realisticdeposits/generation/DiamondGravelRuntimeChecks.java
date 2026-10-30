package zone.moddev.mc.realisticdeposits.generation;

import java.util.List;

import com.google.gson.JsonObject;

import net.minecraft.block.Block;
import net.minecraft.entity.item.EntityFallingBlock;
import net.minecraft.entity.item.EntityItem;
import net.minecraft.init.Blocks;
import net.minecraft.init.Enchantments;
import net.minecraft.init.Items;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.WorldServer;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.common.util.FakePlayerFactory;

/** Native harvest and gravity paths, used only in the disposable server fixture. */
final class DiamondGravelRuntimeChecks {
	private static final BlockPos POS = new BlockPos(8, 200, 8);
	private static final AxisAlignedBB AREA = new AxisAlignedBB(0, 170, 0, 16, 240, 16);

	private DiamondGravelRuntimeChecks() { }

	static JsonObject run(WorldServer world, Block rich) {
		FakePlayer player = FakePlayerFactory.getMinecraft(world);
		ItemStack shovel = new ItemStack(Items.DIAMOND_SHOVEL);
		Item richItem = Item.getItemFromBlock(rich);
		clearItems(world);

		for (int i = 0; i < 128; i++) {
			world.setBlockState(POS, rich.getDefaultState(), 2);
			world.setBlockToAir(POS);
			rich.harvestBlock(world, player, POS, rich.getDefaultState(), null, shovel);
			List<EntityItem> drops = items(world);
			require(count(drops, Item.getItemFromBlock(Blocks.GRAVEL)) == 1, "Mining must return one spent gravel");
			require(count(drops, richItem) == 0, "Mining must consume the rich block");
			require(drops.stream().allMatch(item -> item.getEntityItem().getItem() == Items.DIAMOND
					|| item.getEntityItem().getItem() == Item.getItemFromBlock(Blocks.GRAVEL)), "Unexpected mining drop");
			clearItems(world);
		}

		shovel.addEnchantment(Enchantments.SILK_TOUCH, 1);
		rich.harvestBlock(world, player, POS, rich.getDefaultState(), null, shovel);
		require(count(items(world), richItem) == 1 && items(world).size() == 1, "Silk Touch must return only rich gravel");
		clearItems(world);

		for (int i = 0; i < 512; i++) {
			for (ItemStack drop : Blocks.GRAVEL.getDrops(world, POS, Blocks.GRAVEL.getDefaultState(), 3)) {
				require(drop.getItem() != Items.DIAMOND && drop.getItem() != richItem, "Ordinary gravel became rich");
			}
		}

		world.setBlockState(POS.down(), Blocks.STONE.getDefaultState(), 2);
		world.setBlockState(POS.up(8), rich.getDefaultState(), 2);
		EntityFallingBlock falling = new EntityFallingBlock(world, POS.getX() + 0.5,
				POS.getY() + 8.5, POS.getZ() + 0.5, rich.getDefaultState());
		for (int tick = 0; tick < 100 && !falling.isDead; tick++) falling.onUpdate();
		require(falling.isDead && world.getBlockState(POS).getBlock() == rich, "Falling gravel lost its identity");
		require(items(world).isEmpty(), "Landing extracted diamonds");
		world.setBlockToAir(POS);

		world.setBlockState(POS.up(8), rich.getDefaultState(), 2);
		falling = new EntityFallingBlock(world, POS.getX() + 0.5,
				POS.getY() + 8.5, POS.getZ() + 0.5, rich.getDefaultState());
		falling.onUpdate();
		NBTTagCompound saved = new NBTTagCompound();
		falling.writeToNBT(saved);
		EntityFallingBlock restored = new EntityFallingBlock(world);
		restored.readFromNBT(saved);
		restored.fallTime = 600;
		restored.onUpdate();
		require(restored.isDead && count(items(world), richItem) == 1 && items(world).size() == 1,
				"Indirect falling drop must preserve rich gravel without extracting diamonds");
		clearItems(world);

		for (int i = 0; i < 32; i++) {
			world.setBlockState(POS, rich.getDefaultState(), 2);
			world.newExplosion(null, POS.getX() + 0.5, POS.getY() + 0.5, POS.getZ() + 0.5, 2, false, true);
			require(world.getBlockState(POS).getBlock() != rich, "Explosion did not consume the test block");
			require(count(items(world), richItem) == 0, "Explosion returned an intact rich block");
			clearItems(world);
		}

		JsonObject report = new JsonObject();
		report.addProperty("normal_harvests", 128);
		report.addProperty("spent_gravel_checks", 512);
		report.addProperty("explosions", 32);
		report.addProperty("silk_touch", true);
		report.addProperty("falling_landing", true);
		report.addProperty("falling_save_reload_indirect_drop", true);
		return report;
	}

	private static List<EntityItem> items(WorldServer world) {
		return world.getEntitiesWithinAABB(EntityItem.class, AREA, item -> !item.isDead);
	}

	private static int count(List<EntityItem> items, Item item) {
		return items.stream().filter(entity -> entity.getEntityItem().getItem() == item)
				.mapToInt(entity -> entity.getEntityItem().stackSize).sum();
	}

	private static void clearItems(WorldServer world) {
		for (EntityItem item : items(world)) item.setDead();
	}

	private static void require(boolean passed, String message) {
		if (!passed) throw new IllegalStateException(message);
	}
}
