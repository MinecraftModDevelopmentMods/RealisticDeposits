package zone.moddev.mc.realisticdeposits.generation;

import java.io.FileInputStream;
import java.io.FileWriter;
import java.util.List;
import java.util.Properties;

import com.google.gson.JsonObject;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.WorldServer;
import net.minecraftforge.fml.common.FMLCommonHandler;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.event.FMLServerStartedEvent;

import zone.moddev.mc.realisticdeposits.geometry.AbandonedMinePlan;
import zone.moddev.mc.realisticdeposits.geometry.MineBlock;

/** Reads frozen cells directly, so an upgrade cannot quietly replan an old shaft. */
@Mod(modid = "rdfrozenmineprobe", name = "Frozen Mine Qualification", version = "1",
		dependencies = "required-after:realisticdeposits")
public final class FrozenMineRuntimeProbe {

	@Mod.EventHandler
	public void started(FMLServerStartedEvent event) throws Exception {
		if (!Boolean.getBoolean("rd.frozen.enabled")) return;
		MinecraftServer server = FMLCommonHandler.instance().getMinecraftServerInstance();
		try {
			WorldServer world = server.worldServerForDimension(0);
			MineWorkingsRuntimeProbe.require("mine-test-world".equals(world.getSaveHandler().getWorldDirectory().getName()),
					"The frozen-mine probe must use its disposable world");
			Properties first = new Properties();
			try (FileInputStream input = new FileInputStream("mine-first.properties")) {
				first.load(input);
			}

			MineWorldData data = MineWorldData.get(world);
			AbandonedMinePlan plan = null;
			for (AbandonedMinePlan candidate : data.plans().values()) {
				if (first.getProperty("plan").equals(MineWorkingsRuntimeProbe.planHash(candidate))) plan = candidate;
			}
			MineWorkingsRuntimeProbe.require(plan != null && plan.blocks().containsValue(MineBlock.LADDER_WEST),
					"The old vertical layout was lost or replaced");
			List<Long> old = MineWorkingsRuntimeProbe.split(first.getProperty("chunks"));
			MineWorkingsRuntimeProbe.require(first.getProperty("terrain").equals(MineWorkingsRuntimeProbe.terrainHash(world, old)),
					"The first loaded sections or player marker changed");

			String phase = System.getProperty("rd.frozen.phase");
			List<Long> all = MineWorkingsRuntimeProbe.allChunks(plan);
			if ("complete".equals(phase)) {
				for (long chunk : all) MineWorkingsRuntimeProbe.populate(world, (int) (chunk >> 32), (int) chunk);
			}

			MineWorkingsRuntimeProbe.require(first.getProperty("terrain").equals(MineWorkingsRuntimeProbe.terrainHash(world, old)),
					"Later completion excavated previously finished sections");
			int cells = MineWorkingsRuntimeProbe.verify(world, plan, all, true);
			MineWorkingsRuntimeProbe.verifyAccess(world, plan, true);
			JsonObject report = new JsonObject();
			report.addProperty("plan", MineWorkingsRuntimeProbe.planHash(plan));
			report.addProperty("terrain", MineWorkingsRuntimeProbe.terrainHash(world, all));
			report.addProperty("cells", cells);
			try (FileWriter output = new FileWriter("frozen-" + phase + ".json")) {
				output.write(report.toString());
			}
			System.out.println("RD_FROZEN_MINE_PASS " + phase);
		} finally {
			server.initiateShutdown();
		}
	}
}
