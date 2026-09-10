package zone.moddev.mc.realisticdeposits.config;

import java.io.Reader;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import zone.moddev.mc.realisticdeposits.model.AlignmentMode;
import zone.moddev.mc.realisticdeposits.model.DepositDefinition;
import zone.moddev.mc.realisticdeposits.model.IntRange;
import zone.moddev.mc.realisticdeposits.model.InteractionPolicy;
import zone.moddev.mc.realisticdeposits.model.StratiformSeamSettings;

/** Strict parser for schema 1 with deterministic forward-key diagnostics. */
public final class DepositCatalogLoader {
	private static final Set<String> ROOT_KEYS = keys("schema_version", "catalog", "deposits");
	private static final Set<String> DEPOSIT_KEYS = keys("id", "enabled", "resource", "alignment",
			"interaction", "dimensions", "hosts", "min_y", "max_y", "region_spacing_chunks",
			"selection_chance", "salt", "shape");
	private static final Set<String> SHAPE_KEYS = keys("type", "strike_length", "down_dip_width",
			"thickness", "dip_degrees", "bend_amplitude", "bend_wavelength",
			"fold_amplitude", "fold_wavelength", "density", "edge_falloff");

	private DepositCatalogLoader() { }

	public static DepositCatalog load(Reader reader, String source) {
		try {
			JsonElement parsed = new JsonParser().parse(reader);
			if (!parsed.isJsonObject()) throw invalid(source, "document must be a JSON object");
			JsonObject root = parsed.getAsJsonObject();
			List<String> warnings = new ArrayList<>();
			unknownKeys(root, ROOT_KEYS, source, warnings);
			int schema = integer(root, "schema_version", source);
			if (schema != DepositCatalog.SCHEMA_VERSION) {
				throw invalid(source, "unsupported schema_version " + schema);
			}
			String catalogId = DepositDefinition.identifier(string(root, "catalog", source), "catalog id");
			JsonArray entries = array(root, "deposits", source);
			List<DepositDefinition> definitions = new ArrayList<>();
			Set<String> ids = new LinkedHashSet<>();
			for (int i = 0; i < entries.size(); i++) {
				String path = source + " deposits[" + i + "]";
				if (!entries.get(i).isJsonObject()) throw invalid(path, "entry must be an object");
				DepositDefinition definition = definition(entries.get(i).getAsJsonObject(), path, warnings);
				if (!ids.add(definition.id())) throw invalid(path, "duplicate deposit id " + definition.id());
				definitions.add(definition);
			}
			if (definitions.isEmpty()) throw invalid(source, "catalog has no deposit definitions");
			return new DepositCatalog(catalogId, definitions, warnings);
		} catch (IllegalArgumentException e) {
			if (e.getMessage() != null && e.getMessage().startsWith(source + ":")) throw e;
			throw invalid(source, e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage(), e);
		} catch (RuntimeException e) {
			throw invalid(source, e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage(), e);
		}
	}

	private static DepositDefinition definition(JsonObject json, String path, List<String> warnings) {
		unknownKeys(json, DEPOSIT_KEYS, path, warnings);
		JsonObject shape = object(json, "shape", path);
		unknownKeys(shape, SHAPE_KEYS, path + " shape", warnings);
		String shapeType = string(shape, "type", path + " shape");
		if (!"stratiform_seam".equals(shapeType)) {
			throw invalid(path, "unsupported shape type " + shapeType);
		}
		double[] thickness = doubleRange(shape, "thickness", path);
		double[] dipDegrees = doubleRange(shape, "dip_degrees", path);
		StratiformSeamSettings settings = new StratiformSeamSettings(
				integerRange(shape, "strike_length", path),
				integerRange(shape, "down_dip_width", path),
				thickness[0], thickness[1], dipDegrees[0], dipDegrees[1],
				number(shape, "bend_amplitude", path),
				number(shape, "bend_wavelength", path),
				number(shape, "fold_amplitude", path),
				number(shape, "fold_wavelength", path),
				number(shape, "density", path),
				number(shape, "edge_falloff", path));
		return new DepositDefinition(
				string(json, "id", path), bool(json, "enabled", true, path),
				string(json, "resource", path),
				AlignmentMode.parse(string(json, "alignment", path)),
				InteractionPolicy.parse(string(json, "interaction", path)),
				strings(json, "dimensions", path), strings(json, "hosts", path),
				integer(json, "min_y", path), integer(json, "max_y", path),
				integer(json, "region_spacing_chunks", path),
				number(json, "selection_chance", path), longNumber(json, "salt", path), settings);
	}

	private static void unknownKeys(JsonObject json, Set<String> allowed, String path,
			List<String> warnings) {
		for (Map.Entry<String, JsonElement> entry : json.entrySet()) {
			if (!allowed.contains(entry.getKey())) warnings.add(path + ": ignored unknown key " + entry.getKey());
		}
	}

	private static JsonObject object(JsonObject json, String key, String path) {
		JsonElement value = required(json, key, path);
		if (!value.isJsonObject()) throw invalid(path, key + " must be an object");
		return value.getAsJsonObject();
	}

	private static JsonArray array(JsonObject json, String key, String path) {
		JsonElement value = required(json, key, path);
		if (!value.isJsonArray()) throw invalid(path, key + " must be an array");
		return value.getAsJsonArray();
	}

	private static String string(JsonObject json, String key, String path) {
		JsonElement value = required(json, key, path);
		if (!value.isJsonPrimitive() || !value.getAsJsonPrimitive().isString()) {
			throw invalid(path, key + " must be a string");
		}
		return value.getAsString();
	}

	private static int integer(JsonObject json, String key, String path) {
		double value = number(json, key, path);
		if (value != Math.rint(value) || value < Integer.MIN_VALUE || value > Integer.MAX_VALUE) {
			throw invalid(path, key + " must be an integer");
		}
		return (int) value;
	}

	private static long longNumber(JsonObject json, String key, String path) {
		JsonElement value = required(json, key, path);
		if (!value.isJsonPrimitive() || !value.getAsJsonPrimitive().isNumber()) {
			throw invalid(path, key + " must be an integer");
		}
		try {
			return new BigDecimal(value.getAsString()).longValueExact();
		} catch (RuntimeException e) {
			throw invalid(path, key + " must be an integer", e);
		}
	}

	private static double number(JsonObject json, String key, String path) {
		JsonElement value = required(json, key, path);
		if (!value.isJsonPrimitive() || !value.getAsJsonPrimitive().isNumber()) {
			throw invalid(path, key + " must be numeric");
		}
		try {
			double result = value.getAsDouble();
			if (!Double.isFinite(result)) throw invalid(path, key + " must be finite");
			return result;
		} catch (NumberFormatException | ClassCastException e) {
			throw invalid(path, key + " must be numeric", e);
		}
	}

	private static boolean bool(JsonObject json, String key, boolean fallback, String path) {
		if (!json.has(key)) return fallback;
		JsonElement value = json.get(key);
		if (!value.isJsonPrimitive() || !value.getAsJsonPrimitive().isBoolean()) {
			throw invalid(path, key + " must be boolean");
		}
		try {
			return value.getAsBoolean();
		} catch (RuntimeException e) {
			throw invalid(path, key + " must be boolean", e);
		}
	}

	private static List<String> strings(JsonObject json, String key, String path) {
		JsonArray values = array(json, key, path);
		List<String> result = new ArrayList<>();
		for (int i = 0; i < values.size(); i++) {
			if (!values.get(i).isJsonPrimitive() || !values.get(i).getAsJsonPrimitive().isString()) {
				throw invalid(path, key + "[" + i + "] must be a string");
			}
			result.add(values.get(i).getAsString());
		}
		return result;
	}

	private static IntRange integerRange(JsonObject json, String key, String path) {
		JsonArray range = array(json, key, path);
		if (range.size() != 2) throw invalid(path, key + " must contain [minimum, maximum]");
		JsonObject holder = new JsonObject();
		holder.add("minimum", range.get(0));
		holder.add("maximum", range.get(1));
		return new IntRange(integer(holder, "minimum", path + " " + key),
				integer(holder, "maximum", path + " " + key));
	}

	private static double[] doubleRange(JsonObject json, String key, String path) {
		JsonArray range = array(json, key, path);
		if (range.size() != 2) throw invalid(path, key + " must contain [minimum, maximum]");
		JsonObject holder = new JsonObject();
		holder.add("minimum", range.get(0));
		holder.add("maximum", range.get(1));
		return new double[] { number(holder, "minimum", path + " " + key),
				number(holder, "maximum", path + " " + key) };
	}

	private static JsonElement required(JsonObject json, String key, String path) {
		if (!json.has(key) || json.get(key).isJsonNull()) throw invalid(path, "missing " + key);
		return json.get(key);
	}

	private static IllegalArgumentException invalid(String path, String message) {
		return new IllegalArgumentException(path + ": " + message);
	}

	private static IllegalArgumentException invalid(String path, String message, Throwable cause) {
		return new IllegalArgumentException(path + ": " + message, cause);
	}

	private static Set<String> keys(String... values) {
		return new HashSet<>(Arrays.asList(values));
	}
}
