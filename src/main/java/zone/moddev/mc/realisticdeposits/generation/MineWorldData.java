package zone.moddev.mc.realisticdeposits.generation;

import java.util.Collections;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.LinkedHashSet;
import java.util.LinkedHashMap;
import java.util.Map;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.world.WorldSavedData;
import net.minecraft.world.WorldServer;

import zone.moddev.mc.realisticdeposits.geometry.AbandonedMinePlan;
import zone.moddev.mc.realisticdeposits.geometry.MineBlock;
import zone.moddev.mc.realisticdeposits.geometry.MinePoint;
import zone.moddev.mc.realisticdeposits.config.DepositCatalogLoader;
import zone.moddev.mc.realisticdeposits.model.DepositDefinition;
import com.google.gson.JsonParser;

/** Keeps an accepted mine unchanged while its remaining chunks are explored. */
public final class MineWorldData extends WorldSavedData {
	public static final String NAME = "realisticdeposits_mines";
	private final Map<String, AbandonedMinePlan> plans = new LinkedHashMap<>();
	private final Map<String, String> rejected = new LinkedHashMap<>();
	private final Map<String, DepositDefinition> definitions = new LinkedHashMap<>();
	private final Map<Long, List<String>> byChunk = new LinkedHashMap<>();
	private final Map<String, Set<Long>> remaining = new LinkedHashMap<>();

	public MineWorldData(String name) {
		super(name);
	}

	public static MineWorldData get(WorldServer world) {
		MineWorldData data = (MineWorldData) world.getPerWorldStorage().getOrLoadData(MineWorldData.class, NAME);
		if (data == null) {
			if (world.getSaveHandler().getMapFileFromName(NAME).isFile()) {
				throw new IllegalStateException("The saved abandoned-mine plans could not be read; restore their backup before generating more workings");
			}
			data = new MineWorldData(NAME);
			world.getPerWorldStorage().setData(NAME, data);
		}

		return data;
	}

	public Map<String, AbandonedMinePlan> plans() {
		return Collections.unmodifiableMap(plans);
	}

	public boolean decided(String key) {
		return plans.containsKey(key) || rejected.containsKey(key);
	}

	public String rejectionReason(String key) {
		return rejected.get(key);
	}

	public AbandonedMinePlan plan(String key) {
		return plans.get(key);
	}

	public DepositDefinition definition(String key) {
		return definitions.get(key);
	}

	public List<String> minesInChunk(int x, int z) {
		return Collections.unmodifiableList(byChunk.getOrDefault(MinePoint.chunkKey(x, z), Collections.emptyList()));
	}

	public List<String> unbuiltMinesInChunk(int x, int z) {
		long chunk = MinePoint.chunkKey(x, z);
		List<String> result = new ArrayList<>();
		for (String key : minesInChunk(x, z)) {
			if (remaining.get(key).contains(chunk)) result.add(key);
		}
		return result;
	}

	public void sectionHandled(String key, int x, int z) {
		if (remaining.get(key).remove(MinePoint.chunkKey(x, z))) markDirty();
	}

	public void accept(String key, AbandonedMinePlan plan) {
		accept(key, plan, null);
	}

	public void accept(String key, AbandonedMinePlan plan, DepositDefinition definition) {
		if (decided(key)) throw new IllegalStateException("A mine plan cannot be replaced: " + key);
		plans.put(key, plan);
		if (definition != null) definitions.put(key, definition);
		index(key, plan);
		Set<Long> pending = new LinkedHashSet<>();
		for (Map.Entry<Long, List<String>> slice : byChunk.entrySet()) {
			if (slice.getValue().contains(key)) pending.add(slice.getKey());
		}
		remaining.put(key, pending);
		markDirty();
	}

	public void reject(String key, String reason) {
		if (decided(key)) throw new IllegalStateException("A mine decision cannot be replaced: " + key);
		rejected.put(key, reason);
		markDirty();
	}

	@Override
	public void readFromNBT(NBTTagCompound root) {
		if (root.getInteger("schema_version") != 1) {
			throw new IllegalArgumentException("Unsupported Realistic Deposits mine-save version");
		}

		plans.clear();
		rejected.clear();
		definitions.clear();
		byChunk.clear();
		remaining.clear();
		NBTTagList entries = root.getTagList("mines", 10);
		for (int i = 0; i < entries.tagCount(); i++) {
			NBTTagCompound entry = entries.getCompoundTagAt(i);
			String key = entry.getString("key");
			if (key.isEmpty() || decided(key)) throw new IllegalArgumentException("Invalid or duplicate saved mine key");

			if (entry.hasKey("rejected", 8)) {
				rejected.put(key, entry.getString("rejected"));
				continue;
			}

			if (entry.getInteger("layout_version") != AbandonedMinePlan.LAYOUT_VERSION) {
				throw new IllegalArgumentException("Unsupported abandoned mine layout version");
			}

			int[] positions = entry.getIntArray("positions");
			byte[] palette = entry.getByteArray("palette");
			if (positions.length != palette.length * 2 || palette.length == 0
					|| palette.length > AbandonedMinePlan.MAXIMUM_BLOCKS) {
				throw new IllegalArgumentException("Invalid saved mine block arrays");
			}

			Map<Long, MineBlock> blocks = new LinkedHashMap<>();
			for (int cell = 0; cell < palette.length; cell++) {
				long position = (long) positions[cell * 2] << 32 | positions[cell * 2 + 1] & 0xFFFFFFFFL;
				if (blocks.put(position, MineBlock.fromSavedId(palette[cell] & 255)) != null) {
					throw new IllegalArgumentException("Duplicate saved mine position");
				}
			}

			AbandonedMinePlan plan = new AbandonedMinePlan(entry.getLong("identity"),
					MinePoint.unpack(entry.getLong("entrance")), MinePoint.unpack(entry.getLong("landing")), blocks);
			plans.put(key, plan);
			if (entry.hasKey("definition", 8)) {
				definitions.put(key, DepositCatalogLoader.loadDefinition(new JsonParser()
						.parse(entry.getString("definition")).getAsJsonObject(), "saved abandoned mine"));
			}
			index(key, plan);
			if (!entry.hasKey("remaining_chunks", 11)) {
				throw new IllegalArgumentException("Saved mine has no record of its unbuilt sections");
			}
			int[] chunks = entry.getIntArray("remaining_chunks");
			if ((chunks.length & 1) != 0) throw new IllegalArgumentException("Invalid unbuilt mine sections");
			Set<Long> pending = new LinkedHashSet<>();
			for (int chunk = 0; chunk < chunks.length; chunk += 2) {
				long position = MinePoint.chunkKey(chunks[chunk], chunks[chunk + 1]);
				if (plan.cellsInChunk(chunks[chunk], chunks[chunk + 1]).isEmpty() || !pending.add(position)) {
					throw new IllegalArgumentException("Invalid unbuilt mine section claim");
				}
			}
			remaining.put(key, pending);
		}
	}

	@Override
	public NBTTagCompound writeToNBT(NBTTagCompound root) {
		root.setInteger("schema_version", 1);
		NBTTagList entries = new NBTTagList();

		for (Map.Entry<String, AbandonedMinePlan> saved : plans.entrySet()) {
			AbandonedMinePlan plan = saved.getValue();
			NBTTagCompound entry = new NBTTagCompound();
			entry.setString("key", saved.getKey());
			entry.setInteger("layout_version", AbandonedMinePlan.LAYOUT_VERSION);
			entry.setLong("identity", plan.identity());
			entry.setLong("entrance", plan.entrance().packed());
			entry.setLong("landing", plan.landing().packed());
			if (definitions.containsKey(saved.getKey())) {
				entry.setString("definition", definitions.get(saved.getKey()).toJson().toString());
			}
			int[] positions = new int[plan.blocks().size() * 2];
			byte[] palette = new byte[plan.blocks().size()];
			int index = 0;

			for (Map.Entry<Long, MineBlock> block : plan.blocks().entrySet()) {
				positions[index * 2] = (int) (block.getKey() >> 32);
				positions[index * 2 + 1] = (int) (long) block.getKey();
				palette[index] = (byte) block.getValue().savedId();
				index++;
			}

			entry.setIntArray("positions", positions);
			entry.setByteArray("palette", palette);
			int[] chunks = new int[remaining.get(saved.getKey()).size() * 2];
			int chunkIndex = 0;
			for (long chunk : remaining.get(saved.getKey())) {
				chunks[chunkIndex++] = (int) (chunk >> 32);
				chunks[chunkIndex++] = (int) chunk;
			}
			entry.setIntArray("remaining_chunks", chunks);
			entries.appendTag(entry);
		}

		for (Map.Entry<String, String> skipped : rejected.entrySet()) {
			NBTTagCompound entry = new NBTTagCompound();
			entry.setString("key", skipped.getKey());
			entry.setString("rejected", skipped.getValue());
			entries.appendTag(entry);
		}

		root.setTag("mines", entries);
		return root;
	}

	private void index(String key, AbandonedMinePlan plan) {
		for (int x = Math.floorDiv(plan.bounds().minimumX(), 16); x <= Math.floorDiv(plan.bounds().maximumX(), 16); x++) {
			for (int z = Math.floorDiv(plan.bounds().minimumZ(), 16); z <= Math.floorDiv(plan.bounds().maximumZ(), 16); z++) {
				if (!plan.cellsInChunk(x, z).isEmpty()) {
					byChunk.computeIfAbsent(MinePoint.chunkKey(x, z), ignored -> new ArrayList<>()).add(key);
				}
			}
		}
	}
}
