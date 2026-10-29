package zone.moddev.mc.realisticdeposits.generation;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;

import com.google.gson.JsonObject;

import org.apache.logging.log4j.LogManager;

import net.minecraft.block.Block;
import net.minecraft.block.state.IBlockState;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.fml.common.FMLCommonHandler;

import zone.moddev.mc.orespawn.api.GeologyColumn;
import zone.moddev.mc.orespawn.api.GeologyFamily;
import zone.moddev.mc.orespawn.api.GeologySampler;
import zone.moddev.mc.orespawn.api.OreSpawnApi;
import zone.moddev.mc.realisticdeposits.geometry.CoalMeasuresBody;
import zone.moddev.mc.realisticdeposits.model.AlignmentMode;
import zone.moddev.mc.realisticdeposits.model.DepositDefinition;

/** Reads the active geology without changing rocks or anybody else's coal settings. */
final class CoalGeology {
	private static Snapshot active;
	private static int epoch;

	private CoalGeology() { }

	static synchronized void reset() {
		active = null;
		epoch++;
	}

	static int epoch() { return epoch; }

	static synchronized Snapshot active() {
		if (active != null) return active;
		MinecraftServer server = FMLCommonHandler.instance().getMinecraftServerInstance();
		Optional<zone.moddev.mc.orespawn.api.GeologyProfileView> profile = OreSpawnApi.getActiveProfile(server);
		if (!profile.isPresent()) return new Snapshot(false, false);

		active = Snapshot.fromJson(profile.get().toJson());
		if (active.legacyCoal) {
			LogManager.getLogger("Realistic Deposits").warn("This world also has legacy coal rock layers. "
					+ "They remain independent of the new coalfields; choose which supply you want in the world's settings.");
		}
		return active;
	}

	static Set<IBlockState> hosts(DepositDefinition definition) {
		Set<IBlockState> hosts = new HashSet<>();
		for (String name : definition.hosts()) {
			ResourceLocation id = new ResourceLocation(name);
			if (Block.REGISTRY.containsKey(id)) hosts.add(Block.REGISTRY.getObject(id).getStateFromMeta(0));
		}
		return hosts;
	}

	static boolean acceptsGeome(DepositDefinition definition, CoalMeasuresBody body, GeologySampler sampler) {
		if (definition.alignment() == AlignmentMode.MASSIVE_RARE) return true;
		String geome = sampler.sampleColumn(body.centerX(), body.centerZ(), body.centerY()).geome().toString();
		if (!definition.geomes().contains(geome)) return false;
		boolean basin = "orespawn:sedimentary_basin".equals(geome) || "orespawn:wetland_basin".equals(geome);

		// Ancient coal measures can survive beneath a craton or a later mountain belt.
		return basin || body.selectedOutsideBasin();
	}

	static Optional<CoalMeasuresBody> align(CoalMeasuresBody body, DepositDefinition definition,
			GeologySampler sampler, Set<IBlockState> hosts, Snapshot profile) {
		if (!profile.physicalStrata) return Optional.of(body);

		GeologyColumn centre = sampler.sampleColumn(body.centerX(), body.centerZ(), body.centerY());
		if (!Double.isFinite(findBed(centre, body.centerY() + body.bedOffset(0),
				definition.minimumY(), definition.maximumY(), body.bedThickness(0), hosts))) {
			return Optional.empty();
		}

		int step = 16;
		int x = body.bounds().minimumX() - step;
		int z = body.bounds().minimumZ() - step;
		int side = (body.bounds().maximumX() - x + step - 1) / step + 2;
		float[][] heights = new float[body.bedCount()][side * side];
		for (float[] bed : heights) Arrays.fill(bed, Float.NaN);

		for (int gz = 0; gz < side; gz++) {
			for (int gx = 0; gx < side; gx++) {
				int blockX = x + gx * step;
				int blockZ = z + gz * step;
				GeologyColumn column = sampler.sampleColumn(blockX, blockZ, body.centerY());
				int minimum = definition.minimumY();

				for (int bed = 0; bed < body.bedCount(); bed++) {
					double target = body.bedY(bed, blockX, blockZ);
					double fitted = findBed(column, target, minimum, definition.maximumY(),
							body.bedThickness(bed), hosts);
					if (!Double.isFinite(fitted)) break;

					// Keep the shared folds and fault throws after fitting the parent bedding.
					double deformation = target - body.centerY() - body.bedOffset(bed);
					heights[bed][gx + gz * side] = (float) (fitted - deformation);
					minimum = (int) Math.ceil(fitted + body.bedThickness(bed) * 0.5D + 4);
				}
			}
		}

		CoalMeasuresBody.BeddingGrid[] grids = new CoalMeasuresBody.BeddingGrid[body.bedCount()];
		for (int bed = 0; bed < grids.length; bed++) {
			grids[bed] = new CoalMeasuresBody.BeddingGrid(x, z, step, side, heights[bed]);
		}
		CoalMeasuresBody fitted = body.withBedding(grids);
		return Double.isFinite(fitted.bedY(0, body.centerX(), body.centerZ()))
				? Optional.of(fitted) : Optional.empty();
	}

	private static double findBed(GeologyColumn column, double target, int min, int max,
			int thickness, Set<IBlockState> hosts) {
		double best = Double.NaN;
		double distance = Double.POSITIVE_INFINITY;
		int start = -1;

		for (int y = min; y <= max + 1; y++) {
			boolean allowed = y <= max && column.familyAt(y).orElse(null) == GeologyFamily.SEDIMENTARY
					&& hosts.contains(column.rockAt(y));
			if (allowed && start < 0) start = y;
			if (allowed || start < 0) continue;

			int end = y - 1;
			if (end - start + 1 >= thickness) {
				double centre = Math.max(start + thickness * 0.5D,
						Math.min(end + 1 - thickness * 0.5D, target));
				double candidateDistance = Math.abs(centre - target);
				if (candidateDistance <= 16 && candidateDistance < distance) {
					best = centre;
					distance = candidateDistance;
				}
			}
			start = -1;
		}
		return best;
	}

	static final class Snapshot {
		final boolean physicalStrata;
		final boolean legacyCoal;

		Snapshot(boolean physicalStrata, boolean legacyCoal) {
			this.physicalStrata = physicalStrata;
			this.legacyCoal = legacyCoal;
		}

		static Snapshot fromJson(JsonObject root) {
			JsonObject terrain = root.has("terrain_dimensions") ? root.getAsJsonObject("terrain_dimensions") : new JsonObject();
			JsonObject overworld = terrain.has("minecraft:overworld")
					? terrain.getAsJsonObject("minecraft:overworld") : new JsonObject();
			JsonObject cyano = root.has("cyano") ? root.getAsJsonObject("cyano") : new JsonObject();
			boolean physical = overworld.has("enabled") && overworld.get("enabled").getAsBoolean();
			boolean legacy = cyano.has("realistic_coal_layers") && cyano.get("realistic_coal_layers").getAsBoolean();
			if (cyano.has("sedimentary_rocks")) {
				for (com.google.gson.JsonElement rock : cyano.getAsJsonArray("sedimentary_rocks")) {
					legacy |= "minecraft:coal_ore".equals(rock.getAsString());
				}
			}
			return new Snapshot(physical, legacy);
		}
	}
}
