package dev.kokoto.webchat.fabric;


/* KWC 파일 안내 / KWC file guide
 * FabricCompat는 Minecraft/loader 버전 차이를 흡수하는 Fabric compatibility shim이다.
 * FabricCompat is a Fabric compatibility shim absorbing Minecraft/loader API differences across target versions.
 *
 * reflection/method signature 분기는 정확한 target 범위에만 적용하고, 공통 runtime 코드가 버전별 API를 직접 참조하지 않게 한다.
 * Restrict reflection/signature branches to their exact target range and keep version-specific APIs out of common runtime code.
 */
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Locale;
import java.util.UUID;

/** Version bridge for Minecraft/Fabric API changes across the exact-target matrix. */
final class FabricCompat {
    private FabricCompat() {}

    static String profileName(ServerPlayer player) {
        if (player == null) return "";
        try {
            Object profile = player.getGameProfile();
            Object value = invokeNoArg(profile, "name", "getName");
            if (value != null) return String.valueOf(value);
        } catch (Throwable ignored) {}
        try { return player.getName().getString(); } catch (Throwable ignored) { return ""; }
    }

    static String worldDimension(ServerPlayer player) {
        if (player == null) return "";
        try {
            Object level = invokeNoArg(player, "serverLevel", "level", "getLevel");
            if (level == null) return "";
            Object dimension = invokeNoArg(level, "dimension");
            return dimension == null ? "" : String.valueOf(dimension);
        } catch (Throwable ignored) { return ""; }
    }

    static boolean isOperator(MinecraftServer server, ServerPlayer player) {
        if (server == null || player == null) return false;
        try {
            Object list = server.getPlayerList();
            Object profile = player.getGameProfile();
            Object nameAndId = invokeNoArg(player, "nameAndId");
            for (Method m : list.getClass().getMethods()) {
                if (!m.getName().equals("isOp") || m.getParameterCount() != 1) continue;
                Class<?> t = m.getParameterTypes()[0];
                Object arg = null;
                if (nameAndId != null && t.isInstance(nameAndId)) arg = nameAndId;
                else if (profile != null && t.isInstance(profile)) arg = profile;
                if (arg != null) return Boolean.TRUE.equals(m.invoke(list, arg));
            }
        } catch (Throwable ignored) {}
        return false;
    }

    static boolean checkPermission(ServerPlayer player, String permission, boolean fallback) {
        if (player == null || permission == null || permission.isBlank()) return fallback;
        // Minecraft 26.x / recent Fabric API permission context owner.
        try {
            Class<?> owner = Class.forName("net.fabricmc.fabric.api.permission.v1.PermissionContextOwner");
            if (owner.isInstance(player)) {
                for (Method m : owner.getMethods()) {
                    if (!m.getName().equals("checkPermission") || m.getParameterCount() != 2) continue;
                    Object id = permissionId(m.getParameterTypes()[0], permission);
                    if (id == null) continue;
                    Object second = m.getParameterTypes()[1] == boolean.class || m.getParameterTypes()[1] == Boolean.class ? fallback : null;
                    if (second != null) {
                        Object result = m.invoke(player, id, second);
                        if (result instanceof Boolean b) return b;
                    }
                }
            }
        } catch (Throwable ignored) {}

        // Widely deployed Fabric Permissions API by lucko, kept optional through reflection.
        try {
            Class<?> permissions = Class.forName("me.lucko.fabric.api.permissions.v0.Permissions");
            Object source = invokeNoArg(player, "createCommandSourceStack");
            if (source != null) {
                for (Method m : permissions.getMethods()) {
                    if (!Modifier.isStatic(m.getModifiers()) || !m.getName().equals("check")) continue;
                    Class<?>[] pt = m.getParameterTypes();
                    if (pt.length != 3 || !pt[0].isInstance(source) || pt[1] != String.class) continue;
                    Object third = pt[2] == boolean.class || pt[2] == Boolean.class ? fallback
                            : (pt[2] == int.class || pt[2] == Integer.class ? (fallback ? 4 : 0) : null);
                    if (third == null) continue;
                    Object result = m.invoke(null, source, permission, third);
                    if (result instanceof Boolean b) return b;
                }
            }
        } catch (Throwable ignored) {}
        return fallback;
    }

    private static Object permissionId(Class<?> type, String permission) {
        if (type == String.class) return permission;
        String raw = permission.trim().toLowerCase(Locale.ROOT);
        String namespace = "kwc";
        String path = raw;
        int colon = raw.indexOf(':');
        if (colon > 0) { namespace = raw.substring(0, colon); path = raw.substring(colon + 1); }
        else {
            int dot = raw.indexOf('.');
            if (dot > 0) { namespace = raw.substring(0, dot); path = raw.substring(dot + 1); }
        }
        namespace = namespace.replaceAll("[^a-z0-9_.-]", "_");
        path = path.replace('.', '/').replaceAll("[^a-z0-9_./-]", "_");
        try {
            for (String name : new String[]{"parse", "tryParse"}) {
                try {
                    Method m = type.getMethod(name, String.class);
                    if (Modifier.isStatic(m.getModifiers())) return m.invoke(null, namespace + ":" + path);
                } catch (ReflectiveOperationException ignored) {}
            }
            for (String name : new String[]{"fromNamespaceAndPath", "of"}) {
                try {
                    Method m = type.getMethod(name, String.class, String.class);
                    if (Modifier.isStatic(m.getModifiers())) return m.invoke(null, namespace, path);
                } catch (ReflectiveOperationException ignored) {}
            }
            try { Constructor<?> c = type.getConstructor(String.class, String.class); return c.newInstance(namespace, path); }
            catch (ReflectiveOperationException ignored) {}
            try { Constructor<?> c = type.getConstructor(String.class); return c.newInstance(namespace + ":" + path); }
            catch (ReflectiveOperationException ignored) {}
        } catch (Throwable ignored) {}
        return null;
    }

    static boolean dispatchConsoleCommand(MinecraftServer server, String command) {
        if (server == null || command == null || command.isBlank()) return false;
        try {
            Object commands = server.getCommands();
            Object source = server.createCommandSourceStack();
            for (String name : new String[]{"performPrefixedCommand", "performCommand"}) {
                for (Method m : commands.getClass().getMethods()) {
                    if (!m.getName().equals(name) || m.getParameterCount() != 2) continue;
                    if (!m.getParameterTypes()[0].isInstance(source) || m.getParameterTypes()[1] != String.class) continue;
                    m.invoke(commands, source, command);
                    return true;
                }
            }
        } catch (Throwable ignored) {}
        return false;
    }

    static void sendPlayerMessage(ServerPlayer player, Component message) {
        if (player == null || message == null) return;
        if (invokeCompatible(player, "sendSystemMessage", message)) return;
        if (invokeCompatible(player, "sendMessage", message, UUID.randomUUID())) return;
        invokeCompatible(player, "displayClientMessage", message, false);
    }

    static void sendSourceMessage(CommandSourceStack source, Component message) {
        if (source == null || message == null) return;
        if (invokeCompatible(source, "sendSystemMessage", message)) return;
        if (invokeCompatible(source, "sendSuccess", message, false)) return;
        // 1.20+ sendSuccess commonly takes Supplier<Component>.
        try {
            for (Method m : source.getClass().getMethods()) {
                if (!m.getName().equals("sendSuccess") || m.getParameterCount() != 2) continue;
                if (java.util.function.Supplier.class.isAssignableFrom(m.getParameterTypes()[0])) {
                    m.invoke(source, (java.util.function.Supplier<Component>) () -> message, false);
                    return;
                }
            }
        } catch (Throwable ignored) {}
        invokeCompatible(source, "sendFailure", message);
    }

    static ServerPlayer sourcePlayer(CommandSourceStack source) {
        if (source == null) return null;
        try {
            Object v = invokeNoArg(source, "getPlayer", "getPlayerOrException");
            return v instanceof ServerPlayer p ? p : null;
        } catch (Throwable ignored) { return null; }
    }

    static boolean sourceHasEntity(CommandSourceStack source) {
        if (source == null) return false;
        try { return invokeNoArg(source, "getEntity") != null; } catch (Throwable ignored) { return false; }
    }

    static String messageText(Object message) {
        if (message == null) return "";
        try {
            Object decorated = invokeNoArg(message, "decoratedContent");
            if (decorated != null) {
                Object value = invokeNoArg(decorated, "getString");
                if (value != null) return String.valueOf(value);
            }
        } catch (Throwable ignored) {}
        for (String name : new String[]{"signedContent", "content", "getMessage"}) {
            try {
                Object value = invokeNoArg(message, name);
                if (value == null) continue;
                if (value instanceof String s) return s;
                Object text = invokeNoArg(value, "getString");
                return text == null ? String.valueOf(value) : String.valueOf(text);
            } catch (Throwable ignored) {}
        }
        return String.valueOf(message);
    }

    private static Object invokeNoArg(Object target, String... names) throws Exception {
        if (target == null) return null;
        for (String name : names) {
            try { Method m = target.getClass().getMethod(name); return m.invoke(target); }
            catch (NoSuchMethodException ignored) {}
        }
        return null;
    }

    private static boolean invokeCompatible(Object target, String name, Object... args) {
        try {
            for (Method m : target.getClass().getMethods()) {
                if (!m.getName().equals(name) || m.getParameterCount() != args.length) continue;
                Class<?>[] pt = m.getParameterTypes();
                boolean ok = true;
                for (int i = 0; i < pt.length; i++) {
                    Object a = args[i];
                    if (a == null) continue;
                    Class<?> boxed = box(pt[i]);
                    if (!boxed.isInstance(a)) { ok = false; break; }
                }
                if (!ok) continue;
                m.invoke(target, args);
                return true;
            }
        } catch (Throwable ignored) {}
        return false;
    }

    private static Class<?> box(Class<?> type) {
        if (!type.isPrimitive()) return type;
        if (type == boolean.class) return Boolean.class;
        if (type == int.class) return Integer.class;
        if (type == long.class) return Long.class;
        if (type == double.class) return Double.class;
        if (type == float.class) return Float.class;
        if (type == short.class) return Short.class;
        if (type == byte.class) return Byte.class;
        if (type == char.class) return Character.class;
        return type;
    }
}
