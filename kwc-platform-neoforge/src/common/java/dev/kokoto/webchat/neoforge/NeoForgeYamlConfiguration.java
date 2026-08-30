package dev.kokoto.webchat.neoforge;

import dev.kokoto.webchat.RelayConfigShapeValidator;
import org.yaml.snakeyaml.DumperOptions;
import org.yaml.snakeyaml.Yaml;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.*;

/** Small SnakeYAML-backed configuration used by the NeoForge platform module. */
public final class NeoForgeYamlConfiguration extends NeoForgeYamlSection {
    public NeoForgeYamlConfiguration() {
        this(new LinkedHashMap<>());
    }

    private NeoForgeYamlConfiguration(Map<String,Object> root) {
        super(root, root, "");
    }

    public static NeoForgeYamlConfiguration empty() {
        return new NeoForgeYamlConfiguration(new LinkedHashMap<>());
    }

    public static NeoForgeYamlConfiguration loadConfiguration(File file) {
        if (file == null || !file.isFile()) return empty();
        try (Reader reader = Files.newBufferedReader(file.toPath(), StandardCharsets.UTF_8)) {
            return loadConfiguration(reader);
        } catch (Exception ignored) {
            return empty();
        }
    }

    public static NeoForgeYamlConfiguration loadStrict(File file) throws IOException {
        if (file == null || !file.isFile()) throw new FileNotFoundException(String.valueOf(file));
        try (Reader reader = Files.newBufferedReader(file.toPath(), StandardCharsets.UTF_8)) {
            Object loaded = new Yaml().load(reader);
            if (loaded == null) return empty();
            if (!(loaded instanceof Map<?, ?> map)) throw new IOException("YAML root must be a mapping");
            NeoForgeYamlConfiguration config = new NeoForgeYamlConfiguration(normalizeMap(map));
            String relayShapeProblem = RelayConfigShapeValidator.problem(config.get("server-relay.groups"));
            if (!relayShapeProblem.isBlank()) throw new IOException(relayShapeProblem);
            return config;
        } catch (IOException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new IOException("Invalid YAML: " + ex.getMessage(), ex);
        }
    }

    @SuppressWarnings("unchecked")
    public static NeoForgeYamlConfiguration loadConfiguration(Reader reader) {
        if (reader == null) return empty();
        try {
            Object loaded = new Yaml().load(reader);
            if (loaded instanceof Map<?, ?> map) {
                return new NeoForgeYamlConfiguration(normalizeMap((Map<?,?>) map));
            }
        } catch (Exception ignored) {
        }
        return empty();
    }

    /** Dot-path leaf snapshot used by the loader-neutral KWC config migration engine. */
    public Map<String,Object> flatLeafValues() {
        LinkedHashMap<String,Object> out = new LinkedHashMap<>();
        for (String path : getKeys(true)) {
            NeoForgeYamlSection child = getConfigurationSection(path);
            if (child != null) {
                if (child.getKeys(false).isEmpty()) out.put(path, new LinkedHashMap<>());
                continue;
            }
            out.put(path, get(path));
        }
        return out;
    }

    public void save(File file) throws IOException {
        if (file == null) throw new IOException("null file");
        File parent = file.getParentFile();
        if (parent != null) Files.createDirectories(parent.toPath());
        DumperOptions options = new DumperOptions();
        options.setDefaultFlowStyle(DumperOptions.FlowStyle.BLOCK);
        options.setPrettyFlow(true);
        options.setIndent(2);
        options.setIndicatorIndent(0);
        options.setDefaultScalarStyle(DumperOptions.ScalarStyle.PLAIN);
        Yaml yaml = new Yaml(options);
        try (Writer writer = Files.newBufferedWriter(file.toPath(), StandardCharsets.UTF_8)) {
            yaml.dump(root, writer);
        }
    }

    @SuppressWarnings("unchecked")
    private static LinkedHashMap<String,Object> normalizeMap(Map<?,?> input) {
        LinkedHashMap<String,Object> out = new LinkedHashMap<>();
        for (Map.Entry<?,?> e : input.entrySet()) {
            if (e.getKey() == null) continue;
            Object value = e.getValue();
            if (value instanceof Map<?, ?> child) value = normalizeMap(child);
            else if (value instanceof List<?> list) value = normalizeList(list);
            out.put(String.valueOf(e.getKey()), value);
        }
        return out;
    }

    private static List<Object> normalizeList(List<?> input) {
        ArrayList<Object> out = new ArrayList<>();
        for (Object value : input) {
            if (value instanceof Map<?, ?> child) value = normalizeMap(child);
            else if (value instanceof List<?> list) value = normalizeList(list);
            out.add(value);
        }
        return out;
    }
}
