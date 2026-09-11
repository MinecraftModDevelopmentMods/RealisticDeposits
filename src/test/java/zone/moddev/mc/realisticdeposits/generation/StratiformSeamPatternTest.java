package zone.moddev.mc.realisticdeposits.generation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Random;
import java.util.Set;

import org.junit.jupiter.api.Test;

import zone.moddev.mc.orespawn.api.GeologyColumn;
import zone.moddev.mc.orespawn.api.GeologyFamily;
import zone.moddev.mc.orespawn.api.GeologySampler;
import zone.moddev.mc.orespawn.api.OreGenerationContext;
import zone.moddev.mc.realisticdeposits.geometry.BlockBounds;
import zone.moddev.mc.realisticdeposits.geometry.RegionDepositPlanner;
import zone.moddev.mc.realisticdeposits.geometry.StratiformSeamBody;
import zone.moddev.mc.realisticdeposits.model.AlignmentMode;
import zone.moddev.mc.realisticdeposits.model.DepositDefinition;
import zone.moddev.mc.realisticdeposits.model.IntRange;
import zone.moddev.mc.realisticdeposits.model.InteractionPolicy;
import zone.moddev.mc.realisticdeposits.model.StratiformSeamSettings;

import net.minecraft.block.state.IBlockState;
import net.minecraft.init.Blocks;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.fluids.Fluid;

class StratiformSeamPatternTest {
	private static final long SEED = 5059928472718672684L;

	@Test
	void massiveRareRendersWithoutGeologySampler() {
		DepositDefinition definition = definition(AlignmentMode.MASSIVE_RARE,
				Collections.emptySet(), Collections.emptySet());
		StratiformSeamBody body = RegionDepositPlanner.plan(definition, SEED,
				"minecraft:overworld", 0, 0).get();
		RecordingContext context = context(body, Optional.empty());

		assertTrue(StratiformSeamPattern.place(definition, context));
		assertFalse(context.placed.isEmpty());
	}

	@Test
	void geomeAlignmentQualifiesTheCandidateAtItsCenter() {
		DepositDefinition accepted = definition(AlignmentMode.GEOME_ALIGNED,
				Collections.singleton("orespawn:sedimentary_basin"), Collections.emptySet());
		StratiformSeamBody body = RegionDepositPlanner.plan(accepted, SEED,
				"minecraft:overworld", 0, 0).get();
		RecordingContext matching = context(body, Optional.of(
				new TestSampler("orespawn:sedimentary_basin", GeologyFamily.SEDIMENTARY)));
		RecordingContext rejected = context(body, Optional.of(
				new TestSampler("orespawn:stable_craton", GeologyFamily.SEDIMENTARY)));

		assertTrue(StratiformSeamPattern.place(accepted, matching));
		assertFalse(StratiformSeamPattern.place(accepted, rejected));
		assertTrue(rejected.placed.isEmpty());
	}

	@Test
	void rockAlignmentGatesEveryOutputBlockByFamily() {
		DepositDefinition definition = definition(AlignmentMode.ROCK_ALIGNED,
				Collections.singleton("orespawn:sedimentary_basin"),
				Collections.singleton(GeologyFamily.SEDIMENTARY));
		StratiformSeamBody body = RegionDepositPlanner.plan(definition, SEED,
				"minecraft:overworld", 0, 0).get();
		RecordingContext allowed = context(body, Optional.of(
				new TestSampler("orespawn:sedimentary_basin", GeologyFamily.SEDIMENTARY)));
		RecordingContext disallowed = context(body, Optional.of(
				new TestSampler("orespawn:sedimentary_basin", GeologyFamily.IGNEOUS_INTRUSIVE)));

		assertTrue(StratiformSeamPattern.place(definition, allowed));
		assertFalse(StratiformSeamPattern.place(definition, disallowed));
		assertTrue(disallowed.placed.isEmpty());
	}

	@Test
	void alignedCandidateIsRejectedWhenSamplerIsUnavailable() {
		DepositDefinition definition = definition(AlignmentMode.GEOME_ALIGNED,
				Collections.singleton("orespawn:sedimentary_basin"), Collections.emptySet());
		StratiformSeamBody body = RegionDepositPlanner.plan(definition, SEED,
				"minecraft:overworld", 0, 0).get();
		RecordingContext context = context(body, Optional.empty());

		assertFalse(StratiformSeamPattern.place(definition, context));
		assertTrue(context.placed.isEmpty());
	}

	@Test
	void negativeAndCrossChunkRenderingIsOrderIndependent() {
		DepositDefinition definition = definition(AlignmentMode.MASSIVE_RARE,
				Collections.emptySet(), Collections.emptySet());
		StratiformSeamBody body = RegionDepositPlanner.plan(definition, SEED,
				"minecraft:overworld", -1, -1).get();
		BlockBounds bounds = body.bounds();
		int minChunkX = Math.floorDiv(bounds.minimumX(), 16);
		int maxChunkX = Math.floorDiv(bounds.maximumX(), 16);
		int minChunkZ = Math.floorDiv(bounds.minimumZ(), 16);
		int maxChunkZ = Math.floorDiv(bounds.maximumZ(), 16);

		Set<String> forward = render(definition, minChunkX, maxChunkX, minChunkZ, maxChunkZ, false);
		Set<String> reverse = render(definition, minChunkX, maxChunkX, minChunkZ, maxChunkZ, true);
		assertFalse(forward.isEmpty());
		assertEquals(forward, reverse);
	}

	@Test
	void sparseNegativeEdgeChunkRetainsEveryPlannedPosition() {
		DepositDefinition definition = definition(AlignmentMode.MASSIVE_RARE,
				Collections.emptySet(), Collections.emptySet());
		StratiformSeamBody body = RegionDepositPlanner.plan(definition, SEED,
				"minecraft:overworld", -1, -1).get();
		BlockBounds bounds = body.bounds();
		int selectedChunkX = Integer.MIN_VALUE;
		int selectedChunkZ = Integer.MIN_VALUE;
		Set<String> expected = new HashSet<>();
		for (int chunkX = Math.floorDiv(bounds.minimumX(), 16);
				chunkX <= Math.floorDiv(bounds.maximumX(), 16) && expected.isEmpty(); chunkX++) {
			for (int chunkZ = Math.floorDiv(bounds.minimumZ(), 16);
					chunkZ <= Math.floorDiv(bounds.maximumZ(), 16) && expected.isEmpty(); chunkZ++) {
				for (int x = Math.max(bounds.minimumX(), chunkX << 4);
						x <= Math.min(bounds.maximumX(), (chunkX << 4) + 15); x++) {
					for (int z = Math.max(bounds.minimumZ(), chunkZ << 4);
							z <= Math.min(bounds.maximumZ(), (chunkZ << 4) + 15); z++) {
						for (int y = body.minimumCandidateYAt(x, z);
								y <= body.maximumCandidateYAt(x, z); y++) {
							if (body.isOreAt(x, y, z)) expected.add(x + "," + y + "," + z);
						}
					}
				}
				if (!expected.isEmpty()) {
					selectedChunkX = chunkX;
					selectedChunkZ = chunkZ;
				}
			}
		}
		assertFalse(expected.isEmpty());
		RecordingContext context = new RecordingContext(selectedChunkX, selectedChunkZ,
				Optional.empty(), SEED);

		assertTrue(StratiformSeamPattern.place(definition, context));
		assertTrue(context.placed.containsAll(expected));
	}

	@Test
	void placementRespectsHostSafetyForEveryUnsafeState() {
		DepositDefinition definition = definition(AlignmentMode.MASSIVE_RARE,
				Collections.emptySet(), Collections.emptySet());
		StratiformSeamBody body = RegionDepositPlanner.plan(definition, SEED,
				"minecraft:overworld", 0, 0).get();
		RecordingContext context = context(body, Optional.empty());
		String[] labels = { "air", "liquid", "bedrock", "block_entity", "undeclared_host" };
		int label = 0;
		int chunkMinX = context.chunkX << 4;
		int chunkMinZ = context.chunkZ << 4;
		for (int x = chunkMinX; x <= chunkMinX + 15 && label < labels.length; x++) {
			for (int z = chunkMinZ; z <= chunkMinZ + 15 && label < labels.length; z++) {
				for (int y = body.minimumCandidateYAt(x, z);
						y <= body.maximumCandidateYAt(x, z) && label < labels.length; y++) {
					if (body.isOreAt(x, y, z)) {
						context.reject(x + "," + y + "," + z, labels[label++]);
					}
				}
			}
		}
		assertEquals(labels.length, label);

		assertTrue(StratiformSeamPattern.place(definition, context));
		assertEquals(new HashSet<>(Arrays.asList(labels)), context.rejectedKinds);
		assertTrue(Collections.disjoint(context.placed, context.rejected.keySet()));
	}

	private static Set<String> render(DepositDefinition definition, int minChunkX, int maxChunkX,
			int minChunkZ, int maxChunkZ, boolean reverse) {
		Set<String> result = new HashSet<>();
		int width = maxChunkX - minChunkX + 1;
		int depth = maxChunkZ - minChunkZ + 1;
		for (int step = 0; step < width * depth; step++) {
			int index = reverse ? width * depth - 1 - step : step;
			RecordingContext context = new RecordingContext(minChunkX + index % width,
					minChunkZ + index / width, Optional.empty());
			StratiformSeamPattern.place(definition, context);
			result.addAll(context.placed);
		}
		return result;
	}

	private static RecordingContext context(StratiformSeamBody body,
			Optional<GeologySampler> sampler) {
		return new RecordingContext(Math.floorDiv(body.centerX(), 16),
				Math.floorDiv(body.centerZ(), 16), sampler);
	}

	private static DepositDefinition definition(AlignmentMode alignment, Set<String> geomes,
			Set<GeologyFamily> families) {
		return new DepositDefinition("realisticdeposits:test_iron", true, "minecraft:iron_ore",
				alignment, InteractionPolicy.ADDITIVE,
				Collections.singleton("minecraft:overworld"), Collections.singleton("minecraft:stone"),
				geomes, families, Collections.emptySet(), Collections.emptySet(),
				Collections.emptySet(), Collections.emptySet(), null,
				8, 56, 8, 1.0D, 93475821L,
				new StratiformSeamSettings(new IntRange(32, 32), new IntRange(8, 8),
						2.0D, 2.0D, 0.0D, 0.0D, 0.0D, 32.0D,
						0.0D, 32.0D, 1.0D, 0.0D));
	}

	private static final class RecordingContext implements OreGenerationContext {
		private final int chunkX;
		private final int chunkZ;
		private final Optional<GeologySampler> sampler;
		private final long worldSeed;
		private final Set<String> placed = new HashSet<>();
		private final Map<String, String> rejected = new LinkedHashMap<>();
		private final Set<String> rejectedKinds = new HashSet<>();

		private RecordingContext(int chunkX, int chunkZ, Optional<GeologySampler> sampler) {
			this(chunkX, chunkZ, sampler, SEED);
		}

		private RecordingContext(int chunkX, int chunkZ, Optional<GeologySampler> sampler,
				long worldSeed) {
			this.chunkX = chunkX;
			this.chunkZ = chunkZ;
			this.sampler = sampler;
			this.worldSeed = worldSeed;
		}

		@Override public Random random() { return new Random(1L); }
		@Override public int originX() { return (chunkX << 4) + 8; }
		@Override public int originY() { return 32; }
		@Override public int originZ() { return (chunkZ << 4) + 8; }
		@Override public int minY() { return 8; }
		@Override public int maxY() { return 56; }
		@Override public int quantity() { return 1; }
		@Override public int spread() { return 0; }
		@Override public int verticalSpread() { return 0; }
		@Override public int nodeSize() { return 1; }
		@Override public long worldSeed() { return worldSeed; }
		@Override public ResourceLocation dimension() { return new ResourceLocation("minecraft:overworld"); }
		@Override public int chunkX() { return chunkX; }
		@Override public int chunkZ() { return chunkZ; }
		@Override public Optional<GeologySampler> geologySampler() { return sampler; }
		@Override public boolean inside(int x, int y, int z) {
			return Math.floorDiv(x, 16) == chunkX && Math.floorDiv(z, 16) == chunkZ
					&& y >= minY() && y <= maxY();
		}
		@Override public boolean isFluid(int x, int y, int z, Fluid fluid) { return false; }
		@Override public boolean tryPlace(int x, int y, int z) {
			if (!inside(x, y, z)) return false;
			String coordinate = x + "," + y + "," + z;
			String rejectedKind = rejected.get(coordinate);
			if (rejectedKind != null) {
				rejectedKinds.add(rejectedKind);
				return false;
			}
			return placed.add(coordinate);
		}
		private void reject(String coordinate, String kind) { rejected.put(coordinate, kind); }
	}

	private static final class TestSampler implements GeologySampler {
		private final ResourceLocation geome;
		private final GeologyFamily family;

		private TestSampler(String geome, GeologyFamily family) {
			this.geome = new ResourceLocation(geome);
			this.family = family;
		}

		@Override
		public GeologyColumn sampleColumn(int blockX, int blockZ, int surfaceY) {
			return new GeologyColumn() {
				@Override public ResourceLocation dimension() { return new ResourceLocation("minecraft:overworld"); }
				@Override public ResourceLocation biome() { return new ResourceLocation("minecraft:plains"); }
				@Override public ResourceLocation geome() { return geome; }
				@Override public int blockX() { return blockX; }
				@Override public int blockZ() { return blockZ; }
				@Override public int surfaceY() { return surfaceY; }
				@Override public IBlockState rockAt(int y) { return Blocks.STONE.getDefaultState(); }
				@Override public Optional<GeologyFamily> familyAt(int y) { return Optional.of(family); }
			};
		}
	}
}
