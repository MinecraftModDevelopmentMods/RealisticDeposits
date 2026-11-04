package zone.moddev.mc.realisticdeposits.generation;

import java.util.Collections;
import java.util.Optional;
import java.util.Random;
import java.util.Set;

import com.google.gson.JsonObject;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import net.minecraft.block.state.IBlockState;
import net.minecraft.init.Blocks;
import net.minecraft.init.Bootstrap;
import net.minecraft.init.Items;
import net.minecraft.util.ResourceLocation;

import zone.moddev.mc.orespawn.api.GeologyColumn;
import zone.moddev.mc.orespawn.api.GeologyFamily;
import zone.moddev.mc.realisticdeposits.config.LapisCatalogTest;

import static org.junit.jupiter.api.Assertions.*;

class LapisGeologyTest {
	@BeforeAll
	static void vanilla() {
		Bootstrap.register();
	}

	@Test
	void exactNaturalHostsRejectPolishedAndUnrelatedRocks() {
		for (int metadata = 0; metadata <= 15; metadata++) {
			boolean natural = metadata == 0 || metadata == 1 || metadata == 3 || metadata == 5;
			assertEquals(natural, LapisGeology.permittedRock("minecraft:stone", metadata, false));
			assertFalse(LapisGeology.permittedRock("minecraft:stone", metadata, true));
			assertEquals(metadata == 0, LapisGeology.permittedRock("mineralogy:marble", metadata, true));
		}

		for (String rock : new String[] {"limestone", "dolomite", "schist", "slate", "quartzite",
				"hornfels", "gneiss", "marble_brick", "marble_smooth", "shale", "gypsum", "basalt"}) {
			assertFalse(LapisGeology.permittedRock("mineralogy:" + rock, 0, true));
		}

		Set<IBlockState> hosts = LapisGeology.hosts(LapisCatalogTest.defaults(), new JsonObject(), false);
		assertEquals(4, hosts.size());
		assertFalse(hosts.contains(Blocks.STONE.getStateFromMeta(2)));
		assertFalse(hosts.contains(Blocks.QUARTZ_BLOCK.getDefaultState()));
	}

	@Test
	void fittingRequiresAnActualIntervalThickEnoughForTheLens() {
		Set<IBlockState> marble = Collections.singleton(Blocks.QUARTZ_BLOCK.getDefaultState());
		assertTrue(Double.isNaN(LapisGeology.fitHeight(column(40, 40), 42, 2, 8, 128, marble)));
		assertEquals(43.0, LapisGeology.fitHeight(column(40, 47), 43, 4, 8, 128, marble));
		assertTrue(Double.isNaN(LapisGeology.fitHeight(column(140, 150), 42, 2, 8, 128, marble)));
		assertTrue(Double.isNaN(LapisGeology.fitHeight(column(40, 47), 42, 2, 8, 128, Collections.emptySet())));
	}

	@Test
	void explorationRetainsOnlyABoundedSetOfCompactPlans() throws Exception {
		Class<?> renderer = Class.forName(LapisPattern.class.getName() + "$Renderer");
		java.lang.reflect.Constructor<?> constructor = renderer.getDeclaredConstructors()[0];
		constructor.setAccessible(true);
		Object instance = constructor.newInstance(LapisCatalogTest.defaults());
		java.lang.reflect.Field field = renderer.getDeclaredField("districts");
		field.setAccessible(true);
		@SuppressWarnings("unchecked")
		java.util.Map<Long, Optional<?>> cache = (java.util.Map<Long, Optional<?>>) field.get(instance);

		for (long region = 0; region < 1000; region++) cache.put(region, Optional.empty());
		assertEquals(128, cache.size());

		for (java.lang.reflect.Field member : renderer.getDeclaredFields()) {
			assertNotEquals(MineTerrainSampler.class, member.getType());
			assertNotEquals(zone.moddev.mc.realisticdeposits.geometry.LapisDistrict.Site.class, member.getType());
		}
	}

	@Test
	void nativeLapisDropsUseTheLegacyDyeItemAndFortuneMultiplier() {
		IBlockState ore = Blocks.LAPIS_ORE.getDefaultState();
		assertEquals(Items.DYE, Blocks.LAPIS_ORE.getItemDropped(ore, new Random(1), 0));
		assertEquals(4, Blocks.LAPIS_ORE.damageDropped(ore));

		for (int fortune = 0; fortune <= 3; fortune++) {
			Random random = new Random(371 + fortune);
			long total = 0;

			for (int sample = 0; sample < 40000; sample++) {
				int count = Blocks.LAPIS_ORE.quantityDroppedWithBonus(fortune, random);
				assertTrue(count >= 4 && count <= 8 * (fortune + 1));
				total += count;
			}

			double[] expected = {6, 8, 10.5, 13.2};
			assertEquals(expected[fortune], total / 40000.0, 0.12);
		}
	}

	private static GeologyColumn column(int minimum, int maximum) {
		return new GeologyColumn() {
			@Override public ResourceLocation dimension() { return new ResourceLocation("minecraft:overworld"); }
			@Override public ResourceLocation biome() { return new ResourceLocation("minecraft:plains"); }
			@Override public ResourceLocation geome() { return new ResourceLocation("orespawn:mountain_belt"); }
			@Override public int blockX() { return 0; }
			@Override public int blockZ() { return 0; }
			@Override public int surfaceY() { return 90; }
			@Override public IBlockState rockAt(int y) {
				return y >= minimum && y <= maximum ? Blocks.QUARTZ_BLOCK.getDefaultState() : Blocks.STONE.getDefaultState();
			}
			@Override public Optional<GeologyFamily> familyAt(int y) { return Optional.of(GeologyFamily.METAMORPHIC); }
		};
	}
}
