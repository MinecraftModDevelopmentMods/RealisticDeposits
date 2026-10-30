package zone.moddev.mc.realisticdeposits.content;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Random;

import org.junit.jupiter.api.Test;

class DiamondGravelTest {
	@Test
	void plainAndFortuneRewardsFollowTheOneInSixteenContract() {
		int trials = 320000;
		for (int fortune : new int[] {0, 1, 2, 3}) {
			Random random = new Random(7117);
			int diamonds = 0;
			int successes = 0;
			for (int i = 0; i < trials; i++) {
				int count = DiamondGravelBlock.diamondYield(random, fortune);
				assertTrue(count >= 0 && count <= Math.max(1, fortune + 1));
				diamonds += count;
				if (count > 0) successes++;
			}
			double multiplier = fortune == 0 ? 1 : (double) (1 + (fortune + 1) * (fortune + 2) / 2) / (fortune + 2);
			assertEquals(1D / 16, (double) successes / trials, 0.002);
			assertEquals(multiplier / 16, (double) diamonds / trials, 0.003);
		}
	}
}
