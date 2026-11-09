package zone.moddev.mc.realisticdeposits.geometry;

import java.util.ArrayList;
import java.util.ArrayDeque;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import net.minecraft.entity.monster.EntityZombie;
import net.minecraft.init.Blocks;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.WorldServer;

/** Walks the generated incline and crosscut with native collision and step height. */
public final class EmeraldWalkProbe {
	private EmeraldWalkProbe() { }

	public static void verify(WorldServer world, AbandonedMinePlan plan) {
		List<MinePoint> route = steps(plan);
		MinePoint landing = plan.landing();
		int[][] sides = {{1, 0}, {0, 1}, {-1, 0}, {0, -1}};
		boolean face = false;
		for (int[] side : sides) {
			List<MinePoint> crosscut = new ArrayList<>();
			for (int i = 1; i <= 12; i++) {
				MinePoint p = new MinePoint(landing.x() + i * side[0], landing.y(), landing.z() + i * side[1]);
				if (i >= 6 && world.getBlockState(new BlockPos(p.x(), p.y() + 1, p.z())).getBlock() == Blocks.EMERALD_ORE) {
					route.addAll(crosscut);
					face = true;
					break;
				}
				if (plan.blocks().get(p.packed()) != MineBlock.AIR
						|| plan.blocks().get(new MinePoint(p.x(), p.y() + 1, p.z()).packed()) != MineBlock.AIR) break;
				crosscut.add(p);
			}
			if (face) break;
		}
		if (!face) throw new AssertionError("The crosscut did not expose its emerald face");

		int loaded = world.getChunkProvider().getLoadedChunkCount();
		walk(world, route);
		Collections.reverse(route);
		walk(world, route);
		if (world.getChunkProvider().getLoadedChunkCount() != loaded) {
			throw new AssertionError("Walking an emerald approach loaded another chunk");
		}
	}

	public static int verifyLinked(WorldServer world, AbandonedMinePlan plan, EmeraldDistrict body) {
		MinePoint landing = plan.landing();
		int y = landing.y(), loaded = world.getChunkProvider().getLoadedChunkCount();
		int[][] sides = {{1, 0}, {0, 1}, {-1, 0}, {0, -1}};
		Map<Long, Long> parent = new LinkedHashMap<>();
		ArrayDeque<MinePoint> queue = new ArrayDeque<>();
		parent.put(landing.packed(), landing.packed());
		queue.add(landing);
		while (!queue.isEmpty()) {
			MinePoint current = queue.removeFirst();
			for (int[] side : sides) {
				int x = current.x() + side[0], z = current.z() + side[1];
				MinePoint next = new MinePoint(x, y, z);
				if (parent.containsKey(next.packed())
						|| plan.blocks().get(new MinePoint(x, y - 1, z).packed()) != MineBlock.PLANKS
						|| plan.blocks().get(next.packed()) != MineBlock.AIR
						|| plan.blocks().get(new MinePoint(x, y + 1, z).packed()) != MineBlock.AIR) continue;
				parent.put(next.packed(), current.packed());
				queue.addLast(next);
			}
		}

		int visited = 0;
		for (List<MinePoint> pocket : body.pockets()) {
			Long target = null;
			for (MinePoint ore : pocket) {
				if ((ore.y() != y && ore.y() != y + 1) || (ore.x() >> 4) != (landing.x() >> 4)
						|| (ore.z() >> 4) != (landing.z() >> 4)
						|| world.getBlockState(new BlockPos(ore.x(), ore.y(), ore.z())).getBlock() != Blocks.EMERALD_ORE) continue;
				for (int[] side : sides) {
					long foot = new MinePoint(ore.x() + side[0], y, ore.z() + side[1]).packed();
					if (parent.containsKey(foot)) target = foot;
				}
			}
			if (target == null) continue;
			List<MinePoint> gallery = new ArrayList<>();
			for (long p = target; p != landing.packed(); p = parent.get(p)) gallery.add(MinePoint.unpack(p));
			Collections.reverse(gallery);
			List<MinePoint> route = steps(plan);
			route.addAll(gallery);
			walk(world, route);
			Collections.reverse(route);
			walk(world, route);
			visited++;
		}
		if (visited < 2 || world.getChunkProvider().getLoadedChunkCount() != loaded) {
			throw new AssertionError("Expected two walkable emerald pockets without loading any chunks");
		}
		return visited;
	}

	private static List<MinePoint> steps(AbandonedMinePlan plan) {
		for (int span = 8; span >= 6; span -= 2) {
			for (int forward = 0; forward < 4; forward++) {
				for (int turn = 1; turn <= 3; turn += 2) {
					MineDecline access = MineDecline.compact(plan.landing(), plan.entrance().y(), forward, turn, span);
					if (!access.entrance().equals(plan.entrance()) || !access.hasClearSteps(plan.blocks())) continue;
					List<MinePoint> route = new ArrayList<>();
					for (MineDecline.Step step : access.steps()) route.add(step.point());
					Collections.reverse(route);
					return route;
				}
			}
		}
		throw new AssertionError("No matching compact stair route");
	}

	private static void walk(WorldServer world, List<MinePoint> route) {
		MinePoint start = route.get(0);
		EntityZombie walker = new EntityZombie(world);
		walker.stepHeight = 0.6F;
		walker.setPosition(start.x() + 0.5D, start.y(), start.z() + 0.5D);
		for (int index = 1; index < route.size(); index++) {
			MinePoint target = route.get(index);
			for (int tick = 0; tick < 12; tick++) {
				double dx = Math.max(-0.125D, Math.min(0.125D, target.x() + 0.5D - walker.posX));
				double dz = Math.max(-0.125D, Math.min(0.125D, target.z() + 0.5D - walker.posZ));
				walker.move(dx, -0.15D, dz);
			}
			if (Math.abs(walker.posX - target.x() - 0.5D) >= 0.2D
					|| Math.abs(walker.posZ - target.z() - 0.5D) >= 0.2D
					|| walker.posY < target.y() - 0.05D || walker.posY > target.y() + 1.05D) {
				throw new AssertionError("Cannot walk compact mine step " + target + ": "
						+ walker.posX + "," + walker.posY + "," + walker.posZ);
			}
		}
	}
}
