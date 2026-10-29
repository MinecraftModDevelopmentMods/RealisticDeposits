package zone.moddev.mc.realisticdeposits.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;

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

	@Test
	void anUnusedDisabledRuleDoesNotConflictWithTheActivePolicy() throws IOException {
		Path config = temporary.resolve("disabled-policy");
		Path catalog = config.resolve("realisticdeposits/deposits/vanilla.json");
		Files.createDirectories(catalog.getParent());
		String active = definition("realisticdeposits:active", "minecraft:iron_ore", "replacement", 1);
		String disabled = definition("realisticdeposits:disabled", "minecraft:iron_ore", "hybrid", 1)
				.replace("\"enabled\":true", "\"enabled\":false");
		write(catalog, "{\"schema_version\":1,\"catalog\":\"realisticdeposits:test\",\"deposits\":["
				+ active + "," + disabled + "]}");
		RealisticDepositsRuntime.initializeForTests(config.toFile());
		assertEquals(RuntimeState.IntegrationStatus.ACTIVE, RealisticDepositsRuntime.state().integrationStatus());
	}

	@Test
	void installingCoalLeavesBothEditedCataloguesByteForByteAlone() throws IOException {
		Path config = temporary.resolve("edited-catalogues");
		Path deposits = config.resolve("realisticdeposits/deposits");
		Files.createDirectories(deposits);
		byte[] iron = document("minecraft:iron_ore", "additive", 0.37D).getBytes(StandardCharsets.UTF_8);
		byte[] coal;
		try (java.io.InputStream source = getClass().getResourceAsStream(
				"/assets/realisticdeposits/defaults/coal.json")) {
			coal = new String(com.google.common.io.ByteStreams.toByteArray(source), StandardCharsets.UTF_8)
					.replace("0.15", "0.37").getBytes(StandardCharsets.UTF_8);
		}
		Files.write(deposits.resolve("vanilla.json"), iron);
		Files.write(deposits.resolve("coal.json"), coal);

		RealisticDepositsRuntime.initializeForTests(config.toFile());
		RealisticDepositsRuntime.reloadForTests();

		assertArrayEquals(iron, Files.readAllBytes(deposits.resolve("vanilla.json")));
		assertArrayEquals(coal, Files.readAllBytes(deposits.resolve("coal.json")));
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
