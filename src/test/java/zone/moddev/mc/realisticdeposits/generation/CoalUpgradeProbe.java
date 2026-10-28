package zone.moddev.mc.realisticdeposits.generation;

import java.io.File;
import java.io.FileWriter;
import java.security.MessageDigest;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;

import net.minecraft.block.Block;
import net.minecraft.init.Blocks;
import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.server.MinecraftServer;
import net.minecraft.tileentity.TileEntityChest;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.WorldServer;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.gen.ChunkProviderServer;
import net.minecraftforge.fml.common.FMLCommonHandler;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.event.FMLServerStartedEvent;

/** Uses only old public APIs so the same fixture runs before and after the update. */
@Mod(modid = "rdcoalupgradeprobe", name = "Coal Upgrade Fixture", version = "1",
		dependencies = "required-after:realisticdeposits")
public final class CoalUpgradeProbe {
	private static final BlockPos CHEST = new BlockPos(1032, 132, 1032);
	private static final BlockPos RAW_MARKER = new BlockPos(2568, 48, 2568);

	@Mod.EventHandler
	public void started(FMLServerStartedEvent event) throws Exception {
		if (!Boolean.getBoolean("rd.coal.upgrade")) return;
		MinecraftServer server = FMLCommonHandler.instance().getMinecraftServerInstance();
		try {
			WorldServer world = server.worldServerForDimension(0);
			if (!"coal-upgrade-world".equals(world.getSaveHandler().getWorldDirectory().getName())) {
				throw new IllegalStateException("Wrong disposable upgrade world");
			}
			world.getGameRules().setOrCreateGameRule("randomTickSpeed", "0");
			String phase = System.getProperty("rd.coal.phase");
			if ("first".equals(phase)) {
				populate(world, 64);
				world.setBlockState(CHEST, Blocks.CHEST.getDefaultState(), 2);
				TileEntityChest chest = (TileEntityChest) world.getTileEntity(CHEST);
				ItemStack stack = new ItemStack(Items.DIAMOND, 7);
				stack.setStackDisplayName("Old coal fixture diamonds");
				chest.setInventorySlotContents(0, stack);
				chest.markDirty();
				Chunk raw = world.getChunkProvider().provideChunk(160, 160);
				if (raw.isTerrainPopulated()) throw new IllegalStateException("The later-chunk fixture opened early");
				raw.setBlockState(RAW_MARKER, Blocks.LAPIS_ORE.getDefaultState());
				raw.setChunkModified();
			} else if ("complete".equals(phase)) {
				populate(world, 160);
			}

			JsonObject report = new JsonObject();
			report.addProperty("old_terrain", terrain(world, 64));
			TileEntityChest chest = (TileEntityChest) world.getTileEntity(CHEST);
			report.addProperty("chest", chest.writeToNBT(new NBTTagCompound()).toString());
			report.addProperty("raw_marker", world.getBlockState(RAW_MARKER).getBlock() == Blocks.LAPIS_ORE);
			if (!"first".equals(phase)) report.addProperty("later_terrain", terrain(world, 160));
			try (FileWriter writer = new FileWriter(new File("coal-upgrade-" + phase + ".json"))) {
				new GsonBuilder().setPrettyPrinting().create().toJson(report, writer);
			}
			System.out.println("RD_COAL_UPGRADE_PASS " + phase);
		} finally {
			server.initiateShutdown();
		}
	}

	private static void populate(WorldServer world, int base) {
		ChunkProviderServer provider = world.getChunkProvider();
		for (int x = base - 2; x <= base + 5; x++) {
			for (int z = base - 2; z <= base + 5; z++) provider.provideChunk(x, z);
		}
		for (int x = base - 1; x <= base + 3; x++) {
			for (int z = base - 1; z <= base + 3; z++) {
				Chunk chunk = provider.provideChunk(x, z);
				chunk.populateChunk(provider, provider.chunkGenerator);
			}
		}
	}

	private static String terrain(WorldServer world, int base) throws Exception {
		MessageDigest digest = MessageDigest.getInstance("SHA-256");
		for (int cx = base; cx < base + 3; cx++) {
			for (int cz = base; cz < base + 3; cz++) {
				Chunk chunk = world.getChunkProvider().provideChunk(cx, cz);
				if (!chunk.isTerrainPopulated()) throw new IllegalStateException("Unexpected unopened sample");
				for (int x = 0; x < 16; x++) {
					for (int z = 0; z < 16; z++) {
						for (int y = 0; y < 256; y++) {
							int state = Block.getStateId(chunk.getBlockState(x, y, z));
							digest.update((byte) (state >>> 24));
							digest.update((byte) (state >>> 16));
							digest.update((byte) (state >>> 8));
							digest.update((byte) state);
						}
					}
				}
			}
		}
		StringBuilder text = new StringBuilder();
		for (byte value : digest.digest()) text.append(String.format("%02x", value & 255));
		return text.toString();
	}
}
