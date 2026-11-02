package zone.moddev.mc.realisticdeposits.generation;

import java.util.Random;

import org.junit.jupiter.api.Test;
import zone.moddev.mc.realisticdeposits.content.GoldGravelBlock;

import static org.junit.jupiter.api.Assertions.*;

class GoldGeologyTest {
	@Test
	void exactHostsRejectPolishedStoneAndUnrelatedMineralogyRocks() {
		for (int metadata = 0; metadata <= 6; metadata++) {
			assertEquals(metadata == 0 || metadata == 1 || metadata == 3 || metadata == 5,
					GoldGeology.permittedRock("minecraft:stone", metadata));
		}
		for (String rock : new String[] {"slate", "phyllite", "schist", "amphibolite", "gneiss", "shale"}) {
			assertTrue(GoldGeology.permittedRock("mineralogy:" + rock, 0));
			assertFalse(GoldGeology.permittedRock("mineralogy:" + rock, 1));
		}
		for (String rock : new String[] {"peridotite", "pumice", "marble", "rock_salt", "gypsum", "quartzite"}) {
			assertFalse(GoldGeology.permittedRock("mineralogy:" + rock, 0));
		}
		assertFalse(GoldGeology.permittedRock("anothermod:slate", 0));
	}

	@Test
	void nuggetRollIsOneInFourAndFortuneOnlyMultipliesSuccesses() {
		Random plain = new Random(71);
		Random fortune = new Random(71);
		int ordinary = 0;
		int enchanted = 0;
		for (int sample = 0; sample < 100000; sample++) {
			int normal = GoldGravelBlock.nuggetYield(plain, 0);
			int bonus = GoldGravelBlock.nuggetYield(fortune, 3);
			assertTrue(normal == 0 || normal == 1);
			assertTrue(bonus >= 0 && bonus <= 4);
			ordinary += normal;
			enchanted += bonus;
		}
		assertTrue(ordinary > 24000 && ordinary < 26000);
		assertTrue(enchanted > ordinary * 2 && enchanted < ordinary * 2.4);
	}
}
