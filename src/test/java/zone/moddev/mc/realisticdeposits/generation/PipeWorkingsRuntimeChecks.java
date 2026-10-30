package zone.moddev.mc.realisticdeposits.generation;

import static zone.moddev.mc.realisticdeposits.generation.MineWorkingsRuntimeProbe.*;

import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.Properties;

import com.google.gson.JsonObject;

import net.minecraft.init.Blocks;
import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntityChest;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.WorldServer;
import net.minecraft.world.gen.structure.MapGenStructureData;

import zone.moddev.mc.orespawn.api.GeologySampler;
import zone.moddev.mc.orespawn.api.OreSpawnApi;
import zone.moddev.mc.realisticdeposits.config.DepositCatalogLoader;
import zone.moddev.mc.realisticdeposits.config.RealisticDepositsRuntime;
import zone.moddev.mc.realisticdeposits.geometry.AbandonedMinePlan;
import zone.moddev.mc.realisticdeposits.geometry.AbandonedMinePlanner;
import zone.moddev.mc.realisticdeposits.geometry.DiamondDistrict;
import zone.moddev.mc.realisticdeposits.geometry.DiamondPipeWorkingsBody;
import zone.moddev.mc.realisticdeposits.geometry.MinePoint;
import zone.moddev.mc.realisticdeposits.model.AbandonedMineSettings;
import zone.moddev.mc.realisticdeposits.model.DepositDefinition;

/** Diamond access checks share the iron suite's exact block and reload assertions. */
final class PipeWorkingsRuntimeChecks {
	private PipeWorkingsRuntimeChecks() { }

	static void run(WorldServer world, String phase) throws Exception {
		DepositDefinition captured = RealisticDepositsRuntime.state().definitions().stream()
				.filter(DepositDefinition::isDiamondPipe).findFirst().get();
		JsonObject enabled = captured.toJson();
		enabled.addProperty("enabled", true);
		DepositDefinition geometry = DepositCatalogLoader.loadDefinition(enabled, "pipe mine geometry");
		String scenario = System.getProperty("rd.mines.scenario", "flat");
		boolean obstructed = "blocked".equals(scenario);
		boolean undergroundWater = "mineralogy".equals(scenario);
		if (scenario.startsWith("mineralogy")) {
			require(CoalGeology.active().physicalStrata, "The Mineralogy fixture did not activate physical strata");
		}
		MineTerrainSampler terrain = new MineTerrainSampler(world, world.getChunkProvider().chunkGenerator);
		Optional<GeologySampler> sampler = OreSpawnApi.createSampler(world);
		Properties target = new Properties();
		DiamondPipeWorkingsBody body = null;
		AbandonedMinePlan plan = null;

		if ("first".equals(phase)) {
			int loaded = world.getChunkProvider().getLoadedChunkCount();
			int start = Integer.getInteger("rd.mines.startRegion", 32);
			search: for (int rx = start; rx < start + 32; rx++) {
				for (int rz = start; rz < start + 32; rz++) {
					Optional<DiamondDistrict> district = district(world, geometry, terrain, sampler, rx, rz);
					if (!district.isPresent()) continue;

					for (int index = 0; index < district.get().pipes().size(); index++) {
						DiamondPipeWorkingsBody candidate = new DiamondPipeWorkingsBody(district.get(), index);
						int surface = "wet".equals(scenario) ? 129 : terrain.surfaceY(AbandonedMinePlanner.landing(candidate));
						Optional<AbandonedMinePlan> proposed = "wet".equals(scenario)
								? AbandonedMinePlanner.plan(candidate, new AbandonedMineSettings(true, 1.0D), surface)
								: AbandonedMinePlanner.plan(candidate, new AbandonedMineSettings(true, 1.0D), terrain::naturalSurfaceY);
						if (!proposed.isPresent()) continue;
						if (!"wet".equals(scenario) && (terrain.surfaceY(proposed.get().entrance()) != proposed.get().entrance().y()
								|| !terrain.dry(proposed.get()) || !terrain.covered(proposed.get())
								|| !MineWorkingsGenerator.biomesAllowed(proposed.get(), geometry, world))) continue;

						body = candidate;
						plan = proposed.get();
						target.setProperty("region_x", Integer.toString(rx));
						target.setProperty("region_z", Integer.toString(rz));
						target.setProperty("pipe_index", Integer.toString(index));
						break search;
					}
				}
			}

			require(loaded == world.getChunkProvider().getLoadedChunkCount(), "Pipe mine search loaded a neighbouring chunk");
			require(body != null, "No safe pipe mine in the bounded search");
			target.setProperty("key", body.identity().toString());
			target.setProperty("entrance", plan.entrance().toString());
			target.setProperty("landing", plan.landing().toString());
			target.setProperty("plan", planHash(plan));
			store("pipe-target.properties", target);
		} else {
			try (FileInputStream input = new FileInputStream("pipe-target.properties")) {
				target.load(input);
			}

			DiamondDistrict district = district(world, geometry, terrain, sampler,
					Integer.parseInt(target.getProperty("region_x")), Integer.parseInt(target.getProperty("region_z"))).get();
			body = new DiamondPipeWorkingsBody(district, Integer.parseInt(target.getProperty("pipe_index")));
			int surface = "wet".equals(scenario) ? 129 : terrain.surfaceY(AbandonedMinePlanner.landing(body));
			plan = "wet".equals(scenario) ? AbandonedMinePlanner.plan(body, new AbandonedMineSettings(true, 1.0D), surface).get()
					: AbandonedMinePlanner.plan(body, new AbandonedMineSettings(true, 1.0D), terrain::naturalSurfaceY).get();
			require(target.getProperty("plan").equals(planHash(plan)), "Pipe geometry moved on reload");
		}

		String key = target.getProperty("key");
		boolean rejected = !captured.enabled() || !captured.abandonedMine().enabled()
				|| captured.abandonedMine().chance() == 0 || "wet".equals(scenario) || "structure".equals(scenario);
		Properties report = new Properties();
		report.putAll(target);
		report.setProperty("phase", phase);
		MineWorldData data = MineWorldData.get(world);

		if ("first".equals(phase)) {
			if (obstructed) {
				BlockPos chestPosition = new BlockPos(plan.landing().x(), plan.landing().y() + 1, plan.landing().z());
				net.minecraft.world.chunk.Chunk chunk = world.getChunkProvider().provideChunk(chestPosition.getX() >> 4, chestPosition.getZ() >> 4);
				require(!chunk.isTerrainPopulated(), "The obstruction must start in an unpopulated chunk");
				chunk.setBlockState(chestPosition, Blocks.CHEST.getDefaultState());
				TileEntityChest chest = (TileEntityChest) world.getTileEntity(chestPosition);
				chest.setCustomName("Protected decline chest");
				chest.setInventorySlotContents(0, new ItemStack(Items.DIAMOND, 3));
			}

			if ("structure".equals(scenario)) {
				MapGenStructureData structures = new MapGenStructureData("Mineshaft");
				NBTTagCompound existing = new NBTTagCompound();
				existing.setIntArray("BB", new int[] {plan.bounds().minimumX(), plan.bounds().minimumY(),
						plan.bounds().minimumZ(), plan.bounds().maximumX(), plan.bounds().maximumY(), plan.bounds().maximumZ()});
				structures.writeInstance(existing, plan.landing().x() >> 4, plan.landing().z() >> 4);
				world.getMapStorage().setData("Mineshaft", structures);
			}

			if ("reverse".equals(scenario) || rejected || scenario.startsWith("normal") || "regular".equals(scenario)
					|| "mineralogy".equals(scenario) || obstructed) {
				List<Long> chunks = allChunks(plan);
				if ("reverse".equals(scenario)) Collections.reverse(chunks);
				for (long chunk : chunks) populate(world, (int) (chunk >> 32), (int) chunk);
			} else {
				populate(world, plan.landing().x() >> 4, plan.landing().z() >> 4);
			}

			if (!rejected) {
				require(data.plan(key) != null, "Pipe mine rejected: " + data.rejectionReason(key));
				require(plan.blocks().equals(data.plan(key).blocks()), "Native pipe mine differs from its planner");
				if (!obstructed && !undergroundWater) {
					verify(world, plan, populatedChunks(world, plan), false);
					world.setBlockState(marker(plan), Blocks.DIAMOND_BLOCK.getDefaultState(), 2);
				}
			}

			List<Long> old = populatedChunks(world, plan);
			target.setProperty("old_chunks", join(old));
			target.setProperty("terrain", terrainHash(world, old));
			store("pipe-target.properties", target);
			if ("flat".equals(scenario)) require(old.size() < allChunks(plan).size(), "No pipe mine sections left for later exploration");
		} else {
			List<Long> old = split(target.getProperty("old_chunks"));
			require(target.getProperty("terrain").equals(terrainHash(world, old)), "Reload changed old pipe terrain or a player marker");
			if ("later".equals(phase)) {
				for (long chunk : allChunks(plan)) populate(world, (int) (chunk >> 32), (int) chunk);
				target.setProperty("completed_terrain", terrainHash(world, allChunks(plan)));
				store("pipe-target.properties", target);
			} else if ("stable".equals(phase)) {
				require(target.getProperty("completed_terrain").equals(terrainHash(world, allChunks(plan))),
						"Reload changed the later-generated pipe sections");
			}
			require(target.getProperty("terrain").equals(terrainHash(world, old)), "Later workings excavated old chunks");
		}

		if (rejected) {
			require(data.plan(key) == null, "Disabled or unsafe pipe acquired a mine");
			if ("structure".equals(scenario)) require("Another structure".equals(data.rejectionReason(key)), "Structure was not protected");
		} else {
			require(data.plan(key) != null && planHash(data.plan(key)).equals(target.getProperty("plan")), "Saved pipe mine changed");
			if (obstructed) {
				BlockPos chestPosition = new BlockPos(plan.landing().x(), plan.landing().y() + 1, plan.landing().z());
				require(world.getBlockState(chestPosition).getBlock() == Blocks.CHEST, "The obstructing chest was removed");
				TileEntityChest chest = (TileEntityChest) world.getTileEntity(chestPosition);
				require(chest != null && "Protected decline chest".equals(chest.getName())
						&& chest.getStackInSlot(0).stackSize == 3, "Obstructing chest data changed");
				require(world.getBlockState(chestPosition.down(2)).getBlock() != Blocks.PLANKS,
						"The unsafe section was partly excavated");
				report.setProperty("obstructed_section_preserved", "true");
			} else if (undergroundWater) {
				// This native-strata site has a spring beside the decline. The
				// safety check must leave that whole section sealed, not drain it.
				BlockPos spring = new BlockPos(49495, 36, 56632);
				require(world.getBlockState(spring).getMaterial().isLiquid(), "The protected underground spring changed");
				for (AbandonedMinePlan.Cell cell : plan.cellsInChunk(spring.getX() >> 4, spring.getZ() >> 4)) {
					if (cell.block() == zone.moddev.mc.realisticdeposits.geometry.MineBlock.AIR) continue;
					MinePoint point = cell.point();
					require(!world.getBlockState(new BlockPos(point.x(), point.y(), point.z())).equals(expected(cell.block())),
							"The flooded section was partly excavated at " + point);
				}
				report.setProperty("underground_water_preserved", "true");
			} else {
				List<Long> built = populatedChunks(world, plan);
				report.setProperty("checked_cells", Integer.toString(verify(world, plan, built, true)));
				if (built.size() == allChunks(plan).size()) verifyAccess(world, plan, true);
			}
		}

		store("mine-" + phase + "-pass.properties", report);
	}

	private static Optional<DiamondDistrict> district(WorldServer world, DepositDefinition definition,
			MineTerrainSampler terrain, Optional<GeologySampler> sampler, int rx, int rz) {
		return DiamondDistrict.plan(definition, world.getSeed(), "minecraft:overworld", rx, rz, new DiamondDistrict.Site() {
			@Override public int surfaceY(int x, int z) {
				return Boolean.getBoolean("rd.mines.wet") ? 129 : terrain.naturalSurfaceY(x, z);
			}

			@Override public String geome(int x, int z, int y) {
				return sampler.isPresent() ? sampler.get().sampleColumn(x, z, y).geome().toString() : null;
			}
		});
	}

	private static void store(String name, Properties properties) throws Exception {
		try (FileOutputStream output = new FileOutputStream(name)) {
			properties.store(output, "Disposable diamond pipe workings");
		}
	}
}
