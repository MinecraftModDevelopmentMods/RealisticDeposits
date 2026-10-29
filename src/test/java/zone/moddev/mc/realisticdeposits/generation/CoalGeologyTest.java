package zone.moddev.mc.realisticdeposits.generation;

import static org.junit.jupiter.api.Assertions.*;

import java.util.Optional;
import java.util.Set;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import net.minecraft.block.state.IBlockState;
import net.minecraft.init.Blocks;
import net.minecraft.init.Bootstrap;
import net.minecraft.util.ResourceLocation;

import zone.moddev.mc.orespawn.api.GeologyColumn;
import zone.moddev.mc.orespawn.api.GeologyFamily;
import zone.moddev.mc.orespawn.api.GeologySampler;
import zone.moddev.mc.realisticdeposits.geometry.CoalMeasuresBody;
import zone.moddev.mc.realisticdeposits.geometry.CoalMeasuresBodyTest;

class CoalGeologyTest {
	@BeforeAll
	static void vanilla() { Bootstrap.register(); }

	@Test
	void plainStoneDoesNotIncludeAnyIntrusiveVariants() {
		Set<IBlockState> hosts = CoalGeology.hosts(CoalMeasuresBodyTest.definition());
		assertTrue(hosts.contains(Blocks.STONE.getStateFromMeta(0)));
		for (int metadata = 1; metadata <= 6; metadata++) {
			assertFalse(hosts.contains(Blocks.STONE.getStateFromMeta(metadata)));
		}
		assertFalse(hosts.contains(Blocks.OBSIDIAN.getDefaultState()));
		assertFalse(hosts.contains(Blocks.AIR.getDefaultState()));
	}

	@Test
	void legacyCoalIsDiagnosedWithoutChangingItsSettings() {
		JsonObject profile = new JsonParser().parse("{\"cyano\":{\"realistic_coal_layers\":true,"
				+ "\"sedimentary_rocks\":[\"minecraft:coal_ore\"]}}").getAsJsonObject();
		String before = profile.toString();
		assertTrue(CoalGeology.Snapshot.fromJson(profile).legacyCoal);
		assertFalse(CoalGeology.Snapshot.fromJson(profile).physicalStrata);
		assertEquals(before, profile.toString());
	}

	@Test
	void disabledPhysicalGeologyUsesTheOriginalStoneApproximation() {
		CoalMeasuresBody body = CoalMeasuresBodyTest.body();
		assertSame(body, CoalGeology.align(body, CoalMeasuresBodyTest.definition(), sampler(false),
				CoalGeology.hosts(CoalMeasuresBodyTest.definition()), new CoalGeology.Snapshot(false, false)).get());
	}

	@Test
	void actualBeddingFollowsSedimentaryRocksAndRejectsAnIntrusion() {
		CoalMeasuresBody body = CoalMeasuresBodyTest.body();
		Set<IBlockState> hosts = CoalGeology.hosts(CoalMeasuresBodyTest.definition());
		assertFalse(CoalGeology.align(body, CoalMeasuresBodyTest.definition(), sampler(false),
				hosts, new CoalGeology.Snapshot(true, false)).isPresent());
		Optional<CoalMeasuresBody> fitted = CoalGeology.align(body, CoalMeasuresBodyTest.definition(),
				sampler(true), hosts, new CoalGeology.Snapshot(true, false));
		assertTrue(fitted.isPresent());
		assertTrue(Double.isFinite(fitted.get().bedY(0, body.centerX(), body.centerZ())));
	}

	private static GeologySampler sampler(boolean sedimentary) {
		return (x, z, surface) -> new GeologyColumn() {
			@Override public ResourceLocation dimension() { return new ResourceLocation("minecraft:overworld"); }
			@Override public ResourceLocation biome() { return new ResourceLocation("minecraft:plains"); }
			@Override public ResourceLocation geome() { return new ResourceLocation("orespawn:sedimentary_basin"); }
			@Override public int blockX() { return x; }
			@Override public int blockZ() { return z; }
			@Override public int surfaceY() { return surface; }
			@Override public IBlockState rockAt(int y) { return Blocks.SANDSTONE.getDefaultState(); }
			@Override public Optional<GeologyFamily> familyAt(int y) {
				return Optional.of(sedimentary ? GeologyFamily.SEDIMENTARY : GeologyFamily.IGNEOUS_INTRUSIVE);
			}
		};
	}

	@Test
	void coalKeepsVanillaDropsAndFortuneWithoutNewProcessingRules() {
		assertEquals(net.minecraft.init.Items.COAL, Blocks.COAL_ORE.getItemDropped(
				Blocks.COAL_ORE.getDefaultState(), new java.util.Random(7), 0));
		int ordinary = 0;
		int fortune = 0;
		java.util.Random random = new java.util.Random(17);
		for (int sample = 0; sample < 1000; sample++) {
			ordinary += Blocks.COAL_ORE.quantityDroppedWithBonus(0, random);
			int bonus = Blocks.COAL_ORE.quantityDroppedWithBonus(3, random);
			assertTrue(bonus >= 1 && bonus <= 4);
			fortune += bonus;
		}
		assertEquals(1000, ordinary);
		assertTrue(fortune > ordinary);
	}

	@Test
	void knownStructureBoundsIncludeTheirEdgesButNotNearbyNaturalRock() {
		java.util.List<int[]> boxes = java.util.Collections.singletonList(new int[] {-16, 20, -16, 0, 40, 0});
		assertTrue(CoalStructureProtection.contains(boxes, -16, 20, -16));
		assertTrue(CoalStructureProtection.contains(boxes, 0, 40, 0));
		assertFalse(CoalStructureProtection.contains(boxes, 1, 30, 0));
		assertFalse(CoalStructureProtection.contains(boxes, -5, 41, -5));
	}
}
