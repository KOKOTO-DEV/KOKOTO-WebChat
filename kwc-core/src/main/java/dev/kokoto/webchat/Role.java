package dev.kokoto.webchat;


/* KWC 파일 안내 / KWC file guide
 * KWC 전역 계정 역할 enum이다. 그룹방의 owner/admin/member 역할과는 별개다.
 * Enum for global KWC account roles; separate from room-local owner/admin/member roles.
 *
 * 이 타입은 가능한 한 데이터 의미만 담고, 인증·권한·영속화 같은 정책은 Store/Server 계층에서 처리한다.
 * This type should primarily carry data; authentication, authorization, and persistence policy belong in Store/Server layers.
 */
public enum Role {
    GUEST(0),
    USER(1),
    MODERATOR(2),
    ADMIN(3);

    private final int level;

    Role(int level) {
        this.level = level;
    }

    public boolean atLeast(Role other) {
        return this.level >= other.level;
    }

    public static Role fromString(String value, Role fallback) {
        if (value == null) return fallback;
        try {
            return Role.valueOf(value.trim().toUpperCase());
        } catch (IllegalArgumentException ex) {
            return fallback;
        }
    }
}
