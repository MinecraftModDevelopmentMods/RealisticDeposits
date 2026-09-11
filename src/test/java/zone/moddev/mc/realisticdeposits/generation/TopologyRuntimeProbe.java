package zone.moddev.mc.realisticdeposits.generation;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashSet;
import java.util.Optional;
import java.util.Properties;
import java.util.Set;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import net.minecraft.block.state.IBlockState;
import net.minecraft.init.Blocks;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.WorldServer;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.gen.ChunkProviderServer;
import net.minecraftforge.fml.common.FMLCommonHandler;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.event.FMLServerStartedEvent;
import zone.moddev.mc.realisticdeposits.config.DepositCatalog;
import zone.moddev.mc.realisticdeposits.config.DepositCatalogLoader;
import zone.moddev.mc.realisticdeposits.geometry.BlockBounds;
import zone.moddev.mc.realisticdeposits.geometry.RegionDepositPlanner;
import zone.moddev.mc.realisticdeposits.geometry.StratiformSeamBody;
import zone.moddev.mc.realisticdeposits.model.DepositDefinition;

/** Test-classpath-only full-world topology probe; never packaged in release artifacts. */
@Mod(modid = TopologyRuntimeProbe.MOD_ID, name = "Realistic Deposits Topology Probe",
		version = "1", dependencies = "required-after:realisticdeposits")
public final class TopologyRuntimeProbe {
	static final String MOD_ID = "realisticdepositstopologyprobe";
	private static final Logger LOGGER = LogManager.getLogger(MOD_ID);
	private static final long EXPECTED_SEED = 3346071746553592696L;

	@Mod.EventHandler
	public void serverStarted(FMLServerStartedEvent event) {
		if (!Boolean.getBoolean("realisticDeposits.topologyProbe")) return;
		MinecraftServer server = FMLCommonHandler.instance().getMinecraftServerInstance();
		try {
			WorldServer world = server.worldServerForDimension(0);
			require(world.getSeed() == EXPECTED_SEED, "expected fixed world seed");
			DepositDefinition definition = loadDefinition();
			StratiformSeamBody body = RegionDepositPlanner.plan(definition, world.getSeed(),
					"minecraft:overworld", -1, -1).orElseThrow(
							() -> new IllegalStateException("expected region -1,-1 body"));
			BlockBounds bounds = body.bounds();
			int minimumChunkX = Math.floorDiv(bounds.minimumX(), 16);
			int maximumChunkX = Math.floorDiv(bounds.maximumX(), 16);
			int minimumChunkZ = Math.floorDiv(bounds.minimumZ(), 16);
			int maximumChunkZ = Math.floorDiv(bounds.maximumZ(), 16);
			ChunkProviderServer provider = world.getChunkProvider();

			for (int chunkX = minimumChunkX - 1; chunkX <= maximumChunkX + 1; chunkX++) {
				for (int chunkZ = minimumChunkZ - 1; chunkZ <= maximumChunkZ + 1; chunkZ++) {
					provider.provideChunk(chunkX, chunkZ);
				}
			}
			for (int chunkX = minimumChunkX; chunkX <= maximumChunkX; chunkX++) {
				for (int chunkZ = minimumChunkZ; chunkZ <= maximumChunkZ; chunkZ++) {
					Chunk chunk = provider.provideChunk(chunkX, chunkZ);
					chunk.populateChunk(provider, provider.chunkGenerator);
				}
			}

			Set<Long> generated = new HashSet<>();
			int planned = 0;
			BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
			for (int x = bounds.minimumX(); x <= bounds.maximumX(); x++) {
				for (int z = bounds.minimumZ(); z <= bounds.maximumZ(); z++) {
					Chunk chunk = provider.provideChunk(Math.floorDiv(x, 16), Math.floorDiv(z, 16));
					for (int y = bounds.minimumY(); y <= bounds.maximumY(); y++) {
						if (!body.isOreAt(x, y, z)) continue;
						planned++;
						IBlockState state = chunk.getBlockState(cursor.setPos(x, y, z));
						if (state.getBlock() == Blocks.IRON_ORE) generated.add(pack(x, y, z));
					}
				}
			}

			int largest = largestComponent(generated);
			int edgePositions = 0;
			edgePositions += ironAt(world, -304, 33, -26);
			edgePositions += ironAt(world, -301, 33, -23);
			edgePositions += ironAt(world, -300, 33, -18);
			edgePositions += ironAt(world, -301, 36, -19);
			require(planned >= 20000, "connected planned body size");
			require(generated.size() >= 7000, "safe-host generated ore count");
			require(largest >= 300, "mine-scale connected generated run");
			require(edgePositions == 4, "all four sparse negative-edge positions");

			Properties result = new Properties();
			result.setProperty("seed", Long.toString(world.getSeed()));
			result.setProperty("planned", Integer.toString(planned));
			result.setProperty("generated", Integer.toString(generated.size()));
			result.setProperty("largest_component", Integer.toString(largest));
			result.setProperty("edge_positions", Integer.toString(edgePositions));
			result.setProperty("status", "PASS");
			try (FileOutputStream output = new FileOutputStream(
					new File("realistic-deposits-topology-probe.properties"))) {
				result.store(output, "Realistic Deposits topology runtime probe");
			}
			LOGGER.info("REALISTIC_DEPOSITS_TOPOLOGY_PROBE_PASS planned={} generated={} "
					+ "largestComponent={} edgePositions={}", planned, generated.size(), largest,
					edgePositions);
		} catch (Throwable failure) {
			LOGGER.error("REALISTIC_DEPOSITS_TOPOLOGY_PROBE_FAIL", failure);
			throw failure instanceof RuntimeException ? (RuntimeException) failure
					: new RuntimeException(failure);
		} finally {
			server.initiateShutdown();
		}
	}

	private static DepositDefinition loadDefinition() throws Exception {
		InputStream stream = TopologyRuntimeProbe.class.getResourceAsStream(
				"/assets/realisticdeposits/defaults/vanilla.json");
		require(stream != null, "packaged vanilla catalog");
		try (InputStreamReader reader = new InputStreamReader(stream, StandardCharsets.UTF_8)) {
			DepositCatalog catalog = DepositCatalogLoader.load(reader, "runtime vanilla catalog");
			return catalog.definitions().get(0);
		}
	}

	private static int ironAt(WorldServer world, int x, int y, int z) {
		return world.getBlockState(new BlockPos(x, y, z)).getBlock() == Blocks.IRON_ORE ? 1 : 0;
	}

	private static int largestComponent(Set<Long> positions) {
		Set<Long> remaining = new HashSet<>(positions);
		Deque<Long> pending = new ArrayDeque<>();
		int largest = 0;
		while (!remaining.isEmpty()) {
			long start = remaining.iterator().next();
			remaining.remove(start);
			pending.add(start);
			int size = 0;
			while (!pending.isEmpty()) {
				long packed = pending.removeFirst();
				size++;
				int x = unpackX(packed);
				int y = unpackY(packed);
				int z = unpackZ(packed);
				visit(remaining, pending, x - 1, y, z);
				visit(remaining, pending, x + 1, y, z);
				visit(remaining, pending, x, y - 1, z);
				visit(remaining, pending, x, y + 1, z);
				visit(remaining, pending, x, y, z - 1);
				visit(remaining, pending, x, y, z + 1);
			}
			largest = Math.max(largest, size);
		}
		return largest;
	}

	private static void visit(Set<Long> remaining, Deque<Long> pending, int x, int y, int z) {
		long packed = pack(x, y, z);
		if (remaining.remove(packed)) pending.addLast(packed);
	}

	private static long pack(int x, int y, int z) {
		return ((long) (x & 0x3FFFFFF) << 38) | ((long) (z & 0x3FFFFFF) << 12)
				| (long) (y & 0xFFF);
	}

	private static int unpackX(long packed) { return (int) (packed >> 38); }
	private static int unpackY(long packed) { return (int) (packed << 52 >> 52); }
	private static int unpackZ(long packed) { return (int) (packed << 26 >> 38); }

	private static void require(boolean condition, String check) {
		if (!condition) throw new IllegalStateException("Topology probe failed: " + check);
	}
}
