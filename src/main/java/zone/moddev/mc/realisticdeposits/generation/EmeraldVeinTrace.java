package zone.moddev.mc.realisticdeposits.generation;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;

import net.minecraft.block.state.IBlockState;
import net.minecraft.init.Blocks;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.WorldServer;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.chunk.storage.ExtendedBlockStorage;

import zone.moddev.mc.realisticdeposits.geometry.MinePoint;

/** A pale, non-ore cue written after ores and workings in the current fresh chunk. */
final class EmeraldVeinTrace {
	private static final Map<WorldServer, Map<Long, List<Slice>>> QUEUE = new WeakHashMap<>();

	private EmeraldVeinTrace() { }

	static void offer(WorldServer world, long chunk, List<MinePoint> cells, Set<IBlockState> hosts, int cover) {
		if (cells.isEmpty()) return;
		Map<Long, List<Slice>> pending = QUEUE.computeIfAbsent(world, unused -> new LinkedHashMap<>());
		pending.computeIfAbsent(chunk, unused -> new ArrayList<>()).add(new Slice(cells, hosts, cover));
		if (pending.size() > 128) pending.remove(pending.keySet().iterator().next());
	}

	static List<Slice> take(WorldServer world, long chunk) {
		Map<Long, List<Slice>> pending = QUEUE.get(world);
		List<Slice> slices = pending == null ? null : pending.remove(chunk);
		return slices == null ? Collections.emptyList() : slices;
	}

	static void unload(WorldServer world) { QUEUE.remove(world); }

	static void place(WorldServer world, Chunk chunk, List<Slice> slices) {
		if (slices.isEmpty()) return;
		IBlockState marker = Blocks.STONE.getStateFromMeta(3); // natural diorite
		List<int[]> structures = CoalStructureProtection.inChunk(chunk.xPosition, chunk.zPosition);
		BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
		ExtendedBlockStorage[] sections = chunk.getBlockStorageArray();
		boolean modified = false;
		for (Slice slice : slices) {
			for (MinePoint point : slice.cells) {
				int x = point.x(), y = point.y(), z = point.z();
				if (x >> 4 != chunk.xPosition || z >> 4 != chunk.zPosition
						|| y > chunk.getHeightValue(x & 15, z & 15) - slice.cover
						|| !world.getWorldBorder().contains(cursor.setPos(x, y, z))
						|| CoalStructureProtection.contains(structures, x, y, z)) continue;
				IBlockState current = chunk.getBlockState(cursor);
				if (current == marker || !slice.hosts.contains(current) || current.getBlock().hasTileEntity(current)) continue;
				ExtendedBlockStorage section = sections[y >> 4];
				if (section == null) continue;
				section.set(x & 15, y & 15, z & 15, marker);
				modified = true;
			}
		}
		if (modified) chunk.setChunkModified();
	}

	static final class Slice {
		private final List<MinePoint> cells;
		private final Set<IBlockState> hosts;
		private final int cover;

		private Slice(List<MinePoint> cells, Set<IBlockState> hosts, int cover) {
			this.cells = cells;
			this.hosts = hosts;
			this.cover = cover;
		}
	}
}
