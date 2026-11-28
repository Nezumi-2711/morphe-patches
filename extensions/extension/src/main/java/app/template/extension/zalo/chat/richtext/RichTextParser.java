package app.template.extension.zalo.chat.richtext;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import app.template.extension.zalo.chat.richtext.RichTextRun.Kind;

/**
 * Reads the {@code params} string of a received rtf text message back into {@link RichTextRun}s, the
 * inverse of {@link RichTextSerializer}. Follows Zalo's own receiver: an entry applies to
 * {@code [start, start + len)} of the message text, needs {@code start < text length}, and its end is
 * clamped to the text. Tokens:
 * {@code b}, {@code i}, {@code u}, {@code s}, {@code f_<size>}, {@code c_<rrggbb>},
 * {@code lst_<1|2>} and {@code ind_<level*10>}. Unknown tokens and malformed entries are skipped.
 *
 * <p>The result is in {@link RichTextRuns#canonical} form. Pure Java with its own small JSON reader
 * (no org.json), so it runs in plain JVM unit tests.
 */
public final class RichTextParser {
    private RichTextParser() { }

    /** Runs for {@code text} from {@code params}; empty when nothing usable is found. */
    public static List<RichTextRun> parse(String params, String text) {
        List<RichTextRun> runs = new ArrayList<>();
        if (params == null || text == null || text.isEmpty()) return runs;
        Object root;
        try {
            root = new JsonReader(params).readDocument();
        } catch (IllegalArgumentException malformed) {
            return runs;
        }
        if (!(root instanceof Map)) return runs;
        Object styles = ((Map<?, ?>) root).get("styles");
        if (!(styles instanceof List)) return runs;
        int length = text.length();
        for (Object item : (List<?>) styles) {
            if (!(item instanceof Map)) continue;
            Map<?, ?> entry = (Map<?, ?>) item;
            Long start = integer(entry.get("start"));
            Long len = integer(entry.get("len"));
            Object tokens = entry.get("st");
            if (start == null || len == null || !(tokens instanceof String)) continue;
            if (start < 0 || start >= length || len <= 0) continue;
            int from = (int) (long) start;
            int to = (int) Math.min(length, start + len);
            for (String token : ((String) tokens).split(",")) addToken(runs, token.trim(), from, to);
        }
        return RichTextRuns.canonical(text, runs);
    }

    private static void addToken(List<RichTextRun> runs, String token, int from, int to) {
        switch (token) {
            case "b": runs.add(new RichTextRun(from, to, Kind.BOLD)); return;
            case "i": runs.add(new RichTextRun(from, to, Kind.ITALIC)); return;
            case "u": runs.add(new RichTextRun(from, to, Kind.UNDERLINE)); return;
            case "s": runs.add(new RichTextRun(from, to, Kind.STRIKE)); return;
            default: break;
        }
        if (token.startsWith("f_")) {
            int size = number(token.substring(2));
            if (size > 0) runs.add(new RichTextRun(from, to, Kind.SIZE, size));
        } else if (token.startsWith("c_")) {
            String hex = token.substring(2);
            if (hex.startsWith("#")) hex = hex.substring(1);
            if (hex.matches("[0-9a-fA-F]{6}")) runs.add(new RichTextRun(from, to, Kind.COLOR, Integer.parseInt(hex, 16)));
        } else if (token.startsWith("lst_")) {
            int type = number(token.substring(4));
            if (type == RichTextRun.LIST_BULLET || type == RichTextRun.LIST_NUMBERED) {
                runs.add(new RichTextRun(from, to, Kind.LIST, type));
            }
        } else if (token.startsWith("ind_")) {
            int margin = number(token.substring(4));
            if (margin > 0) {
                int level = Math.max(1, Math.min(RichTextRanges.MAX_INDENT, Math.round(margin / 10f)));
                runs.add(new RichTextRun(from, to, Kind.INDENT, level));
            }
        }
    }

    /** A non-negative decimal int, or -1. */
    private static int number(String digits) {
        if (digits.isEmpty() || digits.length() > 6) return -1;
        for (int index = 0; index < digits.length(); index++) {
            if (digits.charAt(index) < '0' || digits.charAt(index) > '9') return -1;
        }
        return Integer.parseInt(digits);
    }

    /** A JSON number (or numeric string, which Zalo's optInt also accepts) as a whole number, or null. */
    private static Long integer(Object value) {
        if (value instanceof Double) {
            double number = (Double) value;
            if (Double.isNaN(number) || Math.abs(number) > Integer.MAX_VALUE) return null;
            return (long) number;
        }
        if (value instanceof String) {
            try {
                double number = Double.parseDouble(((String) value).trim());
                return Math.abs(number) > Integer.MAX_VALUE ? null : (long) number;
            } catch (NumberFormatException notNumber) {
                return null;
            }
        }
        return null;
    }

    /**
     * Minimal JSON reader: objects ({@link Map}), arrays ({@link List}), strings, numbers ({@link Double}),
     * booleans and null. Throws {@link IllegalArgumentException} on malformed input.
     */
    static final class JsonReader {
        private static final int MAX_DEPTH = 32;
        private final String source;
        private int position;

        JsonReader(String source) {
            this.source = source;
        }

        Object readDocument() {
            Object value = readValue(0);
            skipWhitespace();
            if (position != source.length()) throw error("trailing data");
            return value;
        }

        private Object readValue(int depth) {
            if (depth > MAX_DEPTH) throw error("too deep");
            skipWhitespace();
            if (position >= source.length()) throw error("unexpected end");
            char next = source.charAt(position);
            switch (next) {
                case '{': return readObject(depth);
                case '[': return readArray(depth);
                case '"': return readString();
                case 't': expectWord("true"); return Boolean.TRUE;
                case 'f': expectWord("false"); return Boolean.FALSE;
                case 'n': expectWord("null"); return null;
                default:
                    if (next == '-' || (next >= '0' && next <= '9')) return readNumber();
                    throw error("unexpected character");
            }
        }

        private Map<String, Object> readObject(int depth) {
            position++;
            Map<String, Object> object = new LinkedHashMap<>();
            skipWhitespace();
            if (peek() == '}') {
                position++;
                return object;
            }
            while (true) {
                skipWhitespace();
                if (peek() != '"') throw error("expected key");
                String key = readString();
                skipWhitespace();
                if (peek() != ':') throw error("expected ':'");
                position++;
                object.put(key, readValue(depth + 1));
                skipWhitespace();
                char separator = peek();
                position++;
                if (separator == '}') return object;
                if (separator != ',') throw error("expected ',' or '}'");
            }
        }

        private List<Object> readArray(int depth) {
            position++;
            skipWhitespace();
            if (peek() == ']') {
                position++;
                return Collections.emptyList();
            }
            List<Object> array = new ArrayList<>();
            while (true) {
                array.add(readValue(depth + 1));
                skipWhitespace();
                char separator = peek();
                position++;
                if (separator == ']') return array;
                if (separator != ',') throw error("expected ',' or ']'");
            }
        }

        private String readString() {
            position++;
            StringBuilder out = new StringBuilder();
            while (true) {
                if (position >= source.length()) throw error("unterminated string");
                char c = source.charAt(position++);
                if (c == '"') return out.toString();
                if (c != '\\') {
                    out.append(c);
                    continue;
                }
                if (position >= source.length()) throw error("unterminated escape");
                char escape = source.charAt(position++);
                switch (escape) {
                    case '"': case '\\': case '/': out.append(escape); break;
                    case 'b': out.append('\b'); break;
                    case 'f': out.append('\f'); break;
                    case 'n': out.append('\n'); break;
                    case 'r': out.append('\r'); break;
                    case 't': out.append('\t'); break;
                    case 'u':
                        if (position + 4 > source.length()) throw error("bad \\u escape");
                        try {
                            out.append((char) Integer.parseInt(source.substring(position, position + 4), 16));
                        } catch (NumberFormatException bad) {
                            throw error("bad \\u escape");
                        }
                        position += 4;
                        break;
                    default: throw error("bad escape");
                }
            }
        }

        private Double readNumber() {
            int start = position;
            while (position < source.length() && "+-0123456789.eE".indexOf(source.charAt(position)) >= 0) position++;
            try {
                return Double.parseDouble(source.substring(start, position));
            } catch (NumberFormatException bad) {
                throw error("bad number");
            }
        }

        private void expectWord(String word) {
            if (!source.startsWith(word, position)) throw error("expected " + word);
            position += word.length();
        }

        private char peek() {
            if (position >= source.length()) throw error("unexpected end");
            return source.charAt(position);
        }

        private void skipWhitespace() {
            while (position < source.length() && Character.isWhitespace(source.charAt(position))) position++;
        }

        private IllegalArgumentException error(String message) {
            return new IllegalArgumentException(message + " at " + position);
        }
    }
}
