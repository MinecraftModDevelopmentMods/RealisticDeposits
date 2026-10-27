package zone.moddev.mc.realisticdeposits.generation;

import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;

import net.minecraft.block.Block;
import net.minecraft.block.state.IBlockState;
import net.minecraft.init.Blocks;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.WorldServer;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.gen.ChunkProviderServer;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.terraingen.OreGenEvent;
import net.minecraftforge.fml.common.FMLCommonHandler;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.event.FMLInitializationEvent;
import net.minecraftforge.fml.common.event.FMLServerStartedEvent;
import net.minecraftforge.fml.common.eventhandler.Event;
import net.minecraftforge.fml.common.eventhandler.EventPriority;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import zone.moddev.mc.orespawn.api.OreSpawnApi;
import zone.moddev.mc.realisticdeposits.config.RealisticDepositsRuntime;
import zone.moddev.mc.realisticdeposits.geometry.BlockBounds;
import zone.moddev.mc.realisticdeposits.geometry.RegionDepositPlanner;
import zone.moddev.mc.realisticdeposits.geometry.StratiformSeamBody;
import zone.moddev.mc.realisticdeposits.model.DepositDefinition;

/** Packaged-server qualification fixture, excluded from the public mod jars. */
@Mod(modid = "rdpolicyprobe", name = "Realistic Deposits Policy Probe", version = "1",
        dependencies = "required-after:realisticdeposits")
public final class PolicyQualificationProbe {
    private static final int SAMPLE_SIDE = 9;
    private static final int FIRST_BASE = 64;
    private static final int LATER_BASE = 128;
    private final Map<Long, Integer> ironEvents = new HashMap<>();
    private final Map<Long, Integer> deniedIronEvents = new HashMap<>();

    @Mod.EventHandler
    public void initialize(FMLInitializationEvent event) {
        if (Boolean.getBoolean("rd.qualification.enabled")) {
            MinecraftForge.ORE_GEN_BUS.register(this);
        }
    }

    @SubscribeEvent(priority = EventPriority.LOWEST, receiveCanceled = true)
    public void observeIron(OreGenEvent.GenerateMinable event) {
        if (event.getType() != OreGenEvent.GenerateMinable.EventType.IRON) {
            return;
        }

        long chunk = key(Math.floorDiv(event.getPos().getX(), 16),
                Math.floorDiv(event.getPos().getZ(), 16));
        ironEvents.merge(chunk, 1, Integer::sum);

        if (event.getResult() == Event.Result.DENY) {
            deniedIronEvents.merge(chunk, 1, Integer::sum);
        }
    }

    @Mod.EventHandler
    public void serverStarted(FMLServerStartedEvent event) throws Exception {
        if (!Boolean.getBoolean("rd.qualification.enabled")) {
            return;
        }

        MinecraftServer server = FMLCommonHandler.instance().getMinecraftServerInstance();

        try {
            WorldServer world = server.worldServerForDimension(0);
            world.getGameRules().setOrCreateGameRule("randomTickSpeed", "0");
            String phase = System.getProperty("rd.qualification.phase", "first");
            int laterBase = Integer.getInteger("rd.qualification.laterBase", LATER_BASE);
            JsonObject report = new JsonObject();
            report.addProperty("phase", phase);
            report.add("profile", OreSpawnApi.getActiveProfile(server)
                    .orElseThrow(() -> new IllegalStateException("Missing active OreSpawn profile")).toJson());

            if ("first".equals(phase)) {
                populate(world, FIRST_BASE);
            }

            report.add("first", sample(world, FIRST_BASE));

            if (System.getProperty("rd.qualification.protectedBase") != null) {
                report.add("protected", sample(world, Integer.getInteger("rd.qualification.protectedBase")));
            }

            if ("later".equals(phase)) {
                populate(world, laterBase);
                report.add("later", sample(world, laterBase));
                report.add("first_after_later", sample(world, FIRST_BASE));
            } else if ("stable".equals(phase)) {
                report.add("later", sample(world, laterBase));
            }

            report.addProperty("status", "PASS");

            try (OutputStreamWriter output = new OutputStreamWriter(
                    new FileOutputStream(new File("qualification-" + phase + ".json")),
                    StandardCharsets.UTF_8)) {
                new GsonBuilder().setPrettyPrinting().create().toJson(report, output);
            }

            System.out.println("RD_POLICY_QUALIFICATION_PASS " + phase);
        } finally {
            server.initiateShutdown();
        }
    }

    private static void populate(WorldServer world, int base) {
        ChunkProviderServer provider = world.getChunkProvider();

        // Populate a border too, so later neighbours cannot finish a half-tested chunk.
        for (int x = base - 2; x <= base + SAMPLE_SIDE + 1; x++) {
            for (int z = base - 2; z <= base + SAMPLE_SIDE + 1; z++) {
                provider.provideChunk(x, z);
            }
        }

        for (int x = base - 1; x <= base + SAMPLE_SIDE; x++) {
            for (int z = base - 1; z <= base + SAMPLE_SIDE; z++) {
                Chunk chunk = provider.provideChunk(x, z);
                chunk.populateChunk(provider, provider.chunkGenerator);
            }
        }
    }

    private JsonObject sample(WorldServer world, int base) throws Exception {
        MessageDigest terrain = MessageDigest.getInstance("SHA-256");
        MessageDigest deposits = MessageDigest.getInstance("SHA-256");
        MessageDigest geometry = MessageDigest.getInstance("SHA-256");
        Set<Long> planned = plannedPositions(world.getSeed(), base);
        DepositDefinition definition = RealisticDepositsRuntime.state().definitions().get(0);
        Map<String, Integer> blocked = new HashMap<>();
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        int ordinary = 0;
        int massive = 0;
        int outsideImportedHeight = 0;
        int events = 0;
        int denied = 0;
        int missedStone = 0;
        int unexpectedIron = 0;

        for (long position : new TreeSet<>(planned)) {
            update(geometry, (int) (position >>> 32));
            update(geometry, (int) position);
        }

        for (int chunkX = base; chunkX < base + SAMPLE_SIDE; chunkX++) {
            for (int chunkZ = base; chunkZ < base + SAMPLE_SIDE; chunkZ++) {
                Chunk chunk = world.getChunkProvider().provideChunk(chunkX, chunkZ);

                if (!chunk.isTerrainPopulated()) {
                    throw new IllegalStateException("Unpopulated sample chunk " + chunkX + "," + chunkZ);
                }

                events += ironEvents.getOrDefault(key(chunkX, chunkZ), 0);
                denied += deniedIronEvents.getOrDefault(key(chunkX, chunkZ), 0);

                for (int x = chunkX * 16; x < chunkX * 16 + 16; x++) {
                    for (int z = chunkZ * 16; z < chunkZ * 16 + 16; z++) {
                        for (int y = 0; y < 256; y++) {
                            IBlockState state = chunk.getBlockState(cursor.setPos(x, y, z));
                            update(terrain, Block.getStateId(state));

                            if (y >= definition.minimumY() && y <= definition.maximumY()
                                    && planned.contains(positionKey(x, y, z)) && state.getBlock() != Blocks.IRON_ORE) {
                                if (state.getBlock() == Blocks.STONE) {
                                    missedStone++;
                                } else {
                                    blocked.merge(state.getBlock().getRegistryName().toString(), 1, Integer::sum);
                                }
                            }

                            if (state.getBlock() != Blocks.IRON_ORE) {
                                continue;
                            }

                            if (y >= 96) {
                                if (!planned.contains(positionKey(x, y, z))) {
                                    unexpectedIron++;
                                }

                                massive++;
                                update(deposits, x);
                                update(deposits, y);
                                update(deposits, z);
                            } else {
                                ordinary++;

                                if (y < 12 || y > 18) {
                                    outsideImportedHeight++;
                                }
                            }
                        }
                    }
                }

                terrain.update(chunk.getBiomeArray());
            }
        }

        JsonObject result = new JsonObject();
        result.addProperty("chunks", SAMPLE_SIDE * SAMPLE_SIDE);
        result.addProperty("ordinary_iron", ordinary);
        result.addProperty("massive_iron", massive);
        result.addProperty("ordinary_outside_import_height", outsideImportedHeight);
        result.addProperty("terrain_sha256", hex(terrain.digest()));
        result.addProperty("massive_sha256", hex(deposits.digest()));
        result.addProperty("native_iron_events", events);
        result.addProperty("denied_native_iron_events", denied);
        result.addProperty("planned_positions", planned.size());
        result.addProperty("planned_sha256", hex(geometry.digest()));
        result.addProperty("missed_eligible_stone", missedStone);
        result.addProperty("unexpected_high_iron", unexpectedIron);
        result.add("blocked_hosts", new GsonBuilder().create().toJsonTree(blocked));
        return result;
    }

    private static Set<Long> plannedPositions(long worldSeed, int base) {
        DepositDefinition definition = RealisticDepositsRuntime.state().definitions().get(0);
        int min = base * 16;
        int max = (base + SAMPLE_SIDE) * 16 - 1;
        int spacing = definition.regionSpacingChunks() * 16;
        int radius = (int) Math.ceil(definition.seam().strikeLength().maximum() * 0.5
                + definition.seam().downDipWidth().maximum() * 0.5
                + definition.seam().bendAmplitude() + 13.0);
        Set<Long> positions = new HashSet<>();

        for (int rx = Math.floorDiv(min - radius, spacing); rx <= Math.floorDiv(max + radius, spacing); rx++) {
            for (int rz = Math.floorDiv(min - radius, spacing); rz <= Math.floorDiv(max + radius, spacing); rz++) {
                StratiformSeamBody body = RegionDepositPlanner.plan(definition, worldSeed,
                        "minecraft:overworld", rx, rz).orElse(null);

                if (body == null) {
                    continue;
                }

                BlockBounds bounds = body.bounds();

                for (int x = Math.max(min, bounds.minimumX()); x <= Math.min(max, bounds.maximumX()); x++) {
                    for (int z = Math.max(min, bounds.minimumZ()); z <= Math.min(max, bounds.maximumZ()); z++) {
                        for (int y = body.minimumCandidateYAt(x, z); y <= body.maximumCandidateYAt(x, z); y++) {
                            if (body.isOreAt(x, y, z)) {
                                positions.add(positionKey(x, y, z));
                            }
                        }
                    }
                }
            }
        }

        return positions;
    }

    private static long positionKey(int x, int y, int z) {
        return ((long) (x & 0x3ffffff) << 38) | ((long) (z & 0x3ffffff) << 12) | (y & 0xfff);
    }

    private static long key(int x, int z) {
        return ((long) x << 32) ^ (z & 0xffffffffL);
    }

    private static void update(MessageDigest digest, int value) {
        digest.update((byte) (value >>> 24));
        digest.update((byte) (value >>> 16));
        digest.update((byte) (value >>> 8));
        digest.update((byte) value);
    }

    private static String hex(byte[] bytes) {
        StringBuilder value = new StringBuilder(bytes.length * 2);

        for (byte part : bytes) {
            value.append(Character.forDigit((part >>> 4) & 15, 16));
            value.append(Character.forDigit(part & 15, 16));
        }

        return value.toString();
    }
}
