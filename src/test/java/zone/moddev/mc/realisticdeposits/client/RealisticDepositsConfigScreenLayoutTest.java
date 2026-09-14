package zone.moddev.mc.realisticdeposits.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import org.junit.jupiter.api.Test;

class RealisticDepositsConfigScreenLayoutTest {
	@Test
	void contentWidthTracksSmallScreensAndCapsNormalScreens() {
		assertEquals(288, RealisticDepositsConfigScreen.contentWidth(320));
		assertEquals(390, RealisticDepositsConfigScreen.contentWidth(854));
	}

	@Test
	void variableTextUsesPixelWidthsAndWrapping() throws Exception {
		Path source = Paths.get("src", "main", "java", "zone", "moddev", "mc",
				"realisticdeposits", "client", "RealisticDepositsConfigScreen.java");
		String text = new String(Files.readAllBytes(source), StandardCharsets.UTF_8);
		assertTrue(text.contains("listFormattedStringToWidth"));
		assertTrue(text.contains("fontRendererObj.getStringWidth"));
		assertTrue(text.contains("trimToWidth(state.configRoot().getPath(), contentWidth)"));
		assertFalse(text.contains("trim(state.configRoot().getPath(), 90)"));
	}
}
