package zone.moddev.mc.realisticdeposits.config;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;

import zone.moddev.mc.realisticdeposits.model.DepositDefinition;

/** Immutable status displayed by the command and alpha configuration screen. */
public final class RuntimeState {
	public enum IntegrationStatus {
		NOT_LOADED,
		ACTIVE,
		ACTIVE_RESTART_REQUIRED
	}

	private final File configRoot;
	private final List<DepositCatalog> catalogs;
	private final List<DepositDefinition> definitions;
	private final List<String> warnings;
	private final IntegrationStatus integrationStatus;
	private final Map<String, DepositDefinition> definitionsById;
	private final Map<String, String> structuralFingerprints;
	private final List<String> deferredStructuralChanges;

	RuntimeState(File configRoot, List<DepositCatalog> catalogs, List<DepositDefinition> definitions,
			List<String> warnings, IntegrationStatus integrationStatus,
			List<String> deferredStructuralChanges) {
		this.configRoot = configRoot;
		this.catalogs = Collections.unmodifiableList(new ArrayList<>(catalogs));
		this.definitions = Collections.unmodifiableList(new ArrayList<>(definitions));
		this.warnings = Collections.unmodifiableList(new ArrayList<>(warnings));
		this.integrationStatus = integrationStatus;
		Map<String, DepositDefinition> indexed = new LinkedHashMap<>();
		Map<String, String> structures = new LinkedHashMap<>();
		for (DepositDefinition definition : definitions) {
			indexed.put(definition.id(), definition);
			structures.put(definition.id(), definition.structuralFingerprint());
		}
		this.definitionsById = Collections.unmodifiableMap(indexed);
		this.structuralFingerprints = Collections.unmodifiableMap(structures);
		this.deferredStructuralChanges = Collections.unmodifiableList(
				new ArrayList<>(deferredStructuralChanges));
	}

	static RuntimeState unloaded() {
		return new RuntimeState(null, Collections.emptyList(), Collections.emptyList(),
				Collections.emptyList(), IntegrationStatus.NOT_LOADED, Collections.emptyList());
	}

	public File configRoot() { return configRoot; }
	public List<DepositCatalog> catalogs() { return catalogs; }
	public List<DepositDefinition> definitions() { return definitions; }
	public List<String> warnings() { return warnings; }
	public IntegrationStatus integrationStatus() { return integrationStatus; }
	public DepositDefinition definition(String id) { return definitionsById.get(id); }
	String structuralFingerprint(String id) { return structuralFingerprints.get(id); }
	public List<String> deferredStructuralChanges() { return deferredStructuralChanges; }
}
