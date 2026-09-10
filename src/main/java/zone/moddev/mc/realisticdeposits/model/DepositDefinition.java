package zone.moddev.mc.realisticdeposits.model;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;

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
	private final int minimumY;
	private final int maximumY;
	private final int regionSpacingChunks;
	private final double selectionChance;
	private final long salt;
	private final StratiformSeamSettings seam;

	public DepositDefinition(String id, boolean enabled, String resource,
			AlignmentMode alignment, InteractionPolicy interaction,
			Collection<String> dimensions, Collection<String> hosts,
			int minimumY, int maximumY, int regionSpacingChunks,
			double selectionChance, long salt, StratiformSeamSettings seam) {
		this.id = identifier(id, "deposit id");
		this.enabled = enabled;
		this.resource = identifier(resource, "resource");
		this.alignment = Objects.requireNonNull(alignment, "alignment");
		this.interaction = Objects.requireNonNull(interaction, "interaction");
		this.dimensions = identifiers(dimensions, "dimension");
		this.hosts = identifiers(hosts, "host");
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
		this.seam = Objects.requireNonNull(seam, "seam");
	}

	public String id() { return id; }
	public boolean enabled() { return enabled; }
	public String resource() { return resource; }
	public AlignmentMode alignment() { return alignment; }
	public InteractionPolicy interaction() { return interaction; }
	public Set<String> dimensions() { return dimensions; }
	public Set<String> hosts() { return hosts; }
	public int minimumY() { return minimumY; }
	public int maximumY() { return maximumY; }
	public int regionSpacingChunks() { return regionSpacingChunks; }
	public double selectionChance() { return selectionChance; }
	public long salt() { return salt; }
	public StratiformSeamSettings seam() { return seam; }

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
