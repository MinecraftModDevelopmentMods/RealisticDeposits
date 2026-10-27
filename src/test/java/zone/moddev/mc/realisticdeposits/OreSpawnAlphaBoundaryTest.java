package zone.moddev.mc.realisticdeposits;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

import org.junit.jupiter.api.Test;

import zone.moddev.mc.orespawn.api.OrePlacementContext;
import zone.moddev.mc.orespawn.api.OreGenerationContext;
import zone.moddev.mc.orespawn.api.OreSpawnApi;
import zone.moddev.mc.orespawn.api.WorldgenProvider;

class OreSpawnAlphaBoundaryTest {
	@Test
	void basePatternApiRemainsCompatibleAndOreSpawn41AddsExtendedContext() {
		Set<String> methods = Arrays.stream(OrePlacementContext.class.getMethods())
				.map(Method::getName).collect(Collectors.toSet());
		assertTrue(methods.contains("inside"));
		assertTrue(methods.contains("tryPlace"));
		assertFalse(methods.contains("worldSeed"));
		assertFalse(methods.contains("dimension"));
		assertFalse(methods.contains("chunkX"));
		assertFalse(methods.contains("chunkZ"));
		assertFalse(methods.contains("geologySampler"));
		Set<String> extended = Arrays.stream(OreGenerationContext.class.getMethods())
				.map(Method::getName).collect(Collectors.toSet());
		assertTrue(extended.contains("worldSeed"));
		assertTrue(extended.contains("dimension"));
		assertTrue(extended.contains("chunkX"));
		assertTrue(extended.contains("chunkZ"));
		assertTrue(extended.contains("geologySampler"));
		assertTrue(Arrays.stream(WorldgenProvider.Builder.class.getMethods())
				.anyMatch(method -> method.getName().equals("mergeNewEntriesIntoExistingWorlds")));
		assertTrue(OreSpawnApi.API_VERSION == 1);
	}
}
