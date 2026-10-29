package zone.moddev.mc.realisticdeposits.generation;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.WorldServer;
import net.minecraft.world.gen.structure.MapGenStructureData;
import net.minecraftforge.fml.common.FMLCommonHandler;

/** Leaves known vanilla structures alone, including their natural-looking stone. */
final class CoalStructureProtection {
	private static final String[] NAMES = {"Mineshaft", "Village", "Stronghold", "Temple", "Monument"};

	private CoalStructureProtection() { }

	static List<int[]> inChunk(int chunkX, int chunkZ) {
		MinecraftServer server = FMLCommonHandler.instance().getMinecraftServerInstance();
		WorldServer world = server == null ? null : server.worldServerForDimension(0);
		if (world == null) return Collections.emptyList();
		List<int[]> boxes = new ArrayList<>();

		for (String name : NAMES) {
			MapGenStructureData data = (MapGenStructureData) world.getMapStorage().getOrLoadData(MapGenStructureData.class, name);
			if (data == null) continue;
			NBTTagCompound root = data.getTagCompound();
			for (String key : root.getKeySet()) {
				int[] box = root.getCompoundTag(key).getIntArray("BB");
				if (box.length == 6 && box[0] <= (chunkX << 4) + 15 && box[3] >= (chunkX << 4)
						&& box[2] <= (chunkZ << 4) + 15 && box[5] >= (chunkZ << 4)) boxes.add(box);
			}
		}
		return boxes;
	}

	static boolean contains(List<int[]> boxes, int x, int y, int z) {
		for (int[] box : boxes) {
			if (x >= box[0] && x <= box[3] && y >= box[1] && y <= box[4] && z >= box[2] && z <= box[5]) return true;
		}
		return false;
	}
}
