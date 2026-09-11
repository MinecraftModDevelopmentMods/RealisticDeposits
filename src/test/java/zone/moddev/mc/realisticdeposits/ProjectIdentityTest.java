package zone.moddev.mc.realisticdeposits;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class ProjectIdentityTest {
	@Test
	void usesStablePublicIdentity() {
		assertEquals("realisticdeposits", RealisticDeposits.MOD_ID);
		assertEquals("Realistic Deposits", RealisticDeposits.NAME);
		assertEquals("0.1.0.110021", RealisticDeposits.VERSION);
		net.minecraftforge.fml.common.Mod annotation = RealisticDeposits.class.getAnnotation(
				net.minecraftforge.fml.common.Mod.class);
		assertTrue(annotation.guiFactory().endsWith("RealisticDepositsGuiFactory"));
		assertEquals("required-after:orespawn@[4.1.0,5.0.0)", annotation.dependencies());
	}
}
