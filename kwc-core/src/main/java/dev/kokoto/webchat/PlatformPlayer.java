package dev.kokoto.webchat;

import java.util.UUID;

/** Lightweight, loader-neutral online-player snapshot. */
public record PlatformPlayer(UUID uuid, String name, String displayName, boolean operator) {
}
