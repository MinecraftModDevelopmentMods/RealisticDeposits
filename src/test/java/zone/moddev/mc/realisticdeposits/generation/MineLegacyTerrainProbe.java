package zone.moddev.mc.realisticdeposits.generation;

import java.io.FileOutputStream;
import java.nio.ByteBuffer;
import java.security.MessageDigest;
import java.util.Properties;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import net.minecraft.block.Block;
import net.minecraft.init.Blocks;
import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;
import net.minecraft.server.MinecraftServer;
import net.minecraft.tileentity.TileEntityChest;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.WorldServer;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.gen.ChunkProviderServer;
import net.minecraftforge.fml.common.FMLCommonHandler;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.event.FMLServerStartedEvent;

/** Creates the old-release world fixture without referring to the new mine classes. */
@Mod(modid = "rdminelegacyprobe", name = "Old Terrain Mine Fixture", version = "1",
		dependencies = "required-after:realisticdeposits")
public final class MineLegacyTerrainProbe {
	private static final Logger LOG = LogManager.getLogger("rdminelegacyprobe");

	@Mod.EventHandler
	public void started(FMLServerStartedEvent event) {
		if (!Boolean.getBoolean("rd.mines.legacy")) return;

		MinecraftServer server = FMLCommonHandler.instance().getMinecraftServerInstance();
		try {
			WorldServer world = server.worldServerForDimension(0);
			if (world.getSeed() != 3346071746553592696L
					|| !world.getSaveHandler().getWorldDirectory().getName().equals("mine-test-world")) {
				throw new IllegalStateException("This fixture is restricted to its disposable world");
			}

			BlockPos marker = new BlockPos(value("markerX"), value("markerY"), value("markerZ"));
			BlockPos rawMarker = new BlockPos(value("rawX"), value("rawY"), value("rawZ"));
			int chunkX = marker.getX() >> 4;
			int chunkZ = marker.getZ() >> 4;
			ChunkProviderServer provider = world.getChunkProvider();
			for (int dx = 0; dx <= 1; dx++) {
				for (int dz = 0; dz <= 1; dz++) provider.provideChunk(chunkX + dx, chunkZ + dz);
			}
			Chunk old = provider.provideChunk(chunkX, chunkZ);
			old.populateChunk(provider, provider.chunkGenerator);
			world.setBlockState(marker, Blocks.DIAMOND_BLOCK.getDefaultState(), 2);
			BlockPos chestPosition = marker.add(0, 1, 0);
			world.setBlockState(chestPosition, Blocks.CHEST.getDefaultState(), 2);
			TileEntityChest chest = (TileEntityChest) world.getTileEntity(chestPosition);
			chest.setCustomName("Old mine fixture");
			ItemStack diamonds = new ItemStack(Items.DIAMOND, 7);
			diamonds.setStackDisplayName("Old fixture diamonds");
			chest.setInventorySlotContents(0, diamonds);
			chest.markDirty();

			Chunk raw = provider.provideChunk(rawMarker.getX() >> 4, rawMarker.getZ() >> 4);
			if (raw.isTerrainPopulated()) throw new IllegalStateException("The fixture needs a later-opened unpopulated chunk");
			raw.getBlockStorageArray()[rawMarker.getY() >> 4].set(rawMarker.getX() & 15,
					rawMarker.getY() & 15, rawMarker.getZ() & 15, Blocks.EMERALD_BLOCK.getDefaultState());
			raw.setChunkModified();

			Properties report = new Properties();
			report.setProperty("marker", Long.toString(marker.toLong()));
			report.setProperty("raw_marker", Long.toString(rawMarker.toLong()));
			report.setProperty("old_terrain", terrainHash(old));
			report.setProperty("raw_terrain", terrainHash(raw));
			try (FileOutputStream output = new FileOutputStream("mine-legacy.properties")) {
				report.store(output, "World made with the released Realistic Deposits alpha");
			}
			LOG.info("RD_MINES_LEGACY_PASS");
		} catch (Throwable failure) {
			LOG.error("Old-terrain fixture failed", failure);
		} finally {
			server.initiateShutdown();
		}
	}

	private static int value(String key) {
		return Integer.parseInt(System.getProperty("rd.mines.legacy." + key));
	}

	static String terrainHash(Chunk chunk) throws Exception {
		MessageDigest digest = MessageDigest.getInstance("SHA-256");
		ByteBuffer value = ByteBuffer.allocate(4);
		for (int x = 0; x < 16; x++) {
			for (int z = 0; z < 16; z++) {
				for (int y = 0; y < 256; y++) {
					value.clear();
					value.putInt(Block.getStateId(chunk.getBlockState(x, y, z)));
					digest.update(value.array());
				}
			}
		}
		StringBuilder result = new StringBuilder();
		for (byte part : digest.digest()) result.append(String.format("%02x", part & 255));
		return result.toString();
	}
}
