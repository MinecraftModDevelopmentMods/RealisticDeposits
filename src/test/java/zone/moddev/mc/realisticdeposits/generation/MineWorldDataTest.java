package zone.moddev.mc.realisticdeposits.generation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.LinkedHashMap;
import java.util.Map;

import org.junit.jupiter.api.Test;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;

import zone.moddev.mc.realisticdeposits.geometry.AbandonedMinePlan;
import zone.moddev.mc.realisticdeposits.geometry.MineBlock;
import zone.moddev.mc.realisticdeposits.geometry.MinePoint;

class MineWorldDataTest {

	@Test
	void theOldPaletteAndSavedVerticalLayoutsStayCompatible() {
		assertEquals(MineBlock.LADDER_WEST, MineBlock.fromSavedId(6));
		assertEquals(MineBlock.COBBLESTONE, MineBlock.fromSavedId(7));
		Map<Long, MineBlock> cells = new LinkedHashMap<>();
		for (int y = 20; y <= 80; y++) {
			cells.put(new MinePoint(0, y, 0).packed(), MineBlock.AIR);
			cells.put(new MinePoint(1, y, 0).packed(), MineBlock.LADDER_WEST);
			cells.put(new MinePoint(2, y, 0).packed(), MineBlock.PLANKS);
		}

		MineWorldData original = new MineWorldData("test");
		original.accept("old-shaft", new AbandonedMinePlan(123,
				new MinePoint(0, 80, 0), new MinePoint(0, 20, 0), cells));
		NBTTagCompound saved = original.writeToNBT(new NBTTagCompound());
		MineWorldData loaded = new MineWorldData("test");
		loaded.readFromNBT(saved);
		assertEquals(cells, loaded.plan("old-shaft").blocks());
		assertEquals(saved, loaded.writeToNBT(new NBTTagCompound()));
	}

	@Test
	void laterChunksUseTheExactSavedCellsAndPalette() {
		MineWorldData original = new MineWorldData("test");
		Map<Long, MineBlock> blocks = new LinkedHashMap<>();
		blocks.put(new MinePoint(-12, 23, -77).packed(), MineBlock.AIR);
		blocks.put(new MinePoint(-12, 22, -77).packed(), MineBlock.PLANKS);
		AbandonedMinePlan plan = new AbandonedMinePlan(123L,
				new MinePoint(-12, 70, -77), new MinePoint(-12, 23, -77), blocks);
		original.accept("deposit@region", plan);
		original.reject("other@region", "existing terrain");
		NBTTagCompound saved = original.writeToNBT(new NBTTagCompound());

		MineWorldData reloaded = new MineWorldData("test");
		reloaded.readFromNBT(saved);

		assertEquals(plan.blocks(), reloaded.plan("deposit@region").blocks());
		assertEquals(plan.entrance(), reloaded.plan("deposit@region").entrance());
		assertEquals(saved, reloaded.writeToNBT(new NBTTagCompound()));
		assertTrue(reloaded.decided("other@region"));
		assertThrows(IllegalStateException.class, () -> reloaded.accept("deposit@region", plan));
		assertEquals(1, reloaded.unbuiltMinesInChunk(-1, -5).size());
		reloaded.sectionHandled("deposit@region", -1, -5);
		assertTrue(reloaded.unbuiltMinesInChunk(-1, -5).isEmpty());
		MineWorldData finished = new MineWorldData("test");
		finished.readFromNBT(reloaded.writeToNBT(new NBTTagCompound()));
		assertTrue(finished.unbuiltMinesInChunk(-1, -5).isEmpty());
		assertEquals(plan.blocks(), finished.plan("deposit@region").blocks());
	}

	@Test
	void unknownOrCorruptSaveDataCannotSilentlyMoveAMine() {
		MineWorldData data = new MineWorldData("test");
		assertThrows(IllegalArgumentException.class, () -> data.readFromNBT(new NBTTagCompound()));

		NBTTagCompound root = new NBTTagCompound();
		root.setInteger("schema_version", 1);
		NBTTagCompound entry = new NBTTagCompound();
		entry.setString("key", "broken");
		entry.setInteger("layout_version", 1);
		entry.setIntArray("positions", new int[] {1, 2});
		entry.setByteArray("palette", new byte[] {(byte) 127});
		NBTTagList entries = new NBTTagList();
		entries.appendTag(entry);
		root.setTag("mines", entries);

		assertThrows(IllegalArgumentException.class, () -> data.readFromNBT(root));
	}
}
