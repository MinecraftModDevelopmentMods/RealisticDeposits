package zone.moddev.mc.realisticdeposits.model;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.OptionalDouble;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Pattern;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;

import zone.moddev.mc.orespawn.api.GeologyFamily;

/** Immutable, registry-independent large-deposit definition. */
public final class DepositDefinition {
	private static final Pattern IDENTIFIER = Pattern.compile("[a-z0-9_.-]+:[a-z0-9_./-]+");

	private final String id;
	private final boolean enabled;
	private final String resource;
	private final AlignmentMode alignment;
	private final InteractionPolicy interaction;
	private final Set<String> dimensions;
	private final Set<String> hosts;
	private final Set<String> geomes;
	private final Set<GeologyFamily> rockFamilies;
	private final Set<String> biomeIds;
	private final Set<String> excludedBiomeIds;
	private final Set<String> biomeDictionary;
	private final Set<String> excludedBiomeDictionary;
	private final Double backgroundGenerationScale;
	private final int minimumY;
	private final int maximumY;
	private final int regionSpacingChunks;
	private final double selectionChance;
	private final long salt;
	private final DepositShapeSettings shape;
	private final AbandonedMineSettings abandonedMine;

	public DepositDefinition(String id, boolean enabled, String resource,
			AlignmentMode alignment, InteractionPolicy interaction,
			Collection<String> dimensions, Collection<String> hosts,
			int minimumY, int maximumY, int regionSpacingChunks,
			double selectionChance, long salt, StratiformSeamSettings seam) {
		this(id, enabled, resource, alignment, interaction, dimensions, hosts,
				Collections.emptySet(), Collections.emptySet(), Collections.emptySet(),
				Collections.emptySet(), Collections.emptySet(), Collections.emptySet(), null,
				minimumY, maximumY, regionSpacingChunks, selectionChance, salt, seam);
	}

	public DepositDefinition(String id, boolean enabled, String resource,
			AlignmentMode alignment, InteractionPolicy interaction,
			Collection<String> dimensions, Collection<String> hosts,
			Collection<String> geomes, Collection<GeologyFamily> rockFamilies,
			Collection<String> biomeIds, Collection<String> excludedBiomeIds,
			Collection<String> biomeDictionary, Collection<String> excludedBiomeDictionary,
			Double backgroundGenerationScale,
			int minimumY, int maximumY, int regionSpacingChunks,
			double selectionChance, long salt, StratiformSeamSettings seam) {
		this(id, enabled, resource, alignment, interaction, dimensions, hosts, geomes,
				rockFamilies, biomeIds, excludedBiomeIds, biomeDictionary, excludedBiomeDictionary,
				backgroundGenerationScale, minimumY, maximumY, regionSpacingChunks,
				selectionChance, salt, seam, AbandonedMineSettings.DISABLED);
	}

	public DepositDefinition(String id, boolean enabled, String resource,
			AlignmentMode alignment, InteractionPolicy interaction,
			Collection<String> dimensions, Collection<String> hosts,
			Collection<String> geomes, Collection<GeologyFamily> rockFamilies,
			Collection<String> biomeIds, Collection<String> excludedBiomeIds,
			Collection<String> biomeDictionary, Collection<String> excludedBiomeDictionary,
			Double backgroundGenerationScale,
			int minimumY, int maximumY, int regionSpacingChunks,
			double selectionChance, long salt, DepositShapeSettings seam,
			AbandonedMineSettings abandonedMine) {
		this.id = identifier(id, "deposit id");
		this.enabled = enabled;
		this.resource = identifier(resource, "resource");
		this.alignment = Objects.requireNonNull(alignment, "alignment");
		this.interaction = Objects.requireNonNull(interaction, "interaction");
		this.dimensions = identifiers(dimensions, "dimension");
		this.hosts = identifiers(hosts, "host");
		this.geomes = identifiers(geomes, "geome");
		this.rockFamilies = families(rockFamilies);
		this.biomeIds = identifiers(biomeIds, "biome");
		this.excludedBiomeIds = identifiers(excludedBiomeIds, "excluded biome");
		this.biomeDictionary = dictionaryValues(biomeDictionary, "biome dictionary");
		this.excludedBiomeDictionary = dictionaryValues(excludedBiomeDictionary,
				"excluded biome dictionary");
		if (this.dimensions.isEmpty() || this.hosts.isEmpty()) {
			throw new IllegalArgumentException("Deposit must declare dimensions and hosts: " + id);
		}
		if (minimumY < 0 || maximumY > 255 || minimumY > maximumY) {
			throw new IllegalArgumentException("Invalid deposit height range for " + id);
		}
		if (regionSpacingChunks < 8 || regionSpacingChunks > 1024) {
			throw new IllegalArgumentException("Region spacing must be within 8..1024 chunks: " + id);
		}
		if (!Double.isFinite(selectionChance) || selectionChance <= 0.0D || selectionChance > 1.0D) {
			throw new IllegalArgumentException("Selection chance must be within (0,1]: " + id);
		}
		this.minimumY = minimumY;
		this.maximumY = maximumY;
		this.regionSpacingChunks = regionSpacingChunks;
		this.selectionChance = selectionChance;
		this.salt = salt;
		this.shape = Objects.requireNonNull(seam, "shape");
		this.abandonedMine = Objects.requireNonNull(abandonedMine, "abandonedMine");
		if (alignment == AlignmentMode.MASSIVE_RARE
				&& (!this.geomes.isEmpty() || !this.rockFamilies.isEmpty())) {
			throw new IllegalArgumentException("Massive-and-rare deposit cannot declare geology alignment: " + id);
		}
		if (alignment != AlignmentMode.MASSIVE_RARE && this.geomes.isEmpty()) {
			throw new IllegalArgumentException("Aligned deposit must declare at least one geome: " + id);
		}
		if (alignment == AlignmentMode.ROCK_ALIGNED && this.rockFamilies.isEmpty()) {
			throw new IllegalArgumentException("Rock-aligned deposit must declare at least one rock family: " + id);
		}
		if (alignment != AlignmentMode.ROCK_ALIGNED && !this.rockFamilies.isEmpty()) {
			throw new IllegalArgumentException("Only rock-aligned deposits may declare rock families: " + id);
		}
		if (backgroundGenerationScale != null
				&& (!Double.isFinite(backgroundGenerationScale)
						|| backgroundGenerationScale < 0.0D || backgroundGenerationScale > 1.0D)) {
			throw new IllegalArgumentException("Background generation scale must be within [0,1]: " + id);
		}
		if (interaction != InteractionPolicy.HYBRID && backgroundGenerationScale != null) {
			throw new IllegalArgumentException("Only hybrid deposits may configure background_generation_scale: " + id);
		}
		this.backgroundGenerationScale = backgroundGenerationScale;
	}

	public String id() { return id; }
	public boolean enabled() { return enabled; }
	public String resource() { return resource; }
	public AlignmentMode alignment() { return alignment; }
	public InteractionPolicy interaction() { return interaction; }
	public Set<String> dimensions() { return dimensions; }
	public Set<String> hosts() { return hosts; }
	public Set<String> geomes() { return geomes; }
	public Set<GeologyFamily> rockFamilies() { return rockFamilies; }
	public Set<String> biomeIds() { return biomeIds; }
	public Set<String> excludedBiomeIds() { return excludedBiomeIds; }
	public Set<String> biomeDictionary() { return biomeDictionary; }
	public Set<String> excludedBiomeDictionary() { return excludedBiomeDictionary; }
	public OptionalDouble configuredBackgroundGenerationScale() {
		return backgroundGenerationScale == null ? OptionalDouble.empty()
				: OptionalDouble.of(backgroundGenerationScale.doubleValue());
	}
	public OptionalDouble effectiveBackgroundGenerationScale() {
		if (interaction == InteractionPolicy.ADDITIVE) return OptionalDouble.empty();
		if (interaction == InteractionPolicy.REPLACEMENT) return OptionalDouble.of(0.0D);
		return OptionalDouble.of(backgroundGenerationScale == null ? 0.25D
				: backgroundGenerationScale.doubleValue());
	}
	public int minimumY() { return minimumY; }
	public int maximumY() { return maximumY; }
	public int regionSpacingChunks() { return regionSpacingChunks; }
	public double selectionChance() { return selectionChance; }
	public long salt() { return salt; }
	public DepositShapeSettings shape() { return shape; }
	public boolean isCoal() { return shape instanceof CoalMeasuresSettings; }
	public StratiformSeamSettings seam() { return (StratiformSeamSettings) shape; }
	public CoalMeasuresSettings coal() { return (CoalMeasuresSettings) shape; }

	public AbandonedMineSettings abandonedMine() {
		return abandonedMine;
	}

	/** Fields captured in an existing world's OreSpawn profile. */
	public String structuralFingerprint() {
		return resource + "|" + sorted(dimensions) + "|" + sorted(hosts) + "|"
				+ sorted(biomeIds) + "|" + sorted(excludedBiomeIds) + "|"
				+ sorted(biomeDictionary) + "|" + sorted(excludedBiomeDictionary) + "|"
				+ interaction.configName() + "|"
				+ (effectiveBackgroundGenerationScale().isPresent()
						? effectiveBackgroundGenerationScale().getAsDouble() : "additive")
				+ (abandonedMine.enabled() ? "|abandoned_mine:" + abandonedMine.chance() : "")
				+ (isCoal() ? "|shape:coal_measures" : "");
	}

	/** Deterministic schema-1 representation embedded in OreSpawn pattern settings. */
	public JsonObject toJson() {
		JsonObject json = new JsonObject();
		json.addProperty("id", id);
		json.addProperty("enabled", enabled);
		json.addProperty("resource", resource);
		json.addProperty("alignment", alignment.configName());
		json.addProperty("interaction", interaction.configName());
		json.add("dimensions", strings(dimensions));
		json.add("hosts", strings(hosts));
		if (!geomes.isEmpty()) json.add("geomes", strings(geomes));
		if (!rockFamilies.isEmpty()) {
			JsonArray values = new JsonArray();
			for (GeologyFamily family : rockFamilies) values.add(new JsonPrimitive(family.configName()));
			json.add("rock_families", values);
		}
		if (!biomeIds.isEmpty()) json.add("biome_ids", strings(biomeIds));
		if (!excludedBiomeIds.isEmpty()) json.add("excluded_biome_ids", strings(excludedBiomeIds));
		if (!biomeDictionary.isEmpty()) json.add("biome_dictionary", strings(biomeDictionary));
		if (!excludedBiomeDictionary.isEmpty()) {
			json.add("excluded_biome_dictionary", strings(excludedBiomeDictionary));
		}
		if (backgroundGenerationScale != null) {
			json.addProperty("background_generation_scale", backgroundGenerationScale);
		}
		json.addProperty("min_y", minimumY);
		json.addProperty("max_y", maximumY);
		json.addProperty("region_spacing_chunks", regionSpacingChunks);
		json.addProperty("selection_chance", selectionChance);
		json.addProperty("salt", salt);
		json.add("shape", shape.toJson());

		// Leave old, opted-out world profiles byte-for-byte compatible.
		if (abandonedMine.enabled() || abandonedMine.chance() != AbandonedMineSettings.DEFAULT_CHANCE) {
			json.add("abandoned_mine", abandonedMine.toJson());
		}
		return json;
	}

	private static Set<String> identifiers(Collection<String> values, String label) {
		Objects.requireNonNull(values, label + "s");
		Set<String> result = new LinkedHashSet<>();
		for (String value : values) {
			String normalized = identifier(value, label);
			if (!result.add(normalized)) {
				throw new IllegalArgumentException("Duplicate " + label + ": " + normalized);
			}
		}
		return Collections.unmodifiableSet(result);
	}

	private static Set<String> dictionaryValues(Collection<String> values, String label) {
		Objects.requireNonNull(values, label);
		Set<String> result = new LinkedHashSet<>();
		for (String value : values) {
			String normalized = Objects.requireNonNull(value, label).trim().toUpperCase(Locale.ROOT);
			if (normalized.isEmpty() || !normalized.matches("[A-Z0-9_]+")) {
				throw new IllegalArgumentException("Invalid " + label + ": " + value);
			}
			if (!result.add(normalized)) throw new IllegalArgumentException("Duplicate " + label + ": " + normalized);
		}
		return Collections.unmodifiableSet(result);
	}

	private static Set<GeologyFamily> families(Collection<GeologyFamily> values) {
		Objects.requireNonNull(values, "rockFamilies");
		Set<GeologyFamily> result = new LinkedHashSet<>();
		for (GeologyFamily value : values) {
			GeologyFamily family = Objects.requireNonNull(value, "rock family");
			if (!result.add(family)) {
				throw new IllegalArgumentException("Duplicate rock family: " + family.configName());
			}
		}
		return Collections.unmodifiableSet(result);
	}

	private static String sorted(Collection<?> values) {
		TreeSet<String> sorted = new TreeSet<>();
		for (Object value : values) sorted.add(String.valueOf(value));
		return sorted.toString();
	}

	private static JsonArray strings(Collection<String> values) {
		JsonArray result = new JsonArray();
		for (String value : values) result.add(new JsonPrimitive(value));
		return result;
	}

	public static String identifier(String value, String label) {
		Objects.requireNonNull(value, label);
		if (!IDENTIFIER.matcher(value).matches()) {
			throw new IllegalArgumentException("Invalid canonical " + label + ": " + value);
		}
		return value;
	}

	public static List<String> identifiersList(Collection<String> values, String label) {
		return Collections.unmodifiableList(new ArrayList<>(identifiers(values, label)));
	}
}
