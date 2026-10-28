package zone.moddev.mc.realisticdeposits.generation;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.security.MessageDigest;
import java.util.Optional;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import net.minecraft.block.Block;
import net.minecraft.init.Blocks;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.WorldServer;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.gen.ChunkProviderServer;
import net.minecraftforge.fml.common.FMLCommonHandler;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.event.FMLServerStartedEvent;

import zone.moddev.mc.orespawn.api.GeologySampler;
import zone.moddev.mc.orespawn.api.OreSpawnApi;
import zone.moddev.mc.realisticdeposits.config.RealisticDepositsRuntime;
import zone.moddev.mc.realisticdeposits.geometry.CoalMeasuresBody;
import zone.moddev.mc.realisticdeposits.geometry.AbandonedMinePlan;
import zone.moddev.mc.realisticdeposits.geometry.AbandonedMinePlanner;
import zone.moddev.mc.realisticdeposits.model.DepositDefinition;

/** Packaged coal fixtures only; the ordinary mod never includes this class. */
@Mod(modid = "rdcoalprobe", name = "Coalfield Qualification", version = "1",
		dependencies = "required-after:realisticdeposits")
public final class CoalRuntimeProbe {
	private static final int SIDE = 8;

	@Mod.EventHandler
	public void started(FMLServerStartedEvent event) throws Exception {
		if (!Boolean.getBoolean("rd.coal.enabled")) return;
		MinecraftServer server = FMLCommonHandler.instance().getMinecraftServerInstance();

		try {
			WorldServer world = server.worldServerForDimension(0);
			if (!"coal-test-world".equals(world.getSaveHandler().getWorldDirectory().getName())) {
				throw new IllegalStateException("The coal probe only runs in its disposable world");
			}
			world.getGameRules().setOrCreateGameRule("randomTickSpeed", "0");
			DepositDefinition definition = RealisticDepositsRuntime.state().definitions().stream()
					.filter(DepositDefinition::isCoal).findFirst().get();
			String phase = System.getProperty("rd.coal.phase", "first");
			GeologySampler sampler = OreSpawnApi.createSampler(world).get();
			CoalGeology.Snapshot geology = CoalGeology.active();
			if (Boolean.getBoolean("rd.coal.survey")) {
				survey(world, definition, sampler, geology);
				System.out.println("RD_COAL_PASS survey");
				return;
			}
			File target = new File("coal-target.json");
			JsonObject coordinates;

			if (target.isFile()) {
				try (FileReader reader = new FileReader(target)) {
					coordinates = new JsonParser().parse(reader).getAsJsonObject();
				}
			} else {
				coordinates = new JsonObject();
				find(world, definition, sampler, geology, 32, "first", coordinates);
				find(world, definition, sampler, geology, 64, "later", coordinates);
				write(target, coordinates);
			}

			CoalMeasuresBody first = resolved(world, definition, sampler, geology, coordinates, "first");
			CoalMeasuresBody later = resolved(world, definition, sampler, geology, coordinates, "later");
			if (("first".equals(phase) || "later".equals(phase)) && Boolean.getBoolean("rd.coal.structure")) {
				CoalMeasuresBody protectedBody = "first".equals(phase) ? first : later;
				net.minecraft.world.gen.structure.MapGenStructureData structure =
						(net.minecraft.world.gen.structure.MapGenStructureData) world.getMapStorage().getOrLoadData(
								net.minecraft.world.gen.structure.MapGenStructureData.class, "Mineshaft");
				if (structure == null) structure = new net.minecraft.world.gen.structure.MapGenStructureData("Mineshaft");
				net.minecraft.nbt.NBTTagCompound existing = new net.minecraft.nbt.NBTTagCompound();
				int x = (protectedBody.centerX() >> 4) << 4;
				int z = (protectedBody.centerZ() >> 4) << 4;
				existing.setIntArray("BB", new int[] {x, 0, z, x + 15, 255, z + 15});
				structure.writeInstance(existing, protectedBody.centerX() >> 4, protectedBody.centerZ() >> 4);
				world.getMapStorage().setData("Mineshaft", structure);
			}
			if ("first".equals(phase)) populate(world, first);
			if ("later".equals(phase)) populate(world, later);

			JsonObject report = new JsonObject();
			report.addProperty("phase", phase);
			report.addProperty("physical_strata", geology.physicalStrata);
			report.addProperty("legacy_coal_layers", geology.legacyCoal);
			report.add("profile", OreSpawnApi.getActiveProfile(server).get().toJson());
			report.add("first", sample(world, first));
			if (Boolean.getBoolean("rd.coal.workings")) {
				AbandonedMinePlan mine = MineWorldData.get(world).plan(first.identity().toString());
				if (mine == null) throw new IllegalStateException("The selected coal workings were not built");
				report.addProperty("mine_entrance", mine.entrance().toString());
				report.addProperty("mine_landing", mine.landing().toString());
			}
			if ("later".equals(phase) || "stable".equals(phase)) report.add("later", sample(world, later));
			write(new File("coal-" + phase + ".json"), report);
			System.out.println("RD_COAL_PASS " + phase);
		} finally {
			server.initiateShutdown();
		}
	}

	private static void find(WorldServer world, DepositDefinition definition, GeologySampler sampler,
			CoalGeology.Snapshot geology, int start, String name, JsonObject coordinates) {
		boolean workings = Boolean.getBoolean("rd.coal.workings");
		int range = workings ? 96 : 24;
		int[] rejected = new int[6];
		for (int rx = start; rx < start + range; rx++) {
			for (int rz = start; rz < start + range; rz++) {
				Optional<CoalMeasuresBody> planned = CoalMeasuresBody.plan(definition,
						world.getSeed(), "minecraft:overworld", rx, rz);
				if (!planned.isPresent()) continue;
				CoalMeasuresBody body = planned.get();
				if (!CoalGeology.acceptsGeome(definition, body, sampler)) continue;
				Optional<CoalMeasuresBody> fitted = CoalGeology.align(body, definition, sampler,
						CoalGeology.hosts(definition), geology);
				if (!fitted.isPresent()) continue;
				rejected[0]++;
				if (workings) {
					MineTerrainSampler terrain = new MineTerrainSampler(world, world.getChunkProvider().chunkGenerator);
					Optional<AbandonedMinePlan> mine = AbandonedMinePlanner.plan(fitted.get(), definition.abandonedMine(),
							terrain.surfaceY(AbandonedMinePlanner.landing(fitted.get())));
					if (!mine.isPresent()) { rejected[1]++; continue; }
					if (!terrain.dry(mine.get())) { rejected[2]++; continue; }
					if (!terrain.covered(mine.get())) { rejected[3]++; continue; }
					if (!MineWorkingsGenerator.biomesAllowed(mine.get(), definition, world)) { rejected[4]++; continue; }
				}

				coordinates.addProperty(name + "_rx", rx);
				coordinates.addProperty(name + "_rz", rz);
				return;
			}
		}
		throw new IllegalStateException("No suitable coalfield in the bounded fixture search: "
				+ java.util.Arrays.toString(rejected));
	}

	private static CoalMeasuresBody resolved(WorldServer world, DepositDefinition definition,
			GeologySampler sampler, CoalGeology.Snapshot geology, JsonObject target, String name) {
		CoalMeasuresBody body = CoalMeasuresBody.plan(definition, world.getSeed(), "minecraft:overworld",
				target.get(name + "_rx").getAsInt(), target.get(name + "_rz").getAsInt()).get();
		return CoalGeology.align(body, definition, sampler, CoalGeology.hosts(definition), geology).get();
	}

	private static void populate(WorldServer world, CoalMeasuresBody body) {
		ChunkProviderServer provider = world.getChunkProvider();
		int baseX = (body.centerX() >> 4) - SIDE / 2;
		int baseZ = (body.centerZ() >> 4) - SIDE / 2;
		for (int x = baseX - 2; x <= baseX + SIDE + 1; x++) {
			for (int z = baseZ - 2; z <= baseZ + SIDE + 1; z++) provider.provideChunk(x, z);
		}
		for (int x = baseX - 1; x <= baseX + SIDE; x++) {
			for (int z = baseZ - 1; z <= baseZ + SIDE; z++) {
				Chunk chunk = provider.provideChunk(x, z);
				chunk.populateChunk(provider, provider.chunkGenerator);
			}
		}
	}

	private static JsonObject sample(WorldServer world, CoalMeasuresBody body) throws Exception {
		MessageDigest terrain = MessageDigest.getInstance("SHA-256");
		MessageDigest coal = MessageDigest.getInstance("SHA-256");
		int baseX = (body.centerX() >> 4) - SIDE / 2;
		int baseZ = (body.centerZ() >> 4) - SIDE / 2;
		int inBody = 0;
		int outside = 0;
		int planned = 0;
		int protectedCoal = 0;
		JsonArray section = new JsonArray();
		BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();

		for (int cx = baseX; cx < baseX + SIDE; cx++) {
			for (int cz = baseZ; cz < baseZ + SIDE; cz++) {
				Chunk chunk = world.getChunkProvider().provideChunk(cx, cz);
				if (!chunk.isTerrainPopulated()) throw new IllegalStateException("Fixture sampled unopened terrain");
				for (int x = cx * 16; x < cx * 16 + 16; x++) {
					for (int z = cz * 16; z < cz * 16 + 16; z++) {
						for (int y = 0; y < 256; y++) {
							net.minecraft.block.state.IBlockState state = chunk.getBlockState(cursor.setPos(x, y, z));
							update(terrain, Block.getStateId(state));
							boolean expected = body.isOreAt(x, y, z);
							if (expected) planned++;
							if (state.getBlock() == Blocks.COAL_ORE) {
								if ((x >> 4) == (body.centerX() >> 4) && (z >> 4) == (body.centerZ() >> 4)) protectedCoal++;
								if (expected) inBody++; else outside++;
								update(coal, x);
								update(coal, y);
								update(coal, z);
							}
							if (z == body.centerZ() && y <= 140) {
								JsonArray cell = new JsonArray();
								cell.add(new com.google.gson.JsonPrimitive(x));
								cell.add(new com.google.gson.JsonPrimitive(y));
								cell.add(new com.google.gson.JsonPrimitive(state.getBlock().getRegistryName().toString()));
								section.add(cell);
							}
						}
					}
				}
			}
		}
		if (inBody < 50) throw new IllegalStateException("The selected fixture has no substantial coal bed: " + inBody);
		if (Boolean.getBoolean("rd.coal.structure") && protectedCoal != 0) {
			throw new IllegalStateException("Coal entered the protected native structure: " + protectedCoal);
		}
		JsonObject result = new JsonObject();
		result.addProperty("centre_x", body.centerX());
		result.addProperty("centre_y", body.centerY());
		result.addProperty("centre_z", body.centerZ());
		result.addProperty("beds", body.bedCount());
		result.addProperty("coal_in_body", inBody);
		result.addProperty("coal_outside_body", outside);
		result.addProperty("planned_positions", planned);
		result.addProperty("chunks", SIDE * SIDE);
		result.addProperty("terrain_sha256", hex(terrain.digest()));
		result.addProperty("coal_sha256", hex(coal.digest()));
		result.add("section", section);
		return result;
	}

	private static void update(MessageDigest digest, int value) {
		digest.update((byte) (value >>> 24));
		digest.update((byte) (value >>> 16));
		digest.update((byte) (value >>> 8));
		digest.update((byte) value);
	}

	private static void survey(WorldServer world, DepositDefinition definition,
			GeologySampler sampler, CoalGeology.Snapshot geology) throws Exception {
		JsonObject enabled = definition.toJson();
		enabled.addProperty("enabled", true);
		DepositDefinition geometry = zone.moddev.mc.realisticdeposits.config.DepositCatalogLoader
				.loadDefinition(enabled, "coal reserve survey");
		int candidates = 0;
		int accepted = 0;
		long reserves = 0;
		JsonArray fields = new JsonArray();

		// An evenly spaced survey, not a search centred on a known rich deposit.
		for (int rx = 32; rx < 48; rx++) {
			for (int rz = 32; rz < 48; rz++) {
				Optional<CoalMeasuresBody> body = CoalMeasuresBody.plan(geometry, world.getSeed(),
						"minecraft:overworld", rx, rz);
				if (!body.isPresent()) continue;
				candidates++;
				if (!CoalGeology.acceptsGeome(geometry, body.get(), sampler)) continue;
				body = CoalGeology.align(body.get(), geometry, sampler, CoalGeology.hosts(geometry), geology);
				if (!body.isPresent()) continue;
				accepted++;

				CoalMeasuresBody field = body.get();
				long sampled = 0;
				for (int x = field.bounds().minimumX(); x <= field.bounds().maximumX(); x += 8) {
					for (int z = field.bounds().minimumZ(); z <= field.bounds().maximumZ(); z += 8) {
						for (int y = field.minimumCandidateYAt(x, z); y <= field.maximumCandidateYAt(x, z); y++) {
							if (field.isOreAt(x, y, z)) sampled++;
						}
					}
				}
				long estimate = sampled * 64;
				reserves += estimate;
				fields.add(new com.google.gson.JsonPrimitive(estimate));
			}
		}

		long coal = 0;
		int discoveries = 0;
		int chunks = 0;
		BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
		for (int rx = 32; rx < 41; rx++) {
			for (int rz = 32; rz < 41; rz++) {
				java.util.Random location = new java.util.Random(world.getSeed() ^ ((long) rx << 32) ^ rz);
				int cx = rx * 32 + location.nextInt(32);
				int cz = rz * 32 + location.nextInt(32);
				ChunkProviderServer provider = world.getChunkProvider();
				for (int dx = -1; dx <= 2; dx++) {
					for (int dz = -1; dz <= 2; dz++) provider.provideChunk(cx + dx, cz + dz);
				}
				provider.provideChunk(cx, cz).populateChunk(provider, provider.chunkGenerator);
				Chunk chunk = provider.provideChunk(cx, cz);
				int count = 0;
				for (int x = cx * 16; x < cx * 16 + 16; x++) {
					for (int z = cz * 16; z < cz * 16 + 16; z++) {
						for (int y = 0; y < 256; y++) {
							if (chunk.getBlockState(cursor.setPos(x, y, z)).getBlock() == Blocks.COAL_ORE) count++;
						}
					}
				}
				coal += count;
				if (count >= 400) discoveries++;
				chunks++;
			}
		}

		int fortune = 0;
		java.util.Random random = new java.util.Random(17);
		for (int trial = 0; trial < 10000; trial++) fortune += Blocks.COAL_ORE.quantityDroppedWithBonus(3, random);
		JsonObject report = new JsonObject();
		report.addProperty("coalfields_enabled", definition.enabled());
		report.addProperty("regions", 256);
		report.addProperty("selected_before_geology", candidates);
		report.addProperty("accepted_fields", accepted);
		report.add("estimated_field_reserves", fields);
		report.addProperty("estimated_field_coal_per_chunk_before_host_and_terrain_rejection", reserves / (256.0 * 1024));
		report.addProperty("sample_chunks", chunks);
		report.addProperty("actual_sample_coal", coal);
		report.addProperty("actual_coal_per_sample_chunk", coal / (double) chunks);
		report.addProperty("sample_chunks_with_at_least_400_coal", discoveries);
		report.addProperty("fortune_iii_mean_per_ore", fortune / 10000.0);
		write(new File("coal-survey.json"), report);
	}

	private static String hex(byte[] bytes) {
		StringBuilder text = new StringBuilder();
		for (byte value : bytes) text.append(String.format("%02x", value & 255));
		return text.toString();
	}

	private static void write(File path, JsonObject json) throws Exception {
		try (FileWriter writer = new FileWriter(path)) {
			new GsonBuilder().setPrettyPrinting().create().toJson(json, writer);
		}
	}
}
