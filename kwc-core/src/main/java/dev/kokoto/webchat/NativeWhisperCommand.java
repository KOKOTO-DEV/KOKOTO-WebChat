package dev.kokoto.webchat;


/* KWC 파일 안내 / KWC file guide
 * Minecraft native whisper/tell 명령 형식을 해석해 KWC DM 흐름과 연결하는 parser/adapter다.
 * Parser/adapter translating Minecraft native whisper/tell commands into the KWC DM flow.
 *
 * 플랫폼/버전에 따라 명령 alias와 quoting 규칙이 다를 수 있으므로 parsing 실패 시 원래 게임 동작을 불필요하게 막지 않도록 한다.
 * Command aliases and quoting can vary by platform/version; parsing failure should not unnecessarily block native game behavior.
 */
import java.util.Locale;

/**
 * Parsed Minecraft-style private-message command.  This class intentionally
 * contains no Bukkit dependencies so command routing can be validated without
 * starting a Minecraft server.
 */
final class NativeWhisperCommand {
    final String commandRoot;
    final String target;
    final String message;

    private NativeWhisperCommand(String commandRoot, String target, String message) {
        this.commandRoot = commandRoot;
        this.target = target;
        this.message = message;
    }

    static NativeWhisperCommand parse(String raw) {
        String text = String.valueOf(raw == null ? "" : raw).trim();
        if (!text.startsWith("/")) return null;
        String[] parts = text.substring(1).split("\\s+", 3);
        if (parts.length < 3) return null;

        String commandRoot = parts[0];
        String root = commandRoot.toLowerCase(Locale.ROOT);
        int colon = root.indexOf(':');
        if (colon >= 0 && colon + 1 < root.length()) root = root.substring(colon + 1);
        if (!(root.equals("w") || root.equals("whisper") || root.equals("msg") || root.equals("tell")
                || root.equals("m") || root.equals("pm") || root.equals("message") || root.equals("t"))) {
            return null;
        }

        String target = parts[1].trim();
        String message = parts[2].trim();
        if (target.isBlank() || message.isBlank()) return null;
        return new NativeWhisperCommand(commandRoot, target, message);
    }

    String remoteServerId() {
        RemotePlayerRef direct = RemotePlayerRef.parse(target);
        if (direct != null) return direct.serverId;

        int at = target.lastIndexOf('@');
        if (at <= 0 || at >= target.length() - 1) return "";
        return RemotePlayerRef.normalizeServerId(target.substring(at + 1));
    }

    String targetName() {
        int at = target.lastIndexOf('@');
        if (at <= 0 || at >= target.length() - 1) return target;
        return target.substring(0, at).trim();
    }

    String asKwcDirectMessage() {
        return "/kchat dm " + target + " " + message;
    }

    String asLocalWhisper() {
        String name = targetName();
        if (name.isBlank()) return "";
        return "/" + commandRoot + " " + name + " " + message;
    }

    String routedCommand(String localServerId) {
        String targetServerId = remoteServerId();
        if (targetServerId.isBlank()) return null;
        String local = RemotePlayerRef.normalizeServerId(localServerId);
        if (!local.isBlank() && local.equals(targetServerId)) {
            String localCommand = asLocalWhisper();
            return localCommand.isBlank() ? null : localCommand;
        }
        return asKwcDirectMessage();
    }
}
