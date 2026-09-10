package dev.kokoto.webchat.fabric;


/* KWC 파일 안내 / KWC file guide
 * FabricYamlConfiguration는 Fabric 런타임에서 KWC core 기능을 해당 loader/Minecraft API에 연결한다.
 * FabricYamlConfiguration connects KWC core behavior to the concrete Fabric/Minecraft runtime APIs.
 *
 * 동일 기능의 다른 loader 구현과 의미를 맞추되 API 버전 차이는 이 플랫폼 계층 안에서만 처리한다.
 * Keep semantics aligned with other loaders while containing API-version differences within this platform layer.
 */
import dev.kokoto.webchat.RelayConfigShapeValidator;
import org.yaml.snakeyaml.DumperOptions;
import org.yaml.snakeyaml.Yaml;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.*;

/** Small SnakeYAML-backed configuration used by the Fabric platform module. */
public final class FabricYamlConfiguration extends FabricYamlSection {
    public FabricYamlConfiguration() {
        this(new LinkedHashMap<>());
    }

    private FabricYamlConfiguration(Map<String,Object> root) {
        super(root, root, "");
    }

    public static FabricYamlConfiguration empty() {
        return new FabricYamlConfiguration(new LinkedHashMap<>());
    }

    public static FabricYamlConfiguration loadConfiguration(File file) {
        if (file == null || !file.isFile()) return empty();
        try (Reader reader = Files.newBufferedReader(file.toPath(), StandardCharsets.UTF_8)) {
            return loadConfiguration(reader);
        } catch (Exception ignored) {
            return empty();
        }
    }

    public static FabricYamlConfiguration loadStrict(File file) throws IOException {
        if (file == null || !file.isFile()) throw new FileNotFoundException(String.valueOf(file));
        try (Reader reader = Files.newBufferedReader(file.toPath(), StandardCharsets.UTF_8)) {
            Object loaded = new Yaml().load(reader);
            if (loaded == null) return empty();
            if (!(loaded instanceof Map<?, ?> map)) throw new IOException("YAML root must be a mapping");
            FabricYamlConfiguration config = new FabricYamlConfiguration(normalizeMap(map));
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
    public static FabricYamlConfiguration loadConfiguration(Reader reader) {
        if (reader == null) return empty();
        try {
            Object loaded = new Yaml().load(reader);
            if (loaded instanceof Map<?, ?> map) {
                return new FabricYamlConfiguration(normalizeMap((Map<?,?>) map));
            }
        } catch (Exception ignored) {
        }
        return empty();
    }

    /** Dot-path leaf snapshot used by the loader-neutral KWC config migration engine. */
    public Map<String,Object> flatLeafValues() {
        LinkedHashMap<String,Object> out = new LinkedHashMap<>();
        for (String path : getKeys(true)) {
            FabricYamlSection child = getConfigurationSection(path);
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
