// KWC 파일 안내 / KWC file guide
// ChatGameAutoEndHarness는 KWC Chat Event 자동 종료 수명주기와 재시작 복구를 검증하는 Java 회귀 테스트다.
// ChatGameAutoEndHarness is a Java regression test for KWC Chat Event automatic-end lifecycle and restart recovery.
// 제품 런타임과 동일한 ChatGameManager API를 사용해 용량/응답수/시각 조건의 호환성을 확인한다.
// It uses the same ChatGameManager API as production to verify capacity, response-count, and timed conditions.

import dev.kokoto.webchat.ChatGameManager;
import java.nio.file.*;
import java.util.*;

public final class ChatGameAutoEndHarness {
    private static int assertions;
    private static void check(boolean ok, String message) {
        assertions++;
        if (!ok) throw new AssertionError(message);
    }
    private static String id(ChatGameManager.Result r) { return String.valueOf(r.game().get("id")); }
    private static String status(ChatGameManager.Result r) { return String.valueOf(r.game().get("status")); }
    private static String reason(ChatGameManager.Result r) { return String.valueOf(r.game().get("completionReason")); }

    public static void main(String[] args) throws Exception {
        Path root = Files.createTempDirectory("kwc-auto-end-");
        ChatGameManager games = new ChatGameManager(root);

        ChatGameManager.Result fc = games.create("firstcome", "FC", 99, 2, "admin", true, false, 0L);
        check(fc.ok(), "firstcome create");
        check(Boolean.TRUE.equals(fc.game().get("autoEndOnCapacity")), "firstcome capacity auto-end forced on");
        String fcId = id(fc);
        games.join(fcId, "u1", "one", "One");
        ChatGameManager.Result fcDone = games.join(fcId, "u2", "two", "Two");
        check("completed".equals(status(fcDone)), "firstcome completes at winner count");
        check("capacity".equals(reason(fcDone)), "firstcome completion reason capacity");

        ChatGameManager.Result lotteryManual = games.create("lottery", "Manual lottery", 2, 1, "admin", true, false, 0L);
        String lm = id(lotteryManual);
        games.join(lm, "l1", "l1", "L1");
        ChatGameManager.Result lmFull = games.join(lm, "l2", "l2", "L2");
        check("ready".equals(status(lmFull)), "lottery without capacity auto-end remains ready");

        ChatGameManager.Result lotteryAuto = games.create("lottery", "Auto lottery", 2, 1, "admin", true, true, 0L);
        String la = id(lotteryAuto);
        games.join(la, "a1", "a1", "A1");
        ChatGameManager.Result laDone = games.join(la, "a2", "a2", "A2");
        check("completed".equals(status(laDone)), "lottery capacity auto-end completes");
        check(((List<?>)laDone.game().get("winners")).size() == 1, "lottery capacity auto-end draws winner");
        check("capacity".equals(reason(laDone)), "lottery completion reason capacity");

        ChatGameManager.Result poll = games.createPoll("Poll", "A|B", "admin", true, 2, 0L);
        String pollId = id(poll);
        games.vote(pollId, "p1", "p1", "P1", 1);
        ChatGameManager.Result pollDone = games.vote(pollId, "p2", "p2", "P2", 2);
        check("completed".equals(status(pollDone)), "poll response threshold auto-end completes");
        check("response_count".equals(reason(pollDone)), "poll completion reason response_count");
        check(((Number)pollDone.game().get("voteCount")).intValue() == 2, "poll counts unique voters");

        ChatGameManager.Result recruitment = games.createRecruitment("Recruit", "Tank:1|Heal:1", "admin", true, true, 0L);
        String recruitmentId = id(recruitment);
        games.applyRecruitment(recruitmentId, "r1", "r1", "R1", "Tank");
        ChatGameManager.Result recruitmentDone = games.applyRecruitment(recruitmentId, "r2", "r2", "R2", "Heal");
        check("completed".equals(status(recruitmentDone)), "recruitment full auto-end completes");
        check("capacity".equals(reason(recruitmentDone)), "recruitment completion reason capacity");

        long dueAt = System.currentTimeMillis() + 60_000L;
        ChatGameManager.Result timed = games.createPoll("Timed", "Yes|No", "admin", true, 0, dueAt);
        String timedId = id(timed);
        check(((Number)timed.game().get("autoEndAt")).longValue() == dueAt, "time condition persisted in snapshot");
        List<ChatGameManager.Result> due = games.finishDue(dueAt + 1L);
        check(due.stream().anyMatch(r -> timedId.equals(String.valueOf(r.game().get("id")))), "time lifecycle returns due event");
        ChatGameManager.Result timedDone = games.snapshot(timedId, "");
        check("completed".equals(status(timedDone)), "time auto-end completes");
        check("time".equals(reason(timedDone)), "time completion reason");

        ChatGameManager restored = new ChatGameManager(root);
        ChatGameManager.Result restoredLottery = restored.snapshot(la, "");
        check(Boolean.TRUE.equals(restoredLottery.game().get("autoEndOnCapacity")), "capacity policy survives restart");
        ChatGameManager.Result restoredTimed = restored.snapshot(timedId, "");
        check(((Number)restoredTimed.game().get("autoEndAt")).longValue() == dueAt, "time policy survives restart");
        check("time".equals(String.valueOf(restoredTimed.game().get("completionReason"))), "completion reason survives restart");

        ChatGameManager.Result badTime = games.createPoll("Bad time", "A|B", "admin", true, 0, System.currentTimeMillis() - 1000L);
        check(!badTime.ok() && "invalid_auto_end_time".equals(badTime.error()), "past automatic end time rejected");
        ChatGameManager.Result badCount = games.createPoll("Bad count", "A|B", "admin", true, 501, 0L);
        check(!badCount.ok() && "invalid_auto_end_count".equals(badCount.error()), "oversized response threshold rejected");
        ChatGameManager.Result policyLottery = games.create("lottery", "Policy lottery", 3, 1, "admin", true, false, 0L);
        String policyLotteryId = id(policyLottery);
        ChatGameManager.Result policyLotteryUpdated = games.setAutoEnd(policyLotteryId, true, 0, 0L);
        check(policyLotteryUpdated.ok() && Boolean.TRUE.equals(policyLotteryUpdated.game().get("autoEndOnCapacity")), "runtime capacity policy update persisted");

        ChatGameManager.Result policyPoll = games.createPoll("Policy poll", "A|B", "admin", true, 0, 0L);
        String policyPollId = id(policyPoll);
        ChatGameManager.Result policyPollUpdated = games.setAutoEnd(policyPollId, false, 2, 0L);
        check(policyPollUpdated.ok() && ((Number)policyPollUpdated.game().get("autoEndResponseCount")).intValue() == 2, "runtime poll response policy update persisted");
        ChatGameManager.Result policyScope = games.setRelayAnnouncements(policyPollId, false);
        check(policyScope.ok() && Boolean.FALSE.equals(policyScope.game().get("relayAnnouncements")), "runtime relay announcement policy update persisted");


        System.out.println("KWC_531_AUTO_END_JAVA_PASS assertions=" + assertions);
    }
}
