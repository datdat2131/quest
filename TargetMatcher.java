package vn.astrax.quest.util;

import java.util.List;
import java.util.Locale;

/** So khop muc tieu: danh sach rong / ANY / * = tat ca; ho tro ky tu * (vd *_ORE, *_LOG). */
public final class TargetMatcher {
    private TargetMatcher() {
    }

    public static String normalize(String s) {
        if (s == null) return "";
        String v = s.trim().toUpperCase(Locale.ROOT);
        if (v.startsWith("MINECRAFT:")) v = v.substring(10);
        return v;
    }

    public static boolean matches(List<String> targets, String value) {
        if (targets == null || targets.isEmpty()) return true;
        String v = normalize(value);
        for (String t : targets) {
            String p = normalize(t);
            if (p.equals("ANY") || p.equals("*")) return true;
            if (glob(p, v)) return true;
        }
        return false;
    }

    static boolean glob(String p, String s) {
        int pi = 0, si = 0, star = -1, mark = 0;
        while (si < s.length()) {
            if (pi < p.length() && p.charAt(pi) == s.charAt(si)) {
                pi++;
                si++;
            } else if (pi < p.length() && p.charAt(pi) == '*') {
                star = pi++;
                mark = si;
            } else if (star != -1) {
                pi = star + 1;
                si = ++mark;
            } else {
                return false;
            }
        }
        while (pi < p.length() && p.charAt(pi) == '*') pi++;
        return pi == p.length();
    }
}
