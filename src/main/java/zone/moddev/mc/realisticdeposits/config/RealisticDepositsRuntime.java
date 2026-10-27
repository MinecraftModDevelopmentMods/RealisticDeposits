package zone.moddev.mc.realisticdeposits.config;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.LinkedHashSet;
import java.util.Collections;
import java.util.concurrent.ConcurrentHashMap;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import zone.moddev.mc.realisticdeposits.model.DepositDefinition;

import net.minecraftforge.common.config.Configuration;

/** Loads validated catalogs and separates live geometry from deferred structure. */
public final class RealisticDepositsRuntime {
	private static final Logger LOG = LogManager.getLogger("Realistic Deposits");
	private static final String DEFAULT_RESOURCE =
			"/assets/realisticdeposits/defaults/vanilla.json";
	private static volatile RuntimeState state = RuntimeState.unloaded();
	private static File forgeConfigDirectory;
	private static Map<String, String> startupStructure;
	private static final Set<String> WARNED_MISSING_SAMPLERS = new LinkedHashSet<>();
	private static final Set<String> WARNED_STRUCTURAL_RELOADS = new LinkedHashSet<>();
	private static final Set<String> REPORTED_GENERATION = Collections.newSetFromMap(
			new ConcurrentHashMap<String, Boolean>());
	private static volatile boolean diagnosticsEnabled;

	private RealisticDepositsRuntime() { }

	public static synchronized void initialize(File configDirectory) {
		forgeConfigDirectory = configDirectory;
		startupStructure = null;
		reload();
	}

	public static synchronized void reload() {
		if (forgeConfigDirectory == null) {
			throw new IllegalStateException("Realistic Deposits configuration has not been initialized");
		}
		File configFile = new File(forgeConfigDirectory, "realisticdeposits.cfg");
		Configuration configuration = new Configuration(configFile);
		configuration.load();
		boolean diagnostics = configuration.getBoolean("diagnostics", "general", true,
				"Log validated catalog and OreSpawn integration status at startup.");
		if (configuration.hasChanged()) configuration.save();
		reloadCatalogs(diagnostics);
	}

	static synchronized void initializeForTests(File configDirectory) {
		forgeConfigDirectory = configDirectory;
		startupStructure = null;
		reloadCatalogs(false);
	}

	static synchronized void reloadForTests() {
		reloadCatalogs(false);
	}

	private static void reloadCatalogs(boolean diagnostics) {
		diagnosticsEnabled = diagnostics;
		File root = new File(forgeConfigDirectory, "realisticdeposits");
		File deposits = new File(root, "deposits");
		File defaultCatalog = new File(deposits, "vanilla.json");
		createDefault(defaultCatalog);
		List<File> files = jsonFiles(deposits);
		List<DepositCatalog> catalogs = new ArrayList<>();
		List<DepositDefinition> definitions = new ArrayList<>();
		List<String> warnings = new ArrayList<>();
		Map<String, File> definitionSources = new LinkedHashMap<>();
		for (File file : files) {
			try (Reader reader = new InputStreamReader(new FileInputStream(file), StandardCharsets.UTF_8)) {
				DepositCatalog catalog = DepositCatalogLoader.load(reader, file.getPath());
				catalogs.add(catalog);
				warnings.addAll(catalog.warnings());
				for (DepositDefinition definition : catalog.definitions()) {
					File previous = definitionSources.putIfAbsent(definition.id(), file);
					if (previous != null) {
						throw new IllegalArgumentException(file + ": duplicate deposit " + definition.id()
								+ " already declared by " + previous);
					}
					definitions.add(definition);
				}
			} catch (IOException e) {
				throw new IllegalStateException("Could not read deposit catalog " + file, e);
			}
		}
		validateResourcePolicies(definitions);
		Map<String, String> currentStructure = structure(definitions);
		if (startupStructure == null) startupStructure = currentStructure;
		List<String> deferred = structuralChanges(startupStructure, currentStructure);
		state = new RuntimeState(root, catalogs, definitions, warnings,
				deferred.isEmpty() ? RuntimeState.IntegrationStatus.ACTIVE
						: RuntimeState.IntegrationStatus.ACTIVE_RESTART_REQUIRED,
				deferred);
		if (diagnostics) {
			LOG.info("Loaded {} Realistic Deposits catalog(s) containing {} definition(s).",
					catalogs.size(), definitions.size());
			for (String warning : warnings) LOG.warn(warning);
		}
		if (!deferred.isEmpty()) {
			LOG.warn("{} structural deposit change(s) are deferred until restart/new-world registration: {}",
					deferred.size(), deferred);
		}
	}

	public static RuntimeState state() {
		return state;
	}

	/** Uses live geometry only when the world-captured structural contract still matches. */
	public static DepositDefinition definitionForGeneration(DepositDefinition captured) {
		return resolve(captured, captured.structuralFingerprint(), state);
	}

	/** Compiled-pattern handle that recalculates only when a catalog reload swaps state. */
	public static GenerationDefinitionHandle generationHandle(DepositDefinition captured) {
		return new GenerationDefinitionHandle(captured);
	}

	private static DepositDefinition resolve(DepositDefinition captured, String capturedStructure,
			RuntimeState snapshot) {
		DepositDefinition current = snapshot.definition(captured.id());
		if (current != null && capturedStructure.equals(snapshot.structuralFingerprint(captured.id()))) {
			return current;
		}
		warnStructuralReload(captured.id());
		return captured;
	}

	public static final class GenerationDefinitionHandle {
		private final DepositDefinition captured;
		private final String capturedStructure;
		private volatile RuntimeState observed;
		private volatile DepositDefinition resolved;

		private GenerationDefinitionHandle(DepositDefinition captured) {
			this.captured = java.util.Objects.requireNonNull(captured, "captured");
			this.capturedStructure = captured.structuralFingerprint();
		}

		public DepositDefinition current() {
			RuntimeState snapshot = state;
			if (snapshot != observed) {
				resolved = resolve(captured, capturedStructure, snapshot);
				observed = snapshot;
			}
			return resolved;
		}
	}

	public static synchronized void warnMissingSampler(String definitionId) {
		if (WARNED_MISSING_SAMPLERS.add(definitionId)) {
			LOG.warn("Rejected aligned deposit '{}' because OreSpawn has no active geology sampler "
					+ "for the current dimension", definitionId);
		}
	}

	public static void reportFirstGeneration(String definitionId, String dimension,
			int chunkX, int chunkZ, int placedBlocks) {
		if (diagnosticsEnabled && placedBlocks > 0 && REPORTED_GENERATION.add(definitionId)) {
			LOG.info("Generated first '{}' deposit slice: dimension={}, chunk={},{} blocks={}",
					definitionId, dimension, chunkX, chunkZ, placedBlocks);
		}
	}

	private static synchronized void warnStructuralReload(String definitionId) {
		if (WARNED_STRUCTURAL_RELOADS.add(definitionId)) {
			LOG.warn("Deposit '{}' has a structural reload change; the current world's captured "
					+ "definition remains active until restart/new-world registration", definitionId);
		}
	}

	private static void validateResourcePolicies(List<DepositDefinition> definitions) {
		Map<String, String> contracts = new LinkedHashMap<>();
		for (DepositDefinition definition : definitions) {
			for (String dimension : definition.dimensions()) {
				String key = definition.resource() + "@" + dimension;
				String contract = definition.interaction().configName() + ":"
						+ (definition.effectiveBackgroundGenerationScale().isPresent()
								? definition.effectiveBackgroundGenerationScale().getAsDouble() : "additive");
				String previous = contracts.putIfAbsent(key, contract);
				if (previous != null && !previous.equals(contract)) {
					throw new IllegalArgumentException("Conflicting interaction policies for " + key
							+ ": " + previous + " and " + contract);
				}
			}
		}
	}

	private static Map<String, String> structure(List<DepositDefinition> definitions) {
		Map<String, String> result = new LinkedHashMap<>();
		for (DepositDefinition definition : definitions) {
			result.put(definition.id(), definition.structuralFingerprint());
		}
		return result;
	}

	private static List<String> structuralChanges(Map<String, String> initial,
			Map<String, String> current) {
		Set<String> ids = new java.util.TreeSet<>();
		ids.addAll(initial.keySet());
		ids.addAll(current.keySet());
		List<String> result = new ArrayList<>();
		for (String id : ids) {
			if (!java.util.Objects.equals(initial.get(id), current.get(id))) result.add(id);
		}
		return result;
	}

	private static void createDefault(File target) {
		if (target.isFile()) return;
		File parent = target.getParentFile();
		if (!parent.isDirectory() && !parent.mkdirs()) {
			throw new IllegalStateException("Could not create configuration directory " + parent);
		}
		try (InputStream input = RealisticDepositsRuntime.class.getResourceAsStream(DEFAULT_RESOURCE)) {
			if (input == null) throw new IllegalStateException("Missing packaged default " + DEFAULT_RESOURCE);
			try (OutputStream output = new FileOutputStream(target)) {
				byte[] buffer = new byte[8192];
				int count;
				while ((count = input.read(buffer)) >= 0) output.write(buffer, 0, count);
			}
		} catch (IOException e) {
			throw new IllegalStateException("Could not create default catalog " + target, e);
		}
	}

	private static List<File> jsonFiles(File directory) {
		List<File> result = new ArrayList<>();
		collectJson(directory, result);
		result.sort(Comparator.comparing(File::getPath));
		return result;
	}

	private static void collectJson(File path, List<File> result) {
		File[] children = path.listFiles();
		if (children == null) return;
		for (File child : children) {
			if (child.isDirectory()) collectJson(child, result);
			else if (child.getName().endsWith(".json")) result.add(child);
		}
	}
}
