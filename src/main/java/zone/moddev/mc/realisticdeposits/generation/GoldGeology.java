package zone.moddev.mc.realisticdeposits.generation;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import net.minecraft.block.Block;
import net.minecraft.block.state.IBlockState;
import net.minecraft.init.Blocks;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.ResourceLocation;

import zone.moddev.mc.orespawn.api.GeologyProfileView;
import zone.moddev.mc.orespawn.api.OreSpawnApi;

/** Exact natural hosts for the first lode model; quartz carrier rock comes later. */
final class GoldGeology {
	private static final Set<String> ROCKS = Collections.unmodifiableSet(new HashSet<>(Arrays.asList(
			"mineralogy:slate", "mineralogy:phyllite", "mineralogy:schist", "mineralogy:amphibolite",
			"mineralogy:gneiss", "mineralogy:hornfels", "mineralogy:shale", "mineralogy:siltstone",
			"mineralogy:chert", "minecraft:sandstone")));

	private GoldGeology() { }

	static Set<IBlockState> hosts(MinecraftServer server) {
		Set<IBlockState> hosts = new HashSet<>();
		for (int metadata : new int[] {0, 1, 3, 5}) hosts.add(Blocks.STONE.getStateFromMeta(metadata));
		Optional<GeologyProfileView> profile = OreSpawnApi.getActiveProfile(server);
		if (!profile.isPresent()) return hosts;

		JsonObject json = profile.get().toJson();
		if (!json.has("rocks")) return hosts;
		for (Map.Entry<String, JsonElement> entry : json.getAsJsonObject("rocks").entrySet()) {
			JsonObject rock = entry.getValue().getAsJsonObject();
			if (!rock.has("block") || (rock.has("enabled") && !rock.get("enabled").getAsBoolean())) continue;
			String id = rock.get("block").getAsString();
			int metadata = rock.has("metadata") ? rock.get("metadata").getAsInt() : 0;
			if (!permittedRock(id, metadata)) continue;

			ResourceLocation blockId = new ResourceLocation(id);
			if (Block.REGISTRY.containsKey(blockId)) hosts.add(Block.REGISTRY.getObject(blockId).getStateFromMeta(metadata));
		}
		return hosts;
	}

	static boolean permittedRock(String id, int metadata) {
		if ("minecraft:stone".equals(id)) return metadata == 0 || metadata == 1 || metadata == 3 || metadata == 5;
		return metadata == 0 && ROCKS.contains(id);
	}
}
