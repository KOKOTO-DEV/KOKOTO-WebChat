package dev.kokoto.webchat.neoforge;

import java.util.*;

/** Minimal YAML section wrapper backed by nested maps. */
public class NeoForgeYamlSection {
    protected final Map<String, Object> root;
    protected final Map<String, Object> section;
    protected final String sectionName;

    NeoForgeYamlSection(Map<String, Object> root, Map<String, Object> section, String sectionName) {
        this.root = root;
        this.section = section;
        this.sectionName = sectionName == null ? "" : sectionName;
    }

    public String getName() { return sectionName; }

    public Set<String> getKeys(boolean deep) {
        if (!deep) return new LinkedHashSet<>(section.keySet());
        LinkedHashSet<String> out = new LinkedHashSet<>();
        collectKeys(section, "", out);
        return out;
    }

    @SuppressWarnings("unchecked")
    private static void collectKeys(Map<String, Object> map, String prefix, Set<String> out) {
        for (Map.Entry<String, Object> e : map.entrySet()) {
            String key = prefix.isEmpty() ? e.getKey() : prefix + "." + e.getKey();
            out.add(key);
            if (e.getValue() instanceof Map<?, ?> child) collectKeys((Map<String,Object>) child, key, out);
        }
    }

    public Object get(String path) { return resolve(section, path); }
    public boolean isSet(String path) { return get(path) != null; }
    public boolean contains(String path) { return isSet(path); }

    public String getString(String path) { return getString(path, null); }
    public String getString(String path, String def) {
        Object v = get(path);
        return v == null ? def : String.valueOf(v);
    }

    public boolean getBoolean(String path) { return getBoolean(path, false); }
    public boolean getBoolean(String path, boolean def) {
        Object v = get(path);
        if (v instanceof Boolean b) return b;
        if (v instanceof Number n) return n.intValue() != 0;
        if (v != null) {
            String s = String.valueOf(v).trim();
            if (s.equalsIgnoreCase("true") || s.equalsIgnoreCase("yes") || s.equals("1")) return true;
            if (s.equalsIgnoreCase("false") || s.equalsIgnoreCase("no") || s.equals("0")) return false;
        }
        return def;
    }

    public int getInt(String path) { return getInt(path, 0); }
    public int getInt(String path, int def) {
        Object v = get(path);
        if (v instanceof Number n) return n.intValue();
        try { return v == null ? def : Integer.parseInt(String.valueOf(v).trim()); } catch (Exception ignored) { return def; }
    }

    public long getLong(String path) { return getLong(path, 0L); }
    public long getLong(String path, long def) {
        Object v = get(path);
        if (v instanceof Number n) return n.longValue();
        try { return v == null ? def : Long.parseLong(String.valueOf(v).trim()); } catch (Exception ignored) { return def; }
    }

    public double getDouble(String path) { return getDouble(path, 0.0); }
    public double getDouble(String path, double def) {
        Object v = get(path);
        if (v instanceof Number n) return n.doubleValue();
        try { return v == null ? def : Double.parseDouble(String.valueOf(v).trim()); } catch (Exception ignored) { return def; }
    }

    public List<String> getStringList(String path) {
        Object v = get(path);
        if (!(v instanceof List<?> list)) return new ArrayList<>();
        ArrayList<String> out = new ArrayList<>();
        for (Object o : list) if (o != null) out.add(String.valueOf(o));
        return out;
    }

    @SuppressWarnings("unchecked")
    public List<Map<?, ?>> getMapList(String path) {
        Object v = get(path);
        if (!(v instanceof List<?> list)) return new ArrayList<>();
        ArrayList<Map<?, ?>> out = new ArrayList<>();
        for (Object o : list) if (o instanceof Map<?, ?> m) out.add(m);
        return out;
    }

    public List<?> getList(String path) {
        Object v = get(path);
        return v instanceof List<?> l ? l : null;
    }

    @SuppressWarnings("unchecked")
    public NeoForgeYamlSection getConfigurationSection(String path) {
        Object v = get(path);
        if (!(v instanceof Map<?, ?> map)) return null;
        String name = path;
        int dot = name.lastIndexOf('.');
        if (dot >= 0) name = name.substring(dot + 1);
        return new NeoForgeYamlSection(root, (Map<String,Object>) map, name);
    }

    @SuppressWarnings("unchecked")
    public void set(String path, Object value) {
        if (path == null || path.isBlank()) return;
        String[] parts = path.split("\\.");
        Map<String,Object> cur = section;
        for (int i = 0; i < parts.length - 1; i++) {
            Object existing = cur.get(parts[i]);
            if (!(existing instanceof Map<?, ?>)) {
                LinkedHashMap<String,Object> created = new LinkedHashMap<>();
                cur.put(parts[i], created);
                cur = created;
            } else {
                cur = (Map<String,Object>) existing;
            }
        }
        if (value == null) cur.remove(parts[parts.length - 1]);
        else cur.put(parts[parts.length - 1], value);
    }

    @SuppressWarnings("unchecked")
    protected static Object resolve(Map<String,Object> base, String path) {
        if (path == null || path.isBlank()) return base;
        Object cur = base;
        for (String part : path.split("\\.")) {
            if (!(cur instanceof Map<?, ?> map)) return null;
            cur = ((Map<String,Object>) map).get(part);
            if (cur == null) return null;
        }
        return cur;
    }
}
