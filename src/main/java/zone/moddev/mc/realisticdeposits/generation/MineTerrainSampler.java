package zone.moddev.mc.realisticdeposits.generation;

import java.util.HashMap;
import java.util.Map;

import net.minecraft.block.material.Material;
import net.minecraft.block.state.IBlockState;
import net.minecraft.init.Blocks;
import net.minecraft.world.WorldServer;
import net.minecraft.world.chunk.ChunkPrimer;
import net.minecraft.world.chunk.IChunkGenerator;
import net.minecraft.world.gen.ChunkProviderFlat;
import net.minecraft.world.gen.ChunkProviderOverworld;
import net.minecraft.world.gen.FlatGeneratorInfo;
import net.minecraft.world.gen.FlatLayerInfo;

import zone.moddev.mc.realisticdeposits.geometry.AbandonedMinePlan;
import zone.moddev.mc.realisticdeposits.geometry.MineBlock;
import zone.moddev.mc.realisticdeposits.geometry.MinePoint;

/** Samples base terrain in memory; never asks the world to load another chunk. */
final class MineTerrainSampler {
	private final IChunkGenerator generator;
	private final FlatGeneratorInfo flat;
	private final Map<Long, ChunkPrimer> samples = new HashMap<>();
	private final Map<Long, Integer> heights = new HashMap<>();

	MineTerrainSampler(WorldServer world, IChunkGenerator generator) {
		if (generator.getClass() != ChunkProviderOverworld.class && generator.getClass() != ChunkProviderFlat.class) {
			throw new IllegalArgumentException("Abandoned mines do not yet support this world's terrain generator");
		}

		this.generator = generator;
		flat = generator instanceof ChunkProviderFlat
				? FlatGeneratorInfo.createFlatGeneratorFromString(world.getWorldInfo().getGeneratorOptions()) : null;
	}

	int surfaceY(MinePoint landing) {
		int surface = heightAt(landing.x(), landing.z());
		if (surface < 0) return -1;

		for (int dx = -2; dx <= 2; dx++) {
			for (int dz = -2; dz <= 2; dz++) {
				int nearby = heightAt(landing.x() + dx, landing.z() + dz);
				if (nearby < 0 || Math.abs(nearby - surface) > 1) return -1;
			}
		}

		return surface;
	}

	boolean dry(AbandonedMinePlan plan) {
		int[][] neighbours = {{0,0,0}, {1,0,0}, {-1,0,0}, {0,1,0}, {0,-1,0}, {0,0,1}, {0,0,-1}};
		for (Map.Entry<Long, MineBlock> cell : plan.blocks().entrySet()) {
			if (cell.getValue() != MineBlock.AIR) continue;
			MinePoint point = MinePoint.unpack(cell.getKey());

			for (int[] offset : neighbours) {
				if (state(point.x() + offset[0], point.y() + offset[1], point.z() + offset[2])
						.getMaterial().isLiquid()) return false;
			}
		}

		return true;
	}

	boolean covered(AbandonedMinePlan plan) {
		for (Map.Entry<Long, MineBlock> cell : plan.blocks().entrySet()) {
			MinePoint point = MinePoint.unpack(cell.getKey());
			if (Math.abs(point.x() - plan.landing().x()) <= 2
					&& Math.abs(point.z() - plan.landing().z()) <= 2) continue;
			if (heightAt(point.x(), point.z()) < point.y() + 2) return false;
		}

		return true;
	}

	IBlockState state(int x, int y, int z) {
		if (y < 0 || y > 255) return Blocks.BEDROCK.getDefaultState();
		int chunkX = Math.floorDiv(x, 16);
		int chunkZ = Math.floorDiv(z, 16);
		long key = MinePoint.chunkKey(chunkX, chunkZ);
		ChunkPrimer primer = samples.get(key);
		if (primer == null) {
			primer = new ChunkPrimer();
			if (flat == null) {
				// This public vanilla method fills only the supplied primer. Calling
				// provideChunk here would also generate structures and is not safe.
				((ChunkProviderOverworld) generator).setBlocksInChunk(chunkX, chunkZ, primer);
			} else {
				for (FlatLayerInfo layer : flat.getFlatLayers()) {
					for (int localX = 0; localX < 16; localX++) {
						for (int localZ = 0; localZ < 16; localZ++) {
							for (int layerY = layer.getMinY(); layerY < layer.getMinY() + layer.getLayerCount(); layerY++) {
								primer.setBlockState(localX, layerY, localZ, layer.getLayerMaterial());
							}
						}
					}
				}
			}

			samples.put(key, primer);
		}

		return primer.getBlockState(x & 15, y, z & 15);
	}

	private int heightAt(int x, int z) {
		long column = MinePoint.chunkKey(x, z);
		Integer cached = heights.get(column);
		if (cached != null) return cached;

		for (int y = 255; y >= 0; y--) {
			IBlockState block = state(x, y, z);
			if (block.getMaterial() != Material.AIR) {
				int height = block.getMaterial().isLiquid() ? -1 : y + 1;
				heights.put(column, height);
				return height;
			}
		}

		heights.put(column, -1);
		return -1;
	}
}
