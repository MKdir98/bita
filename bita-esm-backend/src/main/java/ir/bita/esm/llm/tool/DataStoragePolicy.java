package ir.bita.esm.llm.tool;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * BITA is a pass-through integration layer: data stays with the source and target systems and
 * only passes through. This is the technical check behind that rule — applied to what actually
 * gets executed (a template's or component's Groovy code, a service's configured values) before
 * it is stored, so a configuration that would write to a database or local file is refused by
 * the tool itself, not only by the prompt or the reviewer.
 */
public final class DataStoragePolicy {

    private static final Pattern FORBIDDEN = Pattern.compile(
            // storage targets, as a URI/driver name or a Camel endpoint that writes locally; reading
            // a file (e.g. a file:// WSDL) is not storage and is not matched
            "jdbc:|mysql|postgres(ql)?:|mongodb(\\+srv)?:|mongo:|oracle:|sqlserver:|mssql|redis(s)?:|cassandra:"
                    + "|sqlite|h2:mem|h2:file|jpa:|hibernate|\\.to\\(\\s*[\"']file:"
                    + "|FileOutputStream|FileWriter|Files\\.write",
            Pattern.CASE_INSENSITIVE);

    private DataStoragePolicy() {
    }

    /** What in {@code text} breaks the policy; empty if nothing does. */
    public static List<String> violations(String where, String text) {
        List<String> found = new ArrayList<>();
        if (text == null) {
            return found;
        }
        var m = FORBIDDEN.matcher(text);
        while (m.find()) {
            String hit = m.group();
            String entry = where + ": «" + hit + "»";
            if (!found.contains(entry)) {
                found.add(entry);
            }
        }
        return found;
    }

    public static List<String> violations(Map<String, Object> values) {
        List<String> found = new ArrayList<>();
        if (values != null) {
            values.forEach((k, v) -> found.addAll(violations(k, v == null ? null : String.valueOf(v))));
        }
        return found;
    }

    public static String message(List<String> violations) {
        return "طبق سیاست امنیتی، این پلتفرم داده را در خود ذخیره نمی‌کند (اتصال مستقیم به پایگاه‌داده یا نوشتن روی فایل ممنوع است): "
                + String.join("؛ ", violations);
    }
}
