package zone.moddev.mc.realisticdeposits.config;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

import zone.moddev.mc.realisticdeposits.model.DepositDefinition;

/** One validated, versioned deposit-definition document. */
public final class DepositCatalog {
	public static final int SCHEMA_VERSION = 1;

	private final String id;
	private final List<DepositDefinition> definitions;
	private final List<String> warnings;

	DepositCatalog(String id, List<DepositDefinition> definitions, List<String> warnings) {
		this.id = Objects.requireNonNull(id, "id");
		this.definitions = Collections.unmodifiableList(new ArrayList<>(definitions));
		this.warnings = Collections.unmodifiableList(new ArrayList<>(warnings));
	}

	public String id() { return id; }
	public List<DepositDefinition> definitions() { return definitions; }
	public List<String> warnings() { return warnings; }
}
