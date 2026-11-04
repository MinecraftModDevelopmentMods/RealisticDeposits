package zone.moddev.mc.realisticdeposits.generation;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import net.minecraft.block.Block;
import net.minecraft.block.state.IBlockState;
import net.minecraft.init.Blocks;
import net.minecraft.util.ResourceLocation;

import zone.moddev.mc.orespawn.api.GeologyColumn;
import zone.moddev.mc.realisticdeposits.model.DepositDefinition;

/** Exact natural hosts: marble with strata, ordinary stone variants without it. */
final class LapisGeology {
	static final ResourceLocation MARBLE = new ResourceLocation("mineralogy", "marble");

	private LapisGeology() { }

	static boolean physicalStrata(JsonObject profile) {
		JsonObject dimensions = profile.getAsJsonObject("terrain_dimensions");
		if (dimensions == null || !dimensions.has("minecraft:overworld")) return false;

		JsonObject overworld = dimensions.getAsJsonObject("minecraft:overworld");
		return overworld.has("enabled") && overworld.get("enabled").getAsBoolean();
	}

	static Set<IBlockState> hosts(DepositDefinition definition, JsonObject profile, boolean physical) {
		Set<IBlockState> hosts = new HashSet<>();

		if (!physical) {
			if (definition.hosts().contains("minecraft:stone")) {
				for (int metadata : new int[] {0, 1, 3, 5}) {
					hosts.add(Blocks.STONE.getStateFromMeta(metadata));
				}
			}

			return hosts;
		}

		JsonObject rocks = profile.getAsJsonObject("rocks");
		if (rocks == null || !Block.REGISTRY.containsKey(MARBLE)) return hosts;

		for (Map.Entry<String, JsonElement> entry : rocks.entrySet()) {
			JsonObject rock = entry.getValue().getAsJsonObject();
			if (!rock.has("block") || (rock.has("enabled") && !rock.get("enabled").getAsBoolean())) continue;

			String id = rock.get("block").getAsString();
			int metadata = rock.has("metadata") ? rock.get("metadata").getAsInt() : 0;

			if (permittedRock(id, metadata, true)) {
				hosts.add(Block.REGISTRY.getObject(MARBLE).getStateFromMeta(0));
			}
		}

		return hosts;
	}

	static boolean permittedRock(String id, int metadata, boolean physical) {
		if (physical) return "mineralogy:marble".equals(id) && metadata == 0;

		return "minecraft:stone".equals(id)
				&& (metadata == 0 || metadata == 1 || metadata == 3 || metadata == 5);
	}

	static double fitHeight(GeologyColumn column, double target, int thickness, int minimum,
			int maximum, Set<IBlockState> hosts) {
		double best = Double.NaN;
		double distance = Double.POSITIVE_INFINITY;
		int start = -1;

		for (int y = minimum; y <= maximum + 1; y++) {
			boolean marble = y <= maximum && hosts.contains(column.rockAt(y));
			if (marble && start < 0) start = y;
			if (marble || start < 0) continue;

			int end = y - 1;
			if (end - start + 1 >= thickness) {
				double candidate = Math.max(start + thickness * 0.5,
						Math.min(end + 1 - thickness * 0.5, target));
				double candidateDistance = Math.abs(candidate - target);

				if (candidateDistance < distance) {
					best = candidate;
					distance = candidateDistance;
				}
			}

			start = -1;
		}

		return best;
	}
}
