package zone.moddev.mc.realisticdeposits;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

import org.junit.jupiter.api.Test;

import zone.moddev.mc.orespawn.api.OrePlacementContext;
import zone.moddev.mc.orespawn.api.OreSpawnApi;

class OreSpawnAlphaBoundaryTest {
	@Test
	void releasedApiHasPatternSupportButNotRegionDepositContext() {
		Set<String> methods = Arrays.stream(OrePlacementContext.class.getMethods())
				.map(Method::getName).collect(Collectors.toSet());
		assertTrue(methods.contains("inside"));
		assertTrue(methods.contains("tryPlace"));
		assertFalse(methods.contains("worldSeed"));
		assertFalse(methods.contains("dimension"));
		assertFalse(methods.contains("chunkX"));
		assertFalse(methods.contains("chunkZ"));
		assertFalse(methods.contains("geologySampler"));
		assertTrue(OreSpawnApi.API_VERSION == 1);
	}
}
