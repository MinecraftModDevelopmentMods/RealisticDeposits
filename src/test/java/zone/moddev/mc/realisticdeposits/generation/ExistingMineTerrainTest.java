package zone.moddev.mc.realisticdeposits.generation;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import zone.moddev.mc.realisticdeposits.geometry.BlockBounds;

class ExistingMineTerrainTest {
	@TempDir
	Path directory;

	@Test
	void everySavedChunkIsProtectedIncludingUnpopulatedChunksAndNegativeRegions() throws Exception {
		File file = directory.resolve("r.-1.2.mca").toFile();
		try (RandomAccessFile region = new RandomAccessFile(file, "rw")) {
			region.setLength(8192);
			region.seek((3 + 31 * 32) * 4);
			region.writeInt(0x00000201);
		}

		ExistingMineTerrain existing = ExistingMineTerrain.read(directory.toFile());
		assertTrue(existing.contains(-29, 95));
		assertFalse(existing.contains(-28, 95));
		assertTrue(existing.intersects(new BlockBounds(-464, 5, 1520, -449, 100, 1535)));
		assertFalse(existing.intersects(new BlockBounds(0, 5, 0, 15, 100, 15)));
		assertTrue(file.length() == 8192L, "Inspection must not change the region");
	}

	@Test
	void incompleteHeadersFailClosed() throws Exception {
		try (RandomAccessFile region = new RandomAccessFile(directory.resolve("r.0.0.mca").toFile(), "rw")) {
			region.writeInt(1);
		}

		assertThrows(IOException.class, () -> ExistingMineTerrain.read(directory.toFile()));
	}

	@Test
	void anUnusedWorldHasNoOldTerrain() throws Exception {
		assertFalse(ExistingMineTerrain.read(directory.resolve("region").toFile())
				.intersects(new BlockBounds(-20, 5, -20, 20, 100, 20)));
	}
}
