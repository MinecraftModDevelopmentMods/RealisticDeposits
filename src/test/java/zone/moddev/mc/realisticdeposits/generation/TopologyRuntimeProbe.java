package zone.moddev.mc.realisticdeposits.generation;

import java.awt.image.BufferedImage;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.security.MessageDigest;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Properties;
import java.util.Set;
import java.util.TreeSet;

import javax.imageio.ImageIO;

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
			ChunkProviderServer provider = world.getChunkProvider();
			Set<Long> plannedPositions = new HashSet<>();
			Set<Long> plannedChunks = new TreeSet<>();
			for (int x = bounds.minimumX(); x <= bounds.maximumX(); x++) {
				for (int z = bounds.minimumZ(); z <= bounds.maximumZ(); z++) {
					for (int y = body.minimumCandidateYAt(x, z);
							y <= body.maximumCandidateYAt(x, z); y++) {
						if (!body.isOreAt(x, y, z)) continue;
						plannedPositions.add(pack(x, y, z));
						plannedChunks.add(packChunk(Math.floorDiv(x, 16), Math.floorDiv(z, 16)));
					}
				}
			}

			Set<Long> loadedChunks = new TreeSet<>();
			for (long packedChunk : plannedChunks) {
				int chunkX = unpackChunkX(packedChunk);
				int chunkZ = unpackChunkZ(packedChunk);
				for (int offsetX = -1; offsetX <= 1; offsetX++) {
					for (int offsetZ = -1; offsetZ <= 1; offsetZ++) {
						loadedChunks.add(packChunk(chunkX + offsetX, chunkZ + offsetZ));
					}
				}
			}
			for (long packedChunk : loadedChunks) {
				provider.provideChunk(unpackChunkX(packedChunk), unpackChunkZ(packedChunk));
			}
			for (long packedChunk : plannedChunks) {
				Chunk chunk = provider.provideChunk(unpackChunkX(packedChunk), unpackChunkZ(packedChunk));
				chunk.populateChunk(provider, provider.chunkGenerator);
			}

			Set<Long> generated = new HashSet<>();
			BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
			for (long position : plannedPositions) {
				int x = unpackX(position);
				int y = unpackY(position);
				int z = unpackZ(position);
				Chunk chunk = provider.provideChunk(Math.floorDiv(x, 16), Math.floorDiv(z, 16));
				IBlockState state = chunk.getBlockState(cursor.setPos(x, y, z));
				if (state.getBlock() == Blocks.IRON_ORE) generated.add(position);
			}

			List<Integer> components = componentSizes(generated);
			int largest = components.isEmpty() ? 0 : components.get(0);
			int thirdLargest = components.size() < 3 ? 0 : components.get(2);
			double haulageExtent = horizontalExtent(generated);
			LOGGER.info("REALISTIC_DEPOSITS_TOPOLOGY_PROBE_METRICS planned={} generated={} "
					+ "components={} top={} haulage={}", plannedPositions.size(), generated.size(),
					components.size(), joinLargest(components, 8), haulageExtent);
			require(plannedPositions.size() >= 120000 && plannedPositions.size() <= 180000,
					"major district planned size");
			require(generated.size() >= 50000 && generated.size() <= 70000,
					"safe-host generated ore count");
			require(largest >= 2000, "largest mine-scale connected district");
			require(thirdLargest >= 2000, "several multi-thousand-block mining districts");
			require(haulageExtent >= 640.0D, "combined haulage extent");
			File[] maps = renderMaps(generated);

			Properties result = new Properties();
			result.setProperty("seed", Long.toString(world.getSeed()));
			result.setProperty("region", "-1,-1");
			result.setProperty("identity", Long.toUnsignedString(body.identity().seed()));
			result.setProperty("center", body.centerX() + "," + body.centerY() + "," + body.centerZ());
			result.setProperty("planned", Integer.toString(plannedPositions.size()));
			result.setProperty("generated", Integer.toString(generated.size()));
			result.setProperty("components", Integer.toString(components.size()));
			result.setProperty("largest_component", Integer.toString(largest));
			result.setProperty("largest_components", joinLargest(components, 8));
			result.setProperty("haulage_extent", Double.toString(haulageExtent));
			result.setProperty("ore_bounds", positionBounds(generated));
			result.setProperty("faults", faultFingerprint(body));
			result.setProperty("catalog_sha256", sha256(new File(
					"config/realisticdeposits/deposits/vanilla.json")));
			result.setProperty("profile_sha256", sha256(new File(
					"world/serverconfig/orespawn-worldgen.json")));
			result.setProperty("top_down_map_sha256", sha256(maps[0]));
			result.setProperty("x_depth_map_sha256", sha256(maps[1]));
			result.setProperty("z_depth_map_sha256", sha256(maps[2]));
			result.setProperty("status", "PASS");
			try (FileOutputStream output = new FileOutputStream(
					new File("realistic-deposits-topology-probe.properties"))) {
				result.store(output, "Realistic Deposits topology runtime probe");
			}
			LOGGER.info("REALISTIC_DEPOSITS_TOPOLOGY_PROBE_PASS planned={} generated={} "
					+ "components={} largest={} top={} haulage={}", plannedPositions.size(),
					generated.size(), components.size(), largest, joinLargest(components, 8),
					haulageExtent);
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

	private static List<Integer> componentSizes(Set<Long> positions) {
		Set<Long> remaining = new HashSet<>(positions);
		Deque<Long> pending = new ArrayDeque<>();
		List<Integer> result = new ArrayList<>();
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
			result.add(size);
		}
		result.sort(Collections.reverseOrder());
		return result;
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
	private static long packChunk(int x, int z) { return ((long) x << 32) ^ (z & 0xffffffffL); }
	private static int unpackChunkX(long packed) { return (int) (packed >> 32); }
	private static int unpackChunkZ(long packed) { return (int) packed; }

	private static double horizontalExtent(Set<Long> positions) {
		int minimumX = Integer.MAX_VALUE;
		int maximumX = Integer.MIN_VALUE;
		int minimumZ = Integer.MAX_VALUE;
		int maximumZ = Integer.MIN_VALUE;
		for (long packed : positions) {
			int x = unpackX(packed);
			int z = unpackZ(packed);
			minimumX = Math.min(minimumX, x);
			maximumX = Math.max(maximumX, x);
			minimumZ = Math.min(minimumZ, z);
			maximumZ = Math.max(maximumZ, z);
		}
		return positions.isEmpty() ? 0.0D
				: Math.hypot(maximumX - minimumX, maximumZ - minimumZ);
	}

	private static String positionBounds(Set<Long> positions) {
		if (positions.isEmpty()) return "empty";
		int minimumX = Integer.MAX_VALUE;
		int maximumX = Integer.MIN_VALUE;
		int minimumY = Integer.MAX_VALUE;
		int maximumY = Integer.MIN_VALUE;
		int minimumZ = Integer.MAX_VALUE;
		int maximumZ = Integer.MIN_VALUE;
		for (long packed : positions) {
			int x = unpackX(packed);
			int y = unpackY(packed);
			int z = unpackZ(packed);
			minimumX = Math.min(minimumX, x);
			maximumX = Math.max(maximumX, x);
			minimumY = Math.min(minimumY, y);
			maximumY = Math.max(maximumY, y);
			minimumZ = Math.min(minimumZ, z);
			maximumZ = Math.max(maximumZ, z);
		}
		return minimumX + "," + minimumY + "," + minimumZ + ".."
				+ maximumX + "," + maximumY + "," + maximumZ;
	}

	private static String joinLargest(List<Integer> components, int count) {
		StringBuilder result = new StringBuilder();
		for (int i = 0; i < Math.min(count, components.size()); i++) {
			if (i > 0) result.append(',');
			result.append(components.get(i));
		}
		return result.toString();
	}

	private static String faultFingerprint(StratiformSeamBody body) throws Exception {
		MessageDigest digest = MessageDigest.getInstance("SHA-256");
		for (String name : new String[] { "faultAlong", "faultHalfGap",
				"segmentLateralOffset", "segmentVerticalOffset" }) {
			java.lang.reflect.Field field = StratiformSeamBody.class.getDeclaredField(name);
			field.setAccessible(true);
			for (double value : (double[]) field.get(body)) {
				long bits = Double.doubleToLongBits(value);
				for (int shift = 56; shift >= 0; shift -= 8) digest.update((byte) (bits >>> shift));
			}
		}
		return hex(digest.digest());
	}

	private static File[] renderMaps(Set<Long> positions) throws Exception {
		int minimumX = Integer.MAX_VALUE;
		int maximumX = Integer.MIN_VALUE;
		int minimumY = Integer.MAX_VALUE;
		int maximumY = Integer.MIN_VALUE;
		int minimumZ = Integer.MAX_VALUE;
		int maximumZ = Integer.MIN_VALUE;
		for (long packed : positions) {
			int x = unpackX(packed);
			int y = unpackY(packed);
			int z = unpackZ(packed);
			minimumX = Math.min(minimumX, x);
			maximumX = Math.max(maximumX, x);
			minimumY = Math.min(minimumY, y);
			maximumY = Math.max(maximumY, y);
			minimumZ = Math.min(minimumZ, z);
			maximumZ = Math.max(maximumZ, z);
		}
		int widthX = maximumX - minimumX + 1;
		int widthZ = maximumZ - minimumZ + 1;
		int heightY = maximumY - minimumY + 1;
		int[] topCounts = new int[widthX * widthZ];
		int[] topLowestY = new int[topCounts.length];
		java.util.Arrays.fill(topLowestY, Integer.MAX_VALUE);
		int[] xDepthCounts = new int[widthX * heightY];
		int[] zDepthCounts = new int[widthZ * heightY];
		for (long packed : positions) {
			int x = unpackX(packed) - minimumX;
			int y = unpackY(packed) - minimumY;
			int z = unpackZ(packed) - minimumZ;
			int topIndex = x + z * widthX;
			topCounts[topIndex]++;
			topLowestY[topIndex] = Math.min(topLowestY[topIndex], y);
			xDepthCounts[x + (heightY - 1 - y) * widthX]++;
			zDepthCounts[z + (heightY - 1 - y) * widthZ]++;
		}

		BufferedImage topDown = new BufferedImage(widthX, widthZ, BufferedImage.TYPE_INT_RGB);
		for (int z = 0; z < widthZ; z++) {
			for (int x = 0; x < widthX; x++) {
				int index = x + z * widthX;
				topDown.setRGB(x, widthZ - 1 - z,
						topColor(topCounts[index], topLowestY[index], heightY));
			}
		}
		BufferedImage xDepth = projection(widthX, heightY, xDepthCounts);
		BufferedImage zDepth = projection(widthZ, heightY, zDepthCounts);
		File topFile = new File("major-iron-top-down.png");
		File xFile = new File("major-iron-x-depth.png");
		File zFile = new File("major-iron-z-depth.png");
		ImageIO.write(topDown, "png", topFile);
		ImageIO.write(xDepth, "png", xFile);
		ImageIO.write(zDepth, "png", zFile);
		return new File[] { topFile, xFile, zFile };
	}

	private static BufferedImage projection(int width, int height, int[] counts) {
		BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
		for (int y = 0; y < height; y++) {
			for (int x = 0; x < width; x++) {
				int count = counts[x + y * width];
				image.setRGB(x, y, count == 0 ? 0x101218
						: ironColor(Math.min(1.0D, 0.22D + Math.log1p(count) / 4.2D), 0.58D));
			}
		}
		return image;
	}

	private static int topColor(int count, int lowestY, int height) {
		if (count == 0) return 0x101218;
		double intensity = Math.min(1.0D, 0.25D + count * 0.16D);
		double depth = height <= 1 ? 0.5D : lowestY / (double) (height - 1);
		return ironColor(intensity, 0.35D + depth * 0.45D);
	}

	private static int ironColor(double intensity, double depthTone) {
		int red = (int) Math.round(105.0D + 150.0D * intensity);
		int green = (int) Math.round(38.0D + 155.0D * intensity * depthTone);
		int blue = (int) Math.round(18.0D + 62.0D * intensity * depthTone);
		return (red << 16) | (green << 8) | blue;
	}

	private static String sha256(File file) throws Exception {
		require(file.isFile(), "required hash input " + file);
		MessageDigest digest = MessageDigest.getInstance("SHA-256");
		digest.update(Files.readAllBytes(file.toPath()));
		return hex(digest.digest());
	}

	private static String hex(byte[] bytes) {
		StringBuilder result = new StringBuilder(bytes.length * 2);
		for (byte value : bytes) result.append(String.format("%02X", value & 0xff));
		return result.toString();
	}

	private static void require(boolean condition, String check) {
		if (!condition) throw new IllegalStateException("Topology probe failed: " + check);
	}
}
