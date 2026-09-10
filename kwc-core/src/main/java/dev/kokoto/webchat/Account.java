package dev.kokoto.webchat;


/* KWC 파일 안내 / KWC file guide
 * 인증된 KWC 계정의 식별자·표시 정보·전역 역할을 전달하는 도메인 모델이다.
 * Domain model carrying identity, display information, and global role for an authenticated KWC account.
 *
 * 이 타입은 가능한 한 데이터 의미만 담고, 인증·권한·영속화 같은 정책은 Store/Server 계층에서 처리한다.
 * This type should primarily carry data; authentication, authorization, and persistence policy belong in Store/Server layers.
 */
public class Account {
    public String id;
    public String username;
    public String uuid;
    public String lastDisplayName;
    public Role role = Role.USER;
    public String passwordHash;
    public boolean local;
    public long createdAt;
    public long lastLogin;

    public boolean hasPassword() {
        return passwordHash != null && !passwordHash.isBlank();
    }

    public String safeUsername() {
        return username == null ? id : username;
    }
}
