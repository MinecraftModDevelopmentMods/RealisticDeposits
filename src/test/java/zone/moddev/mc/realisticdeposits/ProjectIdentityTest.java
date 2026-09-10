package zone.moddev.mc.realisticdeposits;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class ProjectIdentityTest {
	@Test
	void usesStablePublicIdentity() {
		assertEquals("realisticdeposits", RealisticDeposits.MOD_ID);
		assertEquals("Realistic Deposits", RealisticDeposits.NAME);
		assertEquals("0.1.0.110021", RealisticDeposits.VERSION);
	}
}
