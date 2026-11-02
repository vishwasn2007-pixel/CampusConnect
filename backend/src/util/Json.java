package util;

import java.util.List;
import model.Jsonable;

/** Tiny hand-written JSON builder so the project needs no external libraries. */
public final class Json {

    private Json() {
    }

    /** Json.obj("a", 1, "b", "x") -> {"a":1,"b":"x"} */
    public static String obj(Object... keyValues) {
        StringBuilder sb = new StringBuilder("{");
        for (int i = 0; i + 1 < keyValues.length; i += 2) {
            if (i > 0) {
                sb.append(',');
            }
            sb.append('"').append(esc(String.valueOf(keyValues[i]))).append("\":");
            sb.append(value(keyValues[i + 1]));
        }
        return sb.append('}').toString();
    }

    /** Turns a list of Jsonable objects into a JSON array (runtime polymorphism). */
    public static String array(List<? extends Jsonable> items) {
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < items.size(); i++) {
            if (i > 0) {
                sb.append(',');
            }
            sb.append(items.get(i).toJson());
        }
        return sb.append(']').toString();
    }

    /** Wraps ready-made JSON text so it can be nested inside obj(). */
    public static Jsonable raw(String json) {
        return () -> json;
    }

    public static String esc(String s) {
        if (s == null) {
            return "";
        }
        StringBuilder sb = new StringBuilder(s.length() + 8);
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '"':
                    sb.append("\\\"");
                    break;
                case '\\':
                    sb.append("\\\\");
                    break;
                case '\n':
                    sb.append("\\n");
                    break;
                case '\r':
                    sb.append("\\r");
                    break;
                case '\t':
                    sb.append("\\t");
                    break;
                default:
                    if (c < 0x20) {
                        sb.append(String.format("\\u%04x", (int) c));
                    } else {
                        sb.append(c);
                    }
            }
        }
        return sb.toString();
    }

    private static String value(Object v) {
        if (v == null) {
            return "null";
        }
        if (v instanceof Jsonable j) {
            return j.toJson();
        }
        if (v instanceof Number || v instanceof Boolean) {
            return v.toString();
        }
        return "\"" + esc(v.toString()) + "\"";
    }
}
