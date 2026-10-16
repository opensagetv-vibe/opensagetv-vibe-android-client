package opensagetv.vibe.miniclient.android.diagnostics;

import java.util.*;

/** Tiny bounded-report JSON encoder shared by Android and real JVM tests. */
public final class DiagnosticJson {
    private DiagnosticJson() { }
    public static String encode(Object value) {
        if (value == null) return "null";
        if (value instanceof Boolean) return value.toString();
        if (value instanceof Number) {
            double number = ((Number)value).doubleValue();
            return Double.isNaN(number) || Double.isInfinite(number) ? "null" : value.toString();
        }
        if (value instanceof Map) {
            StringBuilder text = new StringBuilder("{");
            for (Object entryObject : ((Map<?,?>)value).entrySet()) {
                Map.Entry<?,?> entry = (Map.Entry<?,?>)entryObject;
                if (text.length() > 1) text.append(',');
                text.append(encode(String.valueOf(entry.getKey()))).append(':').append(encode(entry.getValue()));
            }
            return text.append('}').toString();
        }
        if (value instanceof Iterable) {
            StringBuilder text = new StringBuilder("[");
            for (Object item : (Iterable<?>)value) { if (text.length() > 1) text.append(','); text.append(encode(item)); }
            return text.append(']').toString();
        }
        StringBuilder text = new StringBuilder("\"");
        for (char c : value.toString().toCharArray()) {
            switch (c) {
                case '\\': text.append("\\\\"); break;
                case '"': text.append("\\\""); break;
                case '\n': text.append("\\n"); break;
                case '\r': text.append("\\r"); break;
                case '\t': text.append("\\t"); break;
                default: if (c < 32) text.append(String.format(Locale.ROOT,"\\u%04x",(int)c)); else text.append(c);
            }
        }
        return text.append('"').toString();
    }
}
