package dev.kokoto.webchat;

/* KWC 파일 안내 / KWC file guide
 * CaptchaModesHarness는 guest CAPTCHA의 off/math/text/mixed 모드와 검증/만료 계약을 검증한다.
 * CaptchaModesHarness verifies guest CAPTCHA off/math/text/mixed modes plus verification and expiry contracts.
 */
public final class CaptchaModesHarness {
    private static int assertions;

    public static void main(String[] args) throws Exception {
        CaptchaManager manager = new CaptchaManager();
        check(!manager.enabled("off"), "off disabled");
        check(manager.enabled("math"), "math enabled");
        check(manager.enabled("text"), "text enabled");
        check(manager.enabled("mixed"), "mixed enabled");
        check(!manager.enabled("unknown"), "unknown disabled");

        CaptchaManager.Captcha math = manager.issue("math", 120, "normal");
        check("math".equals(math.type), "math type");
        check(math.question.matches("[0-9]+ [+-×÷] [0-9]+ = \\?"), "math question shape");
        check(!math.question.startsWith("Solve:"), "math question has no language-dependent Solve prefix");
        String mathAnswer = Integer.toString(solveMath(math.question));
        check(manager.verify(math.id, mathAnswer), "math verifies");
        check(!manager.verify(math.id, mathAnswer), "captcha one-time use");

        for (String complexity : new String[]{"easy", "normal", "hard"}) {
            for (int i = 0; i < 24; i++) {
                CaptchaManager.Captcha challenge = manager.issue("math", 120, complexity);
                check(challenge.question.matches("[0-9]+ [+-×÷] [0-9]+ = \\?"), complexity + " math shape");
                String[] cparts = challenge.question.split(" ");
                int a = Integer.parseInt(cparts[0]);
                int b = Integer.parseInt(cparts[2]);
                String op = cparts[1];
                if ("easy".equals(complexity)) {
                    check(a >= 1 && a <= 9 && b >= 1 && b <= 9, "easy operands are single digit");
                    check("+".equals(op) || "-".equals(op), "easy operators");
                    if ("-".equals(op)) check(a >= b, "easy subtraction nonnegative");
                } else if ("normal".equals(complexity)) {
                    check("+".equals(op) || "-".equals(op) || "×".equals(op), "normal operators");
                } else {
                    check("+".equals(op) || "-".equals(op) || "×".equals(op) || "÷".equals(op), "hard operators");
                    if ("÷".equals(op)) check(a % b == 0, "hard division has integer result");
                }
                check(manager.verify(challenge.id, Integer.toString(solveMath(challenge.question))), complexity + " math verifies");
            }
        }

        CaptchaManager.Captcha text = manager.issue("text", 120);
        check("text".equals(text.type), "text type");
        check(text.question.matches("[ABCDEFGHJKLMNPQRSTUVWXYZ23456789]{6}"), "text alphabet and length");
        check(manager.verify(text.id, text.question.toLowerCase()), "text verify is case-insensitive");

        for (int i = 0; i < 32; i++) {
            CaptchaManager.Captcha mixed = manager.issue("mixed", 120);
            check("math".equals(mixed.type) || "text".equals(mixed.type), "mixed issues supported type");
        }

        CaptchaManager.Captcha expired = manager.issue("text", 0);
        Thread.sleep(3L);
        check(!manager.verify(expired.id, expired.question), "zero-second challenge expires");

        System.out.println("CAPTCHA_MODES_PASS assertions=" + assertions);
    }

    private static int solveMath(String question) {
        String[] parts = question.split(" ");
        int a = Integer.parseInt(parts[0]);
        int b = Integer.parseInt(parts[2]);
        return switch (parts[1]) {
            case "+" -> a + b;
            case "-" -> a - b;
            case "×" -> a * b;
            case "÷" -> a / b;
            default -> throw new IllegalArgumentException("unknown operator: " + parts[1]);
        };
    }

    private static void check(boolean condition, String label) {
        assertions++;
        if (!condition) throw new AssertionError(label);
    }
}
