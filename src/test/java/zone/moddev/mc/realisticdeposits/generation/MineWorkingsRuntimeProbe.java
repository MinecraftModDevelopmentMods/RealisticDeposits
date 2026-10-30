package zone.moddev.mc.realisticdeposits.generation;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.nio.ByteBuffer;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Random;
import java.util.Set;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import net.minecraft.block.BlockLadder;
import net.minecraft.block.BlockStairs;
import net.minecraft.entity.monster.EntityZombie;
import net.minecraft.block.BlockRail;
import net.minecraft.block.BlockRailBase;
import net.minecraft.block.state.IBlockState;
import net.minecraft.init.Blocks;
import net.minecraft.server.MinecraftServer;
import net.minecraft.tileentity.TileEntityChest;
import net.minecraft.init.Items;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.WorldServer;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.gen.ChunkProviderServer;
import net.minecraft.world.gen.structure.MapGenStructureData;
import net.minecraftforge.fml.common.FMLCommonHandler;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.event.FMLServerStartedEvent;

import zone.moddev.mc.realisticdeposits.config.RealisticDepositsRuntime;
import zone.moddev.mc.realisticdeposits.geometry.AbandonedMinePlan;
import zone.moddev.mc.realisticdeposits.geometry.AbandonedMinePlanner;
import zone.moddev.mc.realisticdeposits.geometry.MineBlock;
import zone.moddev.mc.realisticdeposits.geometry.MinePoint;
import zone.moddev.mc.realisticdeposits.geometry.MineDecline;
import zone.moddev.mc.realisticdeposits.geometry.RegionDepositPlanner;
import zone.moddev.mc.realisticdeposits.geometry.StratiformSeamBody;
import zone.moddev.mc.realisticdeposits.model.AbandonedMineSettings;
import zone.moddev.mc.realisticdeposits.model.DepositDefinition;
import zone.moddev.mc.orespawn.api.OreGenerationContext;
import zone.moddev.mc.orespawn.api.GeologySampler;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.fluids.Fluid;

/** Runs only in disposable packaged-server fixtures, never in normal launches. */
@Mod(modid = "rdmineprobe", name = "Abandoned Mine Qualification", version = "1",
		dependencies = "required-after:realisticdeposits")
public final class MineWorkingsRuntimeProbe {
	private static final Logger LOG = LogManager.getLogger("rdmineprobe");
	private static final long SEED = 3346071746553592696L;

	@Mod.EventHandler
	public void started(FMLServerStartedEvent event) {
		if (!Boolean.getBoolean("rd.mines.enabled")) return;

		MinecraftServer server = FMLCommonHandler.instance().getMinecraftServerInstance();
		String phase = System.getProperty("rd.mines.phase");
		try {
			WorldServer world = server.worldServerForDimension(0);
			require(world.getSeed() == SEED, "This probe is restricted to its disposable fixed-seed world");
			require(world.getSaveHandler().getWorldDirectory().getName().equals("mine-test-world"), "Unexpected world directory");
			if (Boolean.getBoolean("rd.mines.pipe")) {
				PipeWorkingsRuntimeChecks.run(world, phase);
				LOG.info("RD_MINES_PASS {}", phase);
				return;
			}

			DepositDefinition definition = RealisticDepositsRuntime.state().definitions().get(0);
			if (phase.startsWith("default-")) {
				normalWorld(world, definition, phase);
				LOG.info("RD_MINES_PASS {}", phase);
				return;
			}
			StratiformSeamBody body = distantBody(definition);
			AbandonedMinePlan expected = AbandonedMinePlanner.plan(body,
					new AbandonedMineSettings(true, 1.0D), 129).get();
			String key = body.identity().toString();
			Properties report = new Properties();
			report.setProperty("phase", phase);
			report.setProperty("entrance", expected.entrance().toString());
			report.setProperty("landing", expected.landing().toString());
			report.setProperty("mine_chunks", join(allChunks(expected)));
			List<Long> fixtureChunks = allChunks(expected);
			report.setProperty("legacy_marker", firstAir(expected, fixtureChunks.get(0)).toString());
			report.setProperty("legacy_raw_marker", firstAir(expected, fixtureChunks.get(fixtureChunks.size() - 1)).toString());

			if (phase.startsWith("upgrade-")) {
				upgrade(world, definition, body, expected, phase, report);
			} else if (phase.startsWith("order-")) {
				List<Long> chunks = allChunks(expected);
				if ("order-reverse".equals(phase)) {
					Collections.reverse(chunks);
				} else {
					Collections.shuffle(chunks, new Random(34919L));
				}

				for (long chunk : chunks) populate(world, (int) (chunk >> 32), (int) chunk);
				MineWorldData data = MineWorldData.get(world);
				require(data.plan(key) != null, "Generation order rejected the mine: " + data.rejectionReason(key));
				require(expected.blocks().equals(data.plan(key).blocks()), "Generation order changed the saved layout");
				report.setProperty("checked_cells", Integer.toString(verify(world, expected, allChunks(expected), false)));
				report.setProperty("plan", planHash(data.plan(key)));
			} else if ("structure".equals(phase)) {
				MapGenStructureData structures = new MapGenStructureData("Mineshaft");
				NBTTagCompound existing = new NBTTagCompound();
				existing.setIntArray("BB", new int[] {expected.bounds().minimumX(), expected.bounds().minimumY(),
						expected.bounds().minimumZ(), expected.bounds().maximumX(), expected.bounds().maximumY(),
						expected.bounds().maximumZ()});
				structures.writeInstance(existing, expected.landing().x() >> 4, expected.landing().z() >> 4);
				world.getMapStorage().setData("Mineshaft", structures);

				for (long chunk : allChunks(expected)) populate(world, (int) (chunk >> 32), (int) chunk);
				MineWorldData data = MineWorldData.get(world);
				require(data.plan(key) == null && "Another structure".equals(data.rejectionReason(key)),
						"A known native structure did not protect its terrain");
			} else if ("blocked".equals(phase)) {
				long target = MinePoint.chunkKey(expected.landing().x() >> 4, expected.landing().z() >> 4);
				MinePoint obstruction = firstAir(expected, target);
				Chunk chunk = world.getChunkProvider().provideChunk((int) (target >> 32), (int) target);
				require(!chunk.isTerrainPopulated(), "The obstruction fixture must start in a raw chunk");
				BlockPos position = new BlockPos(obstruction.x(), obstruction.y(), obstruction.z());
				chunk.setBlockState(position, Blocks.CHEST.getDefaultState());
				TileEntityChest chest = (TileEntityChest) world.getTileEntity(position);
				chest.setCustomName("Protected fixture chest");
				chest.setInventorySlotContents(0, new net.minecraft.item.ItemStack(Items.DIAMOND, 3));
				for (long section : allChunks(expected)) populate(world, (int) (section >> 32), (int) section);
				require(world.getBlockState(position).getBlock() == Blocks.CHEST, "The mine overwrote a foreign block");
				chest = (TileEntityChest) world.getTileEntity(position);
				require(chest != null && "Protected fixture chest".equals(chest.getName())
						&& chest.getStackInSlot(0).stackSize == 3, "The mine changed an obstructing chest");
				require(MineWorldData.get(world).unbuiltMinesInChunk(chunk.xPosition, chunk.zPosition).isEmpty(),
						"A sealed section was left eligible for another excavation attempt");
			} else if ("first".equals(phase)) {
				int x = Math.floorDiv(expected.landing().x(), 16);
				int z = Math.floorDiv(expected.landing().z(), 16);
				populate(world, x, z);
				MineWorldData data = MineWorldData.get(world);
				require(data.plan(key) != null, "No accepted mine: " + data.rejectionReason(key));
				require(expected.blocks().equals(data.plan(key).blocks()), "Native plan differs from the pure planner");
				List<Long> generated = populatedChunks(world, expected);
				require(!generated.isEmpty(), "No first slice was generated");
				require(verify(world, expected, generated, false) > 0, "No planned cells were checked");
				require(generated.size() < allChunks(expected).size(), "The first phase must leave later chunks unexplored");

				world.setBlockState(marker(expected), Blocks.DIAMOND_BLOCK.getDefaultState(), 2);
				Properties first = new Properties();
				first.setProperty("chunks", join(generated));
				first.setProperty("terrain", terrainHash(world, generated));
				first.setProperty("plan", planHash(expected));
				try (FileOutputStream output = new FileOutputStream("mine-first.properties")) {
					first.store(output, "First generated mine slices");
				}
				report.setProperty("first_chunks", Integer.toString(generated.size()));
			} else if ("complete".equals(phase) || "reload".equals(phase)) {
				Properties first = new Properties();
				try (FileInputStream input = new FileInputStream("mine-first.properties")) {
					first.load(input);
				}

				List<Long> old = split(first.getProperty("chunks"));
				require(first.getProperty("terrain").equals(terrainHash(world, old)), "Old terrain or the player's marker changed");
				MineWorldData data = MineWorldData.get(world);
				require(first.getProperty("plan").equals(planHash(data.plan(key))), "The saved mine layout changed on reload");
				if ("complete".equals(phase)) {
					for (long chunk : allChunks(expected)) populate(world, (int) (chunk >> 32), (int) chunk);
				}

				require(first.getProperty("terrain").equals(terrainHash(world, old)), "Completing the mine changed old chunks");
				int checked = verify(world, expected, allChunks(expected), true);
				report.setProperty("checked_cells", Integer.toString(checked));
				require(world.getBlockState(marker(expected)).getBlock() == Blocks.DIAMOND_BLOCK, "Player marker was replaced");
			} else if ("off".equals(phase) || "wet".equals(phase)) {
				for (long chunk : allChunks(expected)) populate(world, (int) (chunk >> 32), (int) chunk);
				if ("off".equals(phase)) {
					require(!world.getSaveHandler().getMapFileFromName(MineWorldData.NAME).isFile(), "Opt-out created mine save data");
				} else {
					MineWorldData data = MineWorldData.get(world);
					require(data.plan(key) == null && data.decided(key), "The underwater site was not rejected");
				}

				for (long chunk : allChunks(expected)) {
					Chunk terrain = world.getChunkFromChunkCoords((int) (chunk >> 32), (int) chunk);
					for (AbandonedMinePlan.Cell cell : expected.cellsInChunk(terrain.xPosition, terrain.zPosition)) {
						IBlockState actual = terrain.getBlockState(new BlockPos(cell.point().x(), cell.point().y(), cell.point().z()));
						require(actual.getBlock() != Blocks.PLANKS && actual.getBlock() != Blocks.RAIL
								&& actual.getBlock() != Blocks.LADDER && actual.getBlock() != Blocks.WEB,
								"A disabled or underwater site contains mine blocks");
					}
				}
			} else {
				throw new IllegalArgumentException("Unknown mine probe phase " + phase);
			}

			try (FileOutputStream output = new FileOutputStream("mine-" + phase + "-pass.properties")) {
				report.store(output, "Packaged abandoned-mine qualification");
			}
			LOG.info("RD_MINES_PASS {}", phase);
		} catch (Throwable failure) {
			LOG.error("Abandoned-mine qualification failed", failure);
		} finally {
			server.initiateShutdown();
		}
	}

	static int verify(WorldServer world, AbandonedMinePlan plan, List<Long> chunks, boolean hasMarker) {
		int checked = 0;
		for (long key : chunks) {
			Chunk chunk = world.getChunkProvider().loadChunk((int) (key >> 32), (int) key);
			require(chunk != null, "A claimed completed chunk was not saved");
			for (AbandonedMinePlan.Cell cell : plan.cellsInChunk(chunk.xPosition, chunk.zPosition)) {
				BlockPos position = new BlockPos(cell.point().x(), cell.point().y(), cell.point().z());
				if (hasMarker && position.equals(marker(plan))) continue;
				IBlockState actual = chunk.getBlockState(position);
				require(actual.equals(expected(cell.block())), "Mine cell mismatch at " + position
						+ ": expected " + cell.block() + ", got " + actual);
				checked++;
			}
		}
		return checked;
	}

	private static MinePoint firstAir(AbandonedMinePlan plan, long chunk) {
		for (AbandonedMinePlan.Cell cell : plan.cellsInChunk((int) (chunk >> 32), (int) chunk)) {
			if (cell.block() == MineBlock.AIR) return cell.point();
		}
		throw new IllegalStateException("The fixture chunk has no tunnel air");
	}

	private static void upgrade(WorldServer world, DepositDefinition definition, StratiformSeamBody body,
			AbandonedMinePlan expected, String phase, Properties report) throws Exception {
		Properties old = new Properties();
		try (FileInputStream input = new FileInputStream("mine-legacy.properties")) {
			old.load(input);
		}

		BlockPos marker = BlockPos.fromLong(Long.parseLong(old.getProperty("marker")));
		BlockPos rawMarker = BlockPos.fromLong(Long.parseLong(old.getProperty("raw_marker")));
		long populated = MinePoint.chunkKey(marker.getX() >> 4, marker.getZ() >> 4);
		long rawKey = MinePoint.chunkKey(rawMarker.getX() >> 4, rawMarker.getZ() >> 4);
		require(old.getProperty("old_terrain").equals(terrainHash(world, java.util.Collections.singletonList(populated))),
				"Upgrading changed the old populated chunk");
		TileEntityChest chest = (TileEntityChest) world.getTileEntity(marker.add(0, 1, 0));
		require(chest != null && "Old mine fixture".equals(chest.getName()), "Old chest name was lost");
		require(chest.getStackInSlot(0) != null && chest.getStackInSlot(0).getItem() == Items.DIAMOND
				&& chest.getStackInSlot(0).stackSize == 7
				&& "Old fixture diamonds".equals(chest.getStackInSlot(0).getDisplayName()), "Old chest contents changed");

		if ("upgrade-later".equals(phase)) {
			require(world.getChunkProvider().getLoadedChunk(rawMarker.getX() >> 4, rawMarker.getZ() >> 4) == null,
					"The later-opened chunk was already loaded");
			Chunk raw = world.getChunkProvider().loadChunk(rawMarker.getX() >> 4, rawMarker.getZ() >> 4);
			require(raw != null && !raw.isTerrainPopulated(), "Expected the old saved raw chunk");
			require(old.getProperty("raw_terrain").equals(terrainHash(world, java.util.Collections.singletonList(rawKey))),
					"The unopened old chunk changed before population");

			// Exercise the excavation guard directly, before ordinary Minecraft
			// population is allowed to decorate this saved-but-unpopulated chunk.
			MineWorkingsGenerator.offer(new OldChunkContext(raw.xPosition, raw.zPosition), definition, body);
			MineWorkingsGenerator.INSTANCE.generate(new Random(1), raw.xPosition, raw.zPosition, world,
					world.getChunkProvider().chunkGenerator, world.getChunkProvider());
			require(old.getProperty("raw_terrain").equals(terrainHash(world, java.util.Collections.singletonList(rawKey))),
					"Mine generation excavated an old raw chunk");
			for (long chunk : allChunks(expected)) populate(world, (int) (chunk >> 32), (int) chunk);
			require(world.getBlockState(rawMarker).getBlock() == Blocks.EMERALD_BLOCK, "Later population lost the old marker");
		} else {
			require(world.getChunkProvider().getLoadedChunk(rawMarker.getX() >> 4, rawMarker.getZ() >> 4) == null,
					"The raw fixture must remain unopened until the later phase");
			populate(world, (marker.getX() >> 4) - 1, (marker.getZ() >> 4) - 1);
		}

		require(old.getProperty("old_terrain").equals(terrainHash(world, java.util.Collections.singletonList(populated))),
				"New chunks changed old populated terrain");
		String key = body.identity().toString();
		if (Boolean.getBoolean("rd.mines.upgrade.optIn")) {
			MineWorldData data = MineWorldData.get(world);
			require(data.plan(key) == null && "Existing terrain".equals(data.rejectionReason(key)),
					"Explicit opt-in did not reject a mine crossing old terrain: " + data.rejectionReason(key));
		} else {
			require(!world.getSaveHandler().getMapFileFromName(MineWorldData.NAME).isFile(),
					"An unchanged old world profile silently enabled mines");
		}
		report.setProperty("old_terrain_preserved", "true");
	}

	private static final class OldChunkContext implements OreGenerationContext {
		private final int x;
		private final int z;

		private OldChunkContext(int x, int z) {
			this.x = x;
			this.z = z;
		}

		@Override public long worldSeed() { return SEED; }
		@Override public ResourceLocation dimension() { return new ResourceLocation("minecraft:overworld"); }
		@Override public int chunkX() { return x; }
		@Override public int chunkZ() { return z; }
		@Override public java.util.Optional<GeologySampler> geologySampler() { return java.util.Optional.empty(); }
		@Override public Random random() { return new Random(1); }
		@Override public int originX() { return x << 4; }
		@Override public int originY() { return 0; }
		@Override public int originZ() { return z << 4; }
		@Override public int minY() { return 0; }
		@Override public int maxY() { return 255; }
		@Override public int quantity() { return 1; }
		@Override public int spread() { return 0; }
		@Override public int verticalSpread() { return 0; }
		@Override public int nodeSize() { return 1; }
		@Override public boolean inside(int px, int py, int pz) { return false; }
		@Override public boolean isFluid(int px, int py, int pz, Fluid fluid) { return false; }
		@Override public boolean tryPlace(int px, int py, int pz) { return false; }
	}

	private static void normalWorld(WorldServer world, DepositDefinition definition, String phase) throws Exception {
		Properties report = new Properties();
		MineWorldData data = MineWorldData.get(world);
		AbandonedMinePlan plan;
		String key;

		if ("default-first".equals(phase)) {
			int loadedBefore = world.getChunkProvider().getLoadedChunkCount();
			MineTerrainSampler terrain = new MineTerrainSampler(world, world.getChunkProvider().chunkGenerator);
			StratiformSeamBody chosen = null;
			plan = null;

			search: for (int x = 8; x < 72; x++) {
				for (int z = 8; z < 72; z++) {
					java.util.Optional<StratiformSeamBody> body = RegionDepositPlanner.plan(
							definition, SEED, "minecraft:overworld", x, z);
					if (!body.isPresent()) continue;
					java.util.Optional<AbandonedMinePlan> proposed = AbandonedMinePlanner.plan(body.get(),
							new AbandonedMineSettings(true, 1.0D), terrain::naturalSurfaceY);
					if (!proposed.isPresent()) continue;
					AbandonedMinePlan candidate = proposed.get();
					// Mountain decoration can add infested stone after the terrain
					// preview. Use an ordinary-rock site for the walking fixture;
					// obstruction handling has its own sealed-section checks.
					boolean mountains = candidate.blocks().keySet().stream().map(MinePoint::unpack)
							.anyMatch(point -> net.minecraftforge.common.BiomeDictionary.isBiomeOfType(
									world.getBiomeProvider().getBiome(new BlockPos(point.x(), 0, point.z())),
									net.minecraftforge.common.BiomeDictionary.Type.MOUNTAIN));
					if (mountains) continue;

					if (terrain.surfaceY(candidate.entrance()) == candidate.entrance().y()
							&& terrain.covered(candidate) && terrain.dry(candidate)
							&& MineWorkingsGenerator.biomesAllowed(candidate, definition, world)) {
						chosen = body.get();
						plan = candidate;
						break search;
					}
				}
			}

			require(loadedBefore == world.getChunkProvider().getLoadedChunkCount(), "Terrain sampling loaded neighbouring chunks");
			require(chosen != null && plan != null, "No suitable normal-Overworld fixture was found");
			key = chosen.identity().toString();
			for (long chunk : allChunks(plan)) populate(world, (int) (chunk >> 32), (int) chunk);
			require(data.plan(key) != null, "Normal terrain rejected the mine: " + data.rejectionReason(key));
			require(plan.blocks().equals(data.plan(key).blocks()), "Native normal-terrain plan mismatch");
			verify(world, plan, allChunks(plan), false);
			verifyAccess(world, plan, false);
			world.setBlockState(marker(plan), Blocks.DIAMOND_BLOCK.getDefaultState(), 2);
			report.setProperty("key", key);
			report.setProperty("terrain", terrainHash(world, allChunks(plan)));
			report.setProperty("plan", planHash(plan));
			try (FileOutputStream output = new FileOutputStream("mine-default.properties")) {
				report.store(output, "Normal Overworld mine fixture");
			}
		} else {
			try (FileInputStream input = new FileInputStream("mine-default.properties")) {
				report.load(input);
			}
			key = report.getProperty("key");
			plan = data.plan(key);
			require(plan != null && report.getProperty("plan").equals(planHash(plan)), "Normal-terrain plan moved after reload");
			require(report.getProperty("terrain").equals(terrainHash(world, allChunks(plan))), "Normal-terrain mine or old terrain changed");
			verify(world, plan, allChunks(plan), true);
		}

		verifyAccess(world, plan, true);

		report.setProperty("entrance", plan.entrance().toString());
		report.setProperty("landing", plan.landing().toString());
		report.setProperty("checked_cells", Integer.toString(plan.blocks().size()));
		try (FileOutputStream output = new FileOutputStream("mine-" + phase + "-pass.properties")) {
			report.store(output, "Packaged normal-Overworld mine qualification");
		}
	}

	static void verifyAccess(WorldServer world, AbandonedMinePlan plan, boolean hasMarker) {
		if (!plan.blocks().containsValue(MineBlock.LADDER_WEST)) {
			List<MineDecline.Step> steps = MineDecline.plan(plan.landing(), plan.entrance().y(), plan.identity()).steps();
			require(steps.get(steps.size() - 1).point().equals(plan.entrance()), "The access entrance moved");
			int loaded = world.getChunkProvider().getLoadedChunkCount();

			for (int direction : new int[] {1, -1}) {
				List<MineDecline.Step> route = new ArrayList<>(steps);
				if (direction < 0) Collections.reverse(route);
				MinePoint start = route.get(0).point();
				net.minecraft.entity.Entity walker = new EntityZombie(world);
				walker.stepHeight = 0.6F;
				walker.setPosition(start.x() + 0.5D, start.y(), start.z() + 0.5D);

				for (int index = 1; index < route.size(); index++) {
					MinePoint target = route.get(index).point();
					for (int tick = 0; tick < 12; tick++) {
						double dx = Math.max(-0.125D, Math.min(0.125D, target.x() + 0.5D - walker.posX));
						double dz = Math.max(-0.125D, Math.min(0.125D, target.z() + 0.5D - walker.posZ));
						walker.move(dx, -0.15D, dz);
					}

					require(Math.abs(walker.posX - target.x() - 0.5D) < 0.2D
							&& Math.abs(walker.posZ - target.z() - 0.5D) < 0.2D,
							"The walker could not pass the incline at " + target + ": " + walker.posX + "," + walker.posY + "," + walker.posZ);
					require(walker.posY >= target.y() - 0.05D && walker.posY <= target.y() + 1.05D,
							"Unsupported or obstructed stair at " + target);
				}
			}

			require(loaded == world.getChunkProvider().getLoadedChunkCount(), "Walking access loaded a neighbouring chunk");
			return;
		}

		MinePoint landing = plan.landing();
		for (int y = landing.y(); y <= plan.entrance().y(); y++) {
			BlockPos ladder = new BlockPos(landing.x() + 1, y, landing.z());
			require(world.getBlockState(ladder).getBlock() == Blocks.LADDER, "The shaft ladder has a gap");
			require(world.isSideSolid(ladder.east(), EnumFacing.WEST), "Unsupported shaft ladder");
			BlockPos access = ladder.west();
			if (!hasMarker || !access.equals(marker(plan))) {
				require(world.getBlockState(access).getBlock() == Blocks.AIR, "The shaft's access space is blocked");
			}
		}

		BlockPos top = new BlockPos(landing.x(), plan.entrance().y(), landing.z());
		require(world.canSeeSky(top), "The surface entrance is not open to the sky");
	}

	private static StratiformSeamBody distantBody(DepositDefinition definition) {
		for (int x = 8; x < 16; x++) {
			for (int z = 8; z < 16; z++) {
				java.util.Optional<StratiformSeamBody> body = RegionDepositPlanner.plan(
						definition, SEED, "minecraft:overworld", x, z);
				if (body.isPresent() && AbandonedMinePlanner.plan(body.get(),
						new AbandonedMineSettings(true, 1.0D), 129).isPresent()) return body.get();
			}
		}
		throw new IllegalStateException("The fixture has no suitable distant deposit");
	}

	static void populate(WorldServer world, int x, int z) {
		ChunkProviderServer provider = world.getChunkProvider();
		for (int dx = 0; dx <= 1; dx++) {
			for (int dz = 0; dz <= 1; dz++) provider.provideChunk(x + dx, z + dz);
		}
		provider.provideChunk(x, z).populateChunk(provider, provider.chunkGenerator);
	}

	static List<Long> allChunks(AbandonedMinePlan plan) {
		List<Long> chunks = new ArrayList<>();
		for (int x = Math.floorDiv(plan.bounds().minimumX(), 16); x <= Math.floorDiv(plan.bounds().maximumX(), 16); x++) {
			for (int z = Math.floorDiv(plan.bounds().minimumZ(), 16); z <= Math.floorDiv(plan.bounds().maximumZ(), 16); z++) {
				if (!plan.cellsInChunk(x, z).isEmpty()) chunks.add(MinePoint.chunkKey(x, z));
			}
		}
		return chunks;
	}

	static List<Long> populatedChunks(WorldServer world, AbandonedMinePlan plan) {
		List<Long> chunks = new ArrayList<>();
		for (long key : allChunks(plan)) {
			Chunk chunk = world.getChunkProvider().getLoadedChunk((int) (key >> 32), (int) key);
			if (chunk != null && chunk.isTerrainPopulated()) chunks.add(key);
		}
		return chunks;
	}

	static BlockPos marker(AbandonedMinePlan plan) {
		if (!plan.blocks().containsValue(MineBlock.LADDER_WEST)) {
			return new BlockPos(plan.landing().x() + 3, plan.landing().y() + 1, plan.landing().z() + 3);
		}
		return new BlockPos(plan.landing().x(), plan.landing().y() + 1, plan.landing().z());
	}

	static String terrainHash(WorldServer world, List<Long> chunks) throws Exception {
		MessageDigest digest = MessageDigest.getInstance("SHA-256");
		ByteBuffer value = ByteBuffer.allocate(4);
		for (long key : chunks) {
			Chunk chunk = world.getChunkProvider().loadChunk((int) (key >> 32), (int) key);
			require(chunk != null, "Expected saved old chunk");
			for (int x = 0; x < 16; x++) {
				for (int z = 0; z < 16; z++) {
					for (int y = 0; y < 256; y++) {
						value.clear();
						value.putInt(net.minecraft.block.Block.getStateId(chunk.getBlockState(x, y, z)));
						digest.update(value.array());
					}
				}
			}
		}
		return hex(digest.digest());
	}

	static String planHash(AbandonedMinePlan plan) throws Exception {
		require(plan != null, "Expected saved mine");
		MessageDigest digest = MessageDigest.getInstance("SHA-256");
		ByteBuffer value = ByteBuffer.allocate(12);
		for (Map.Entry<Long, MineBlock> cell : plan.blocks().entrySet()) {
			value.clear();
			value.putLong(cell.getKey()).putInt(cell.getValue().savedId());
			digest.update(value.array());
		}
		return hex(digest.digest());
	}

	static String join(List<Long> values) {
		StringBuilder text = new StringBuilder();
		for (long value : values) {
			if (text.length() > 0) text.append(',');
			text.append(value);
		}
		return text.toString();
	}

	static List<Long> split(String text) {
		List<Long> values = new ArrayList<>();
		for (String value : text.split(",")) values.add(Long.parseLong(value));
		return values;
	}

	private static String hex(byte[] bytes) {
		StringBuilder value = new StringBuilder();
		for (byte part : bytes) value.append(String.format("%02x", part & 255));
		return value.toString();
	}

	static IBlockState expected(MineBlock block) {
		switch (block) {
			case AIR: return Blocks.AIR.getDefaultState();
			case PLANKS: return Blocks.PLANKS.getDefaultState();
			case FENCE: return Blocks.OAK_FENCE.getDefaultState();
			case COBWEB: return Blocks.WEB.getDefaultState();
			case COBBLESTONE: return Blocks.COBBLESTONE.getDefaultState();
			case STAIRS_NORTH: return Blocks.OAK_STAIRS.getDefaultState().withProperty(BlockStairs.FACING, EnumFacing.NORTH);
			case STAIRS_SOUTH: return Blocks.OAK_STAIRS.getDefaultState().withProperty(BlockStairs.FACING, EnumFacing.SOUTH);
			case STAIRS_EAST: return Blocks.OAK_STAIRS.getDefaultState().withProperty(BlockStairs.FACING, EnumFacing.EAST);
			case STAIRS_WEST: return Blocks.OAK_STAIRS.getDefaultState().withProperty(BlockStairs.FACING, EnumFacing.WEST);
			case LADDER_WEST: return Blocks.LADDER.getDefaultState().withProperty(BlockLadder.FACING, EnumFacing.WEST);
			case RAIL_EAST_WEST: return Blocks.RAIL.getDefaultState().withProperty(BlockRail.SHAPE, BlockRailBase.EnumRailDirection.EAST_WEST);
			case RAIL_NORTH_SOUTH: return Blocks.RAIL.getDefaultState().withProperty(BlockRail.SHAPE, BlockRailBase.EnumRailDirection.NORTH_SOUTH);
			default: throw new IllegalArgumentException("Unknown test palette");
		}
	}

	static void require(boolean condition, String message) {
		if (!condition) throw new IllegalStateException(message);
	}
}
