package vn.astrax.quest.util;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Chuyen &-code va &#RRGGBB sang Component / chuoi legacy. */
public final class Text {
    private static final LegacyComponentSerializer SER = LegacyComponentSerializer.builder()
            .character('&').hexColors().build();
    private static final Pattern HEX = Pattern.compile("&#([A-Fa-f0-9]{6})");
    private static final Pattern CODE = Pattern.compile("&([0-9a-fk-orA-FK-OR])");
    private static final Pattern STRIP = Pattern.compile("(?i)&#[0-9a-f]{6}|[&\u00a7][0-9a-fk-orx]");

    private Text() {
    }

    public static Component color(String s) {
        if (s == null) return Component.empty();
        return SER.deserialize(s).decorationIfAbsent(TextDecoration.ITALIC, TextDecoration.State.FALSE);
    }

    public static List<Component> colorAll(List<String> lines) {
        List<Component> out = new ArrayList<>(lines.size());
        for (String l : lines) out.add(color(l));
        return out;
    }

    /** Chuoi dung ma mau section (cho placeholder tra ve plugin chat). */
    public static String legacy(String s) {
        if (s == null) return "";
        Matcher m = HEX.matcher(s);
        StringBuilder sb = new StringBuilder();
        while (m.find()) {
            StringBuilder r = new StringBuilder("\u00a7x");
            for (char ch : m.group(1).toCharArray()) r.append('\u00a7').append(ch);
            m.appendReplacement(sb, Matcher.quoteReplacement(r.toString()));
        }
        m.appendTail(sb);
        return CODE.matcher(sb.toString()).replaceAll("\u00a7$1");
    }

    public static String strip(String s) {
        return s == null ? "" : STRIP.matcher(s).replaceAll("");
    }

    /** replace("{a}", "x", "{b}") theo cap key/value: kv = "a", 1, "b", 2. */
    public static String replace(String s, Object... kv) {
        if (s == null) return "";
        for (int i = 0; i + 1 < kv.length; i += 2) {
            s = s.replace("{" + kv[i] + "}", String.valueOf(kv[i + 1]));
        }
        return s;
    }

    public static List<String> replaceAll(List<String> lines, Object... kv) {
        List<String> out = new ArrayList<>(lines.size());
        for (String l : lines) out.add(replace(l, kv));
        return out;
    }
}
