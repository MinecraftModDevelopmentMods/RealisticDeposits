package zone.moddev.mc.realisticdeposits.generation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.Set;

import com.google.gson.JsonObject;
import org.junit.jupiter.api.Test;

import net.minecraft.util.ResourceLocation;

class MineNaturalHostsTest {

	@Test
	void geologyDefinitionIdsDoNotHaveToMatchBlockIds() {
		JsonObject rocks = new JsonObject();
		rocks.add("mineralogy:rock/mineralogy/rock_salt", rock("mineralogy:rock_salt", true));
		rocks.add("pack:shale_layer", rock("mineralogy:shale", true));
		rocks.add("pack:disabled", rock("pack:authored_block", false));
		rocks.add("pack:no_block", new JsonObject());
		JsonObject profile = new JsonObject();
		profile.add("rocks", rocks);

		Set<ResourceLocation> expected = new LinkedHashSet<>(Arrays.asList(
				new ResourceLocation("mineralogy:rock_salt"), new ResourceLocation("mineralogy:shale")));
		assertEquals(expected, MineWorkingsGenerator.naturalRockBlocks(profile));
		assertFalse(MineWorkingsGenerator.naturalRockBlocks(profile)
				.contains(new ResourceLocation("mineralogy:rock/mineralogy/rock_salt")));
	}

	@Test
	void missingRockDeclarationsDoNotAddAnyHosts() {
		assertEquals(java.util.Collections.emptySet(), MineWorkingsGenerator.naturalRockBlocks(new JsonObject()));
	}

	private static JsonObject rock(String block, boolean enabled) {
		JsonObject result = new JsonObject();
		result.addProperty("block", block);
		result.addProperty("enabled", enabled);
		return result;
	}
}
