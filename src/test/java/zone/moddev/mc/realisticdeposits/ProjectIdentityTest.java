package zone.moddev.mc.realisticdeposits;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Properties;

import org.junit.jupiter.api.Test;

class ProjectIdentityTest {
	@Test
	void usesStablePublicIdentity() {
		assertEquals("realisticdeposits", RealisticDeposits.MOD_ID);
		assertEquals("Realistic Deposits", RealisticDeposits.NAME);
		assertEquals("0.4.0.110021", RealisticDeposits.VERSION);
		net.minecraftforge.fml.common.Mod annotation = RealisticDeposits.class.getAnnotation(
				net.minecraftforge.fml.common.Mod.class);
		assertTrue(annotation.guiFactory().endsWith("RealisticDepositsGuiFactory"));
		assertEquals("required-after:orespawn@[4.1.5.110021,5.0.0)", annotation.dependencies());
	}

	@Test
	void pinsThePublishedOreSpawnRelease() throws IOException {
		Properties properties = new Properties();

		try (InputStream input = Files.newInputStream(Paths.get("gradle.properties"))) {
			properties.load(input);
		}

		assertEquals("4.1.5.110021", properties.getProperty("orespawn_version"));
		assertEquals("[4.1.5.110021,5.0.0)", properties.getProperty("orespawn_version_range"));
		assertEquals("245586", properties.getProperty("orespawn_curse_project_id"));
		assertEquals("9397156", properties.getProperty("orespawn_curse_file_id"));
		assertEquals("1E580EBB8D93B1256E2E81C7D0D815DEAAAC58A028CB5A77A52773B62048E010",
				properties.getProperty("orespawn_sha256"));
	}
}
