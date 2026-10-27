package zone.moddev.mc.realisticdeposits.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import zone.moddev.mc.realisticdeposits.model.DepositDefinition;

class RealisticDepositsRuntimeTest {
	@TempDir Path temporary;

	@Test
	void geometryReloadsLiveWhileStructuralChangesAreDeferred() throws IOException {
		Path config = temporary.resolve("config");
		Path catalog = config.resolve("realisticdeposits/deposits/vanilla.json");
		Files.createDirectories(catalog.getParent());
		write(catalog, document("minecraft:iron_ore", "additive", 1.0D));
		RealisticDepositsRuntime.initializeForTests(config.toFile());
		DepositDefinition captured = RealisticDepositsRuntime.state().definitions().get(0);
		RealisticDepositsRuntime.GenerationDefinitionHandle handle =
				RealisticDepositsRuntime.generationHandle(captured);

		write(catalog, document("minecraft:iron_ore", "additive", 0.5D));
		RealisticDepositsRuntime.reloadForTests();
		DepositDefinition live = handle.current();
		assertEquals(0.5D, live.selectionChance());
		assertEquals(RuntimeState.IntegrationStatus.ACTIVE,
				RealisticDepositsRuntime.state().integrationStatus());

		write(catalog, document("minecraft:gold_ore", "additive", 0.5D));
		RealisticDepositsRuntime.reloadForTests();
		assertSame(captured, handle.current());
		assertEquals(RuntimeState.IntegrationStatus.ACTIVE_RESTART_REQUIRED,
				RealisticDepositsRuntime.state().integrationStatus());
		assertEquals(1, RealisticDepositsRuntime.state().deferredStructuralChanges().size());
	}

	@Test
	void rejectsConflictingPoliciesForOneResourceAndDimension() throws IOException {
		Path config = temporary.resolve("conflict");
		Path catalog = config.resolve("realisticdeposits/deposits/vanilla.json");
		Files.createDirectories(catalog.getParent());
		String first = definition("realisticdeposits:first", "minecraft:iron_ore", "additive", 1.0D);
		String second = definition("realisticdeposits:second", "minecraft:iron_ore", "replacement", 1.0D);
		write(catalog, "{\"schema_version\":1,\"catalog\":\"realisticdeposits:test\","
				+ "\"deposits\":[" + first + "," + second + "]}");
		assertThrows(IllegalArgumentException.class,
				() -> RealisticDepositsRuntime.initializeForTests(config.toFile()));
	}

	private static void write(Path path, String value) throws IOException {
		Files.write(path, value.getBytes(StandardCharsets.UTF_8));
	}

	private static String document(String resource, String interaction, double chance) {
		return "{\"schema_version\":1,\"catalog\":\"realisticdeposits:test\","
				+ "\"deposits\":[" + definition("realisticdeposits:test", resource,
						interaction, chance) + "]}";
	}

	private static String definition(String id, String resource, String interaction, double chance) {
		return "{\"id\":\"" + id + "\",\"enabled\":true,\"resource\":\"" + resource
				+ "\",\"alignment\":\"massive_rare\",\"interaction\":\"" + interaction
				+ "\",\"dimensions\":[\"minecraft:overworld\"],\"hosts\":[\"minecraft:stone\"],"
				+ "\"min_y\":8,\"max_y\":56,\"region_spacing_chunks\":16,"
				+ "\"selection_chance\":" + chance + ",\"salt\":17,\"shape\":{"
				+ "\"type\":\"stratiform_seam\",\"strike_length\":[64,96],"
				+ "\"down_dip_width\":[16,24],\"thickness\":[1.0,3.0],"
				+ "\"dip_degrees\":[-20,20],\"bend_amplitude\":4,\"bend_wavelength\":64,"
				+ "\"fold_amplitude\":3,\"fold_wavelength\":48,\"density\":0.25,"
				+ "\"edge_falloff\":0.2}}";
	}
}
