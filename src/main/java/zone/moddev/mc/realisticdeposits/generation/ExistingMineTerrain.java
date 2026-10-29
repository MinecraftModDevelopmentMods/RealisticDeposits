package zone.moddev.mc.realisticdeposits.generation;

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.util.HashSet;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import zone.moddev.mc.realisticdeposits.geometry.BlockBounds;
import zone.moddev.mc.realisticdeposits.geometry.MinePoint;

/** Reads Anvil headers without loading, generating or rewriting any chunks. */
public final class ExistingMineTerrain {
	private static final Pattern REGION_NAME = Pattern.compile("r\\.(-?[0-9]+)\\.(-?[0-9]+)\\.mca");
	private final Set<Long> chunks = new HashSet<>();

	private ExistingMineTerrain() { }

	public static ExistingMineTerrain read(File regionDirectory) throws IOException {
		ExistingMineTerrain result = new ExistingMineTerrain();
		if (!regionDirectory.exists()) return result;

		File[] files = regionDirectory.listFiles();
		if (files == null) throw new IOException("Cannot read region directory " + regionDirectory);

		for (File file : files) {
			Matcher name = REGION_NAME.matcher(file.getName());
			if (!name.matches()) continue;

			try (RandomAccessFile region = new RandomAccessFile(file, "r")) {
				if (region.length() < 4096L) throw new IOException("Incomplete Anvil header: " + file);
				int regionX = Integer.parseInt(name.group(1));
				int regionZ = Integer.parseInt(name.group(2));

				for (int index = 0; index < 1024; index++) {
					if (region.readInt() != 0) {
						result.chunks.add(MinePoint.chunkKey(Math.addExact(Math.multiplyExact(regionX, 32), index & 31),
								Math.addExact(Math.multiplyExact(regionZ, 32), index >> 5)));
					}
				}
			} catch (ArithmeticException | NumberFormatException invalid) {
				throw new IOException("Invalid Anvil region coordinates: " + file, invalid);
			}
		}

		return result;
	}

	public boolean contains(int chunkX, int chunkZ) {
		return chunks.contains(MinePoint.chunkKey(chunkX, chunkZ));
	}

	public boolean intersects(BlockBounds bounds) {
		for (int x = Math.floorDiv(bounds.minimumX(), 16); x <= Math.floorDiv(bounds.maximumX(), 16); x++) {
			for (int z = Math.floorDiv(bounds.minimumZ(), 16); z <= Math.floorDiv(bounds.maximumZ(), 16); z++) {
				if (contains(x, z)) return true;
			}
		}

		return false;
	}
}
