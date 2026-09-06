package dev.kokoto.webchat;

import java.util.List;
import java.util.Set;

public final class MentionMatchingHarness {
    private static int assertions = 0;

    private static void check(boolean condition, String message) {
        assertions++;
        if (!condition) throw new AssertionError(message);
    }

    private static MentionMatcher.Candidate c(String uuid, String name) {
        return new MentionMatcher.Candidate(uuid, name);
    }

    public static void main(String[] args) {
        List<MentionMatcher.Candidate> candidates = List.of(
                c("u-honoka", "쿠로베 호노카"),
                c("u-nanoka", "쿠로베 나노카"),
                c("u-short", "쿠로베"),
                c("u-team1", "개발팀"),
                c("u-team2", "개발팀"),
                c("u-real", "RealUser"),
                c("u-format", "<gold>표시 이름</gold>"),
                c("u-fullwidth", "ＴＥＡＭ")
        );

        Set<String> none = MentionMatcher.resolve("쿠로베 호노카가 말했다", candidates);
        check(none.isEmpty(), "unprefixed name must not mention anyone");

        Set<String> honoka = MentionMatcher.resolve("@쿠로베 호노카 안녕", candidates);
        check(honoka.equals(Set.of("u-honoka")), "longest full name should beat shorter shared prefix");

        Set<String> nanoka = MentionMatcher.resolve("@쿠로베 나노카도 와", candidates);
        check(nanoka.equals(Set.of("u-nanoka")), "suffix text must not require a trailing boundary");

        Set<String> shortOnly = MentionMatcher.resolve("@쿠로베", candidates);
        check(shortOnly.equals(Set.of("u-short")), "an exact shorter registered name remains callable");

        Set<String> team = MentionMatcher.resolve("@개발팀 확인해주세요", candidates);
        check(team.equals(Set.of("u-team1", "u-team2")), "identical display names should mention all matching accounts");

        Set<String> multiple = MentionMatcher.resolve("@RealUser 그리고 @개발팀", candidates);
        check(multiple.equals(Set.of("u-real", "u-team1", "u-team2")), "multiple @ positions should union recipients");

        Set<String> formatted = MentionMatcher.resolve("@표시 이름확인", candidates);
        check(formatted.equals(Set.of("u-format")), "formatted display name should match its visible plain text");

        Set<String> nfkc = MentionMatcher.resolve("@team 모여", candidates);
        check(nfkc.equals(Set.of("u-fullwidth")), "NFKC/case normalization should apply consistently");

        System.out.println("MENTION_MATCHING_HARNESS_PASS assertions=" + assertions);
    }
}
