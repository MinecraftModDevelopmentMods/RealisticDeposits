package zone.moddev.mc.realisticdeposits.generation;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Random;
import java.util.Set;
import java.util.WeakHashMap;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import net.minecraft.block.Block;
import net.minecraft.block.BlockLadder;
import net.minecraft.block.BlockRail;
import net.minecraft.block.BlockRailBase;
import net.minecraft.block.BlockStairs;
import net.minecraft.block.state.IBlockState;
import net.minecraft.init.Blocks;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraft.world.WorldServer;
import net.minecraft.world.biome.Biome;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.chunk.IChunkGenerator;
import net.minecraft.world.chunk.IChunkProvider;
import net.minecraft.world.chunk.storage.ExtendedBlockStorage;
import net.minecraft.world.gen.structure.MapGenStructureData;
import net.minecraftforge.common.BiomeDictionary;
import net.minecraftforge.event.world.WorldEvent;
import net.minecraftforge.event.world.ChunkDataEvent;
import net.minecraftforge.fml.common.IWorldGenerator;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;

import zone.moddev.mc.orespawn.api.OreGenerationContext;
import zone.moddev.mc.orespawn.api.OreSpawnApi;
import zone.moddev.mc.realisticdeposits.geometry.AbandonedMinePlan;
import zone.moddev.mc.realisticdeposits.geometry.AbandonedMinePlanner;
import zone.moddev.mc.realisticdeposits.geometry.BlockBounds;
import zone.moddev.mc.realisticdeposits.geometry.MineBlock;
import zone.moddev.mc.realisticdeposits.geometry.MinePoint;
import zone.moddev.mc.realisticdeposits.geometry.MineableDepositBody;
import zone.moddev.mc.realisticdeposits.model.DepositDefinition;

/** Builds abandoned workings after OreSpawn, without owning or replacing its ore pass. */
public final class MineWorkingsGenerator implements IWorldGenerator {
	public static final MineWorkingsGenerator INSTANCE = new MineWorkingsGenerator();
	private static final Logger LOG = LogManager.getLogger("Realistic Deposits");
	private static final Map<World, Session> SESSIONS = new WeakHashMap<>();
	private static final Map<String, List<Candidate>> PENDING = new LinkedHashMap<>();
	private static final String[] STRUCTURES = {"Mineshaft", "Village", "Stronghold", "Temple", "Monument"};

	private MineWorkingsGenerator() { }

	/** Called only for definitions accepted by the current world's OreSpawn profile. */
	static void offer(OreGenerationContext context, DepositDefinition definition, MineableDepositBody body) {
		if (!"minecraft:overworld".equals(context.dimension().toString())) return;
		if (!AbandonedMinePlanner.selected(body, definition.abandonedMine())) return;

		MinePoint landing = AbandonedMinePlanner.landing(body);
		if (Math.abs((long) (context.chunkX() << 4) - landing.x()) > 96L
				|| Math.abs((long) (context.chunkZ() << 4) - landing.z()) > 96L) return;

		String chunk = queueKey(context.worldSeed(), context.dimension().toString(), context.chunkX(), context.chunkZ());
		List<Candidate> candidates = PENDING.computeIfAbsent(chunk, ignored -> new ArrayList<>());
		String key = body.identity().toString();
		if (candidates.stream().noneMatch(candidate -> candidate.key.equals(key))) {
			candidates.add(new Candidate(definition, body));
		}

		// A third-party generator may omit Forge's final population pass. Do not
		// retain those unused proposals forever or let them leak into another save.
		if (PENDING.size() > 256) PENDING.remove(PENDING.keySet().iterator().next());
	}

	@SubscribeEvent
	public void worldLoaded(WorldEvent.Load event) {
		if (event.getWorld() instanceof WorldServer && event.getWorld().provider.getDimension() == 0) {
			SESSIONS.computeIfAbsent(event.getWorld(), ignored -> new Session((WorldServer) event.getWorld()));
		}
	}

	@SubscribeEvent
	public void worldUnloaded(WorldEvent.Unload event) {
		SESSIONS.remove(event.getWorld());
		String prefix = event.getWorld().getSeed() + "/";
		PENDING.keySet().removeIf(key -> key.startsWith(prefix));
	}

	@SubscribeEvent
	public void chunkLoaded(ChunkDataEvent.Load event) {
		if (!(event.getWorld() instanceof WorldServer) || event.getWorld().provider.getDimension() != 0) return;

		Session session = SESSIONS.computeIfAbsent(event.getWorld(), ignored -> new Session((WorldServer) event.getWorld()));
		NBTTagCompound level = event.getData().getCompoundTag("Level");
		if (level.getBoolean("TerrainPopulated")) {
			session.loadedPopulated.add(MinePoint.chunkKey(event.getChunk().xPosition, event.getChunk().zPosition));
		}
	}

	@Override
	public void generate(Random ignored, int chunkX, int chunkZ, World baseWorld,
			IChunkGenerator generator, IChunkProvider provider) {
		String dimension = baseWorld.provider.getDimension() == 0 ? "minecraft:overworld"
				: Integer.toString(baseWorld.provider.getDimension());
		List<Candidate> candidates = PENDING.remove(queueKey(baseWorld.getSeed(), dimension, chunkX, chunkZ));
		if (!(baseWorld instanceof WorldServer) || baseWorld.provider.getDimension() != 0) return;

		WorldServer world = (WorldServer) baseWorld;
		Session session = SESSIONS.get(world);
		if (session == null) {
			// Spawn chunks can populate before Forge posts WorldEvent.Load.
			// Take the disk snapshot on that first pass and keep it for the session.
			session = new Session(world);
			SESSIONS.put(world, session);
		}

		long chunkKey = MinePoint.chunkKey(chunkX, chunkZ);
		boolean alreadyVisited = !session.visited.add(chunkKey);
		if (session.existing == null || alreadyVisited) return;
		boolean pendingSection = session.data != null && !session.data.unbuiltMinesInChunk(chunkX, chunkZ).isEmpty();
		if (session.existing.contains(chunkX, chunkZ)
				&& (!pendingSection || session.loadedPopulated.contains(chunkKey))) return;
		if (candidates == null && session.data == null) return;

		Chunk chunk = world.getChunkProvider().getLoadedChunk(chunkX, chunkZ);
		if (chunk == null) throw new IllegalStateException("The mine pass must only use its current loaded chunk");
		try {
			if (session.data == null) session.data = MineWorldData.get(world);
			MineWorldData data = session.data;
			if (candidates != null) {
				for (Candidate candidate : candidates) {
					if (!data.decided(candidate.key)) decide(candidate, data, session, world, generator, chunkKey);
				}
			}

			// Saved pieces finish even if live seam geometry has since changed.
			for (String key : data.unbuiltMinesInChunk(chunkX, chunkZ)) {
				place(data.plan(key), data.definition(key), chunk, world, session);
				data.sectionHandled(key, chunkX, chunkZ);
			}
		} catch (RuntimeException failure) {
			if (!session.warned) {
				LOG.warn("Abandoned workings were skipped: {}. No old chunks were excavated.", failure.getMessage());
				session.warned = true;
			}
		}
	}

	private static void decide(Candidate candidate, MineWorldData data, Session session,
			WorldServer world, IChunkGenerator generator, long currentChunk) {
		MineTerrainSampler terrain = new MineTerrainSampler(world, generator);
		Optional<AbandonedMinePlan> proposed = AbandonedMinePlanner.plan(candidate.body,
				candidate.definition.abandonedMine(), terrain::naturalSurfaceY);
		if (!proposed.isPresent()) {
			data.reject(candidate.key, "No safe surface access");
			return;
		}

		AbandonedMinePlan plan = proposed.get();
		if (session.existing.intersects(plan.bounds()) || crossesVisited(plan, session, currentChunk)) {
			data.reject(candidate.key, "Existing terrain");
		} else if (!world.getWorldBorder().contains(new BlockPos(plan.bounds().minimumX(), 64, plan.bounds().minimumZ()))
				|| !world.getWorldBorder().contains(new BlockPos(plan.bounds().maximumX(), 64, plan.bounds().maximumZ()))) {
			data.reject(candidate.key, "World border");
		} else if (terrain.surfaceY(plan.entrance()) != plan.entrance().y()
				|| !terrain.dry(plan) || !terrain.covered(plan) || !biomesAllowed(plan, candidate.definition, world)) {
			data.reject(candidate.key, "Water, insufficient cover or an excluded biome");
		} else if (intersectsStructure(plan.bounds(), world)) {
			data.reject(candidate.key, "Another structure");
		} else if (data.plans().values().stream().anyMatch(other -> overlaps(plan.bounds(), other.bounds()))) {
			data.reject(candidate.key, "Another abandoned mine");
		} else {
			data.accept(candidate.key, plan, candidate.definition);
			LOG.info("Planned abandoned workings for '{}': surface={}, seam={}, blocks={}",
					candidate.key, plan.entrance(), plan.landing(), plan.blocks().size());
		}
	}

	private static boolean crossesVisited(AbandonedMinePlan plan, Session session, long current) {
		BlockBounds bounds = plan.bounds();
		for (int x = Math.floorDiv(bounds.minimumX(), 16); x <= Math.floorDiv(bounds.maximumX(), 16); x++) {
			for (int z = Math.floorDiv(bounds.minimumZ(), 16); z <= Math.floorDiv(bounds.maximumZ(), 16); z++) {
				long key = MinePoint.chunkKey(x, z);
				if (key != current && session.visited.contains(key)) return true;
			}
		}

		return false;
	}

	static boolean biomesAllowed(AbandonedMinePlan plan, DepositDefinition definition, WorldServer world) {
		Biome entrance = world.getBiomeProvider().getBiome(
				new BlockPos(plan.entrance().x(), 0, plan.entrance().z()));
		// The first version needs open ground for its surface frame. Do not
		// clear tree trunks or guess which wooden blocks belong to a structure.
		if (BiomeDictionary.isBiomeOfType(entrance, BiomeDictionary.Type.FOREST)
				|| BiomeDictionary.isBiomeOfType(entrance, BiomeDictionary.Type.CONIFEROUS)
				|| BiomeDictionary.isBiomeOfType(entrance, BiomeDictionary.Type.JUNGLE)
				|| BiomeDictionary.isBiomeOfType(entrance, BiomeDictionary.Type.SWAMP)) return false;

		Set<Long> columns = new HashSet<>();
		Map<Biome, Boolean> checked = new HashMap<>();
		for (long position : plan.blocks().keySet()) {
			MinePoint point = MinePoint.unpack(position);
			if (!columns.add(MinePoint.chunkKey(point.x(), point.z()))) continue;
			Biome biome = world.getBiomeProvider().getBiome(new BlockPos(point.x(), 0, point.z()));
			if (!checked.computeIfAbsent(biome, value -> biomeAllowed(value, definition))) return false;
		}

		return true;
	}

	private static boolean biomeAllowed(Biome biome, DepositDefinition definition) {
		if (BiomeDictionary.isBiomeOfType(biome, BiomeDictionary.Type.OCEAN)
				|| BiomeDictionary.isBiomeOfType(biome, BiomeDictionary.Type.RIVER)) return false;
		String id = Biome.REGISTRY.getNameForObject(biome).toString();
		if ((!definition.biomeIds().isEmpty() && !definition.biomeIds().contains(id))
				|| definition.excludedBiomeIds().contains(id)) return false;

		Set<String> types = new HashSet<>();
		for (BiomeDictionary.Type type : BiomeDictionary.getTypesForBiome(biome)) types.add(type.name());
		return (definition.biomeDictionary().isEmpty() || !Collections.disjoint(types, definition.biomeDictionary()))
				&& Collections.disjoint(types, definition.excludedBiomeDictionary());
	}

	private static boolean overlaps(BlockBounds left, BlockBounds right) {
		return left.minimumX() <= right.maximumX() && left.maximumX() >= right.minimumX()
				&& left.minimumY() <= right.maximumY() && left.maximumY() >= right.minimumY()
				&& left.minimumZ() <= right.maximumZ() && left.maximumZ() >= right.minimumZ();
	}

	private static boolean intersectsStructure(BlockBounds bounds, WorldServer world) {
		for (String name : STRUCTURES) {
			MapGenStructureData structures = (MapGenStructureData) world.getMapStorage()
					.getOrLoadData(MapGenStructureData.class, name);
			if (structures == null) continue;

			NBTTagCompound entries = structures.getTagCompound();
			for (String key : entries.getKeySet()) {
				int[] box = entries.getCompoundTag(key).getIntArray("BB");
				if (box.length == 6 && box[0] <= bounds.maximumX() + 2 && box[3] >= bounds.minimumX() - 2
						&& box[1] <= bounds.maximumY() + 2 && box[4] >= bounds.minimumY() - 2
						&& box[2] <= bounds.maximumZ() + 2 && box[5] >= bounds.minimumZ() - 2) return true;
			}
		}

		return false;
	}

	private static void place(AbandonedMinePlan plan, DepositDefinition definition,
			Chunk chunk, WorldServer world, Session session) {
		List<AbandonedMinePlan.Cell> cells = plan.cellsInChunk(chunk.xPosition, chunk.zPosition);
		if (cells.isEmpty()) return;
		String hostKey = definition == null ? "vanilla" : definition.id();
		Set<Block> hosts = session.hosts.computeIfAbsent(hostKey, ignored -> naturalHosts(definition, world));
		BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();

		for (AbandonedMinePlan.Cell cell : cells) {
			MinePoint point = cell.point();
			cursor.setPos(point.x(), point.y(), point.z());
			IBlockState current = chunk.getBlockState(cursor);
			if (current.getMaterial().isLiquid() || current.getBlock().hasTileEntity(current)
					|| current.getBlock() != Blocks.AIR && !hosts.contains(current.getBlock())) {
				LOG.info("Left abandoned workings sealed in chunk {},{}: {} at {} is not safe to replace.",
						chunk.xPosition, chunk.zPosition, current, point);
				return;
			}

			// A newly generated lake or another mod's structure is allowed to block
			// this section. Never open a hole into it or remove somebody else's work.
			if (cell.block() == MineBlock.AIR) {
				for (int dx = -1; dx <= 1; dx++) {
					for (int dz = -1; dz <= 1; dz++) {
						int x = point.x() + dx;
						int z = point.z() + dz;
						if ((x >> 4) != chunk.xPosition || (z >> 4) != chunk.zPosition) continue;
						for (int dy = -1; dy <= 1; dy++) {
							if (chunk.getBlockState(x & 15, point.y() + dy, z & 15).getMaterial().isLiquid()) {
								LOG.info("Left abandoned workings sealed in chunk {},{} to avoid opening a liquid at {},{},{}.",
										chunk.xPosition, chunk.zPosition, x, point.y() + dy, z);
								return;
							}
						}
					}
				}
			}
		}

		ExtendedBlockStorage[] sections = chunk.getBlockStorageArray();
		for (AbandonedMinePlan.Cell cell : cells) {
			MinePoint point = cell.point();
			int section = point.y() >> 4;
			if (sections[section] == null) {
				if (cell.block() == MineBlock.AIR) continue;
				sections[section] = new ExtendedBlockStorage(section << 4, !world.provider.hasNoSky());
			}

			// Direct generation writes avoid drops, rail physics and neighbour loads.
			// Lighting and the chunk's save flag are updated once after the slice.
			sections[section].set(point.x() & 15, point.y() & 15, point.z() & 15, state(cell.block()));
		}

		chunk.generateSkylightMap();
		chunk.setChunkModified();
		chunk.resetRelightChecks();
	}

	private static Set<Block> naturalHosts(DepositDefinition definition, WorldServer world) {
		Set<Block> blocks = new HashSet<>();
		Collections.addAll(blocks, Blocks.STONE, Blocks.DIRT, Blocks.GRASS, Blocks.GRAVEL, Blocks.SAND,
				Blocks.SANDSTONE, Blocks.RED_SANDSTONE, Blocks.HARDENED_CLAY, Blocks.STAINED_HARDENED_CLAY,
				Blocks.MYCELIUM, Blocks.SNOW, Blocks.SNOW_LAYER, Blocks.COAL_ORE, Blocks.IRON_ORE,
				Blocks.GOLD_ORE, Blocks.DIAMOND_ORE, Blocks.LAPIS_ORE, Blocks.REDSTONE_ORE,
				Blocks.LIT_REDSTONE_ORE, Blocks.EMERALD_ORE, Blocks.TALLGRASS, Blocks.DOUBLE_PLANT,
				Blocks.DEADBUSH, Blocks.RED_FLOWER, Blocks.YELLOW_FLOWER, Blocks.BROWN_MUSHROOM, Blocks.RED_MUSHROOM);
		if (definition != null) {
			for (String id : definition.hosts()) addHost(blocks, new ResourceLocation(id));
			addHost(blocks, new ResourceLocation(definition.resource()));
		}
		OreSpawnApi.getActiveProfile(world.getMinecraftServer()).ifPresent(profile -> {
			for (ResourceLocation rock : naturalRockBlocks(profile.toJson())) addHost(blocks, rock);
		});

		// These may be natural-looking, but they also make up villages and ruins.
		blocks.remove(Blocks.COBBLESTONE);
		blocks.remove(Blocks.PLANKS);
		blocks.remove(Blocks.LOG);
		blocks.remove(Blocks.LOG2);
		blocks.remove(Blocks.BEDROCK);
		return blocks;
	}

	static Set<ResourceLocation> naturalRockBlocks(JsonObject profile) {
		Set<ResourceLocation> blocks = new LinkedHashSet<>();
		if (!profile.has("rocks") || !profile.get("rocks").isJsonObject()) return blocks;

		// Rock IDs name geology definitions, not registered blocks. Read the
		// declared block instead, just as OreSpawn does when building its strata.
		for (Map.Entry<String, JsonElement> entry : profile.getAsJsonObject("rocks").entrySet()) {
			if (!entry.getValue().isJsonObject()) continue;
			JsonObject rock = entry.getValue().getAsJsonObject();
			if (rock.has("enabled") && !rock.get("enabled").getAsBoolean()) continue;
			if (!rock.has("block") || !rock.get("block").isJsonPrimitive()
					|| !rock.getAsJsonPrimitive("block").isString()) continue;

			blocks.add(new ResourceLocation(rock.get("block").getAsString()));
		}

		return blocks;
	}

	private static void addHost(Set<Block> hosts, ResourceLocation id) {
		if (Block.REGISTRY.containsKey(id)) hosts.add(Block.REGISTRY.getObject(id));
	}

	private static IBlockState state(MineBlock block) {
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
			case RAIL_EAST_WEST: return Blocks.RAIL.getDefaultState().withProperty(
					BlockRail.SHAPE, BlockRailBase.EnumRailDirection.EAST_WEST);
			case RAIL_NORTH_SOUTH: return Blocks.RAIL.getDefaultState().withProperty(
					BlockRail.SHAPE, BlockRailBase.EnumRailDirection.NORTH_SOUTH);
			default: throw new IllegalArgumentException("Unknown mine palette entry " + block);
		}
	}

	private static String queueKey(long seed, String dimension, int chunkX, int chunkZ) {
		return seed + "/" + dimension + "/" + chunkX + "," + chunkZ;
	}

	private static final class Candidate {
		private final String key;
		private final DepositDefinition definition;
		private final MineableDepositBody body;

		private Candidate(DepositDefinition definition, MineableDepositBody body) {
			key = body.identity().toString();
			this.definition = definition;
			this.body = body;
		}
	}

	private static final class Session {
		private ExistingMineTerrain existing;
		private final Set<Long> visited = new HashSet<>();
		private final Set<Long> loadedPopulated = new HashSet<>();
		private final Map<String, Set<Block>> hosts = new HashMap<>();
		private boolean warned;
		private MineWorldData data;

		private Session(WorldServer world) {
			try {
				existing = ExistingMineTerrain.read(new File(world.getSaveHandler().getWorldDirectory(), "region"));
				if (world.getSaveHandler().getMapFileFromName(MineWorldData.NAME).isFile()) {
					data = MineWorldData.get(world);
				}
			} catch (IOException failure) {
				LOG.warn("Abandoned mines are disabled for this session because old chunks could not be inspected: {}",
						failure.getMessage());
			} catch (RuntimeException failure) {
				existing = null;
				LOG.warn("Abandoned mines are disabled because their saved plans could not be read: {}", failure.getMessage());
			}
		}
	}
}
