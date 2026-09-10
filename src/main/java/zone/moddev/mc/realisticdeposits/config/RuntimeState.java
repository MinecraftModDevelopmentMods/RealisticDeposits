package zone.moddev.mc.realisticdeposits.config;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import zone.moddev.mc.realisticdeposits.model.DepositDefinition;

/** Immutable status displayed by the command and alpha configuration screen. */
public final class RuntimeState {
	public enum IntegrationStatus {
		NOT_LOADED,
		AWAITING_ORESPAWN_API
	}

	private final File configRoot;
	private final List<DepositCatalog> catalogs;
	private final List<DepositDefinition> definitions;
	private final List<String> warnings;
	private final IntegrationStatus integrationStatus;

	RuntimeState(File configRoot, List<DepositCatalog> catalogs, List<DepositDefinition> definitions,
			List<String> warnings, IntegrationStatus integrationStatus) {
		this.configRoot = configRoot;
		this.catalogs = Collections.unmodifiableList(new ArrayList<>(catalogs));
		this.definitions = Collections.unmodifiableList(new ArrayList<>(definitions));
		this.warnings = Collections.unmodifiableList(new ArrayList<>(warnings));
		this.integrationStatus = integrationStatus;
	}

	static RuntimeState unloaded() {
		return new RuntimeState(null, Collections.emptyList(), Collections.emptyList(),
				Collections.emptyList(), IntegrationStatus.NOT_LOADED);
	}

	public File configRoot() { return configRoot; }
	public List<DepositCatalog> catalogs() { return catalogs; }
	public List<DepositDefinition> definitions() { return definitions; }
	public List<String> warnings() { return warnings; }
	public IntegrationStatus integrationStatus() { return integrationStatus; }
}
