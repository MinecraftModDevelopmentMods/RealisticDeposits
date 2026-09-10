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

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import zone.moddev.mc.realisticdeposits.model.DepositDefinition;

import net.minecraftforge.common.config.Configuration;

/** Loads alpha configuration without registering an unsafe partial generator. */
public final class RealisticDepositsRuntime {
	private static final Logger LOG = LogManager.getLogger("Realistic Deposits");
	private static final String DEFAULT_RESOURCE =
			"/assets/realisticdeposits/defaults/vanilla.json";
	private static volatile RuntimeState state = RuntimeState.unloaded();
	private static File forgeConfigDirectory;

	private RealisticDepositsRuntime() { }

	public static synchronized void initialize(File configDirectory) {
		forgeConfigDirectory = configDirectory;
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
		state = new RuntimeState(root, catalogs, definitions, warnings,
				RuntimeState.IntegrationStatus.AWAITING_ORESPAWN_API);
		if (diagnostics) {
			LOG.info("Loaded {} Realistic Deposits catalog(s) containing {} definition(s).",
					catalogs.size(), definitions.size());
			for (String warning : warnings) LOG.warn(warning);
		}
		LOG.warn("World generation is intentionally disabled in this alpha: OreSpawn API 1 does not "
				+ "expose stable world, dimension and current-chunk identity or geology sampling to patterns.");
	}

	public static RuntimeState state() {
		return state;
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
