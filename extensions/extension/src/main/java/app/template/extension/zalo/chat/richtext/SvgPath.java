package app.template.extension.zalo.chat.richtext;

/**
 * Minimal SVG / VectorDrawable {@code pathData} parser for the toolbar icons, so they can be drawn
 * from Material path strings without adding resources. Supports M, L, H, V, C, S, Q, T and Z in
 * absolute and relative forms (no arcs). Android-free: commands go to a {@link Sink}.
 */
public final class SvgPath {
    private SvgPath() { }

    /** Receives absolute path commands. */
    public interface Sink {
        void moveTo(float x, float y);
        void lineTo(float x, float y);
        void cubicTo(float x1, float y1, float x2, float y2, float x, float y);
        void quadTo(float x1, float y1, float x, float y);
        void close();
    }

    /** Parses {@code data} into {@code sink}. Throws IllegalArgumentException on malformed data. */
    public static void parse(String data, Sink sink) {
        Parser parser = new Parser(data);
        float x = 0, y = 0, startX = 0, startY = 0;
        float controlX = 0, controlY = 0;
        char previous = 0;
        char command = 0;
        while (true) {
            parser.skipSeparators();
            if (parser.atEnd()) break;
            char next = parser.peek();
            if (Character.isLetter(next)) {
                command = next;
                parser.advance();
            } else if (command == 0) {
                throw new IllegalArgumentException("Path data must start with a command: " + data);
            } else if (command == 'M') {
                command = 'L';
            } else if (command == 'm') {
                command = 'l';
            }
            boolean relative = Character.isLowerCase(command);
            float baseX = relative ? x : 0;
            float baseY = relative ? y : 0;
            switch (Character.toUpperCase(command)) {
                case 'M':
                    x = baseX + parser.number();
                    y = baseY + parser.number();
                    startX = x;
                    startY = y;
                    sink.moveTo(x, y);
                    break;
                case 'L':
                    x = baseX + parser.number();
                    y = baseY + parser.number();
                    sink.lineTo(x, y);
                    break;
                case 'H':
                    x = baseX + parser.number();
                    sink.lineTo(x, y);
                    break;
                case 'V':
                    y = baseY + parser.number();
                    sink.lineTo(x, y);
                    break;
                case 'C': {
                    float x1 = baseX + parser.number();
                    float y1 = baseY + parser.number();
                    float x2 = baseX + parser.number();
                    float y2 = baseY + parser.number();
                    x = baseX + parser.number();
                    y = baseY + parser.number();
                    sink.cubicTo(x1, y1, x2, y2, x, y);
                    controlX = x2;
                    controlY = y2;
                    break;
                }
                case 'S': {
                    boolean smooth = "CcSs".indexOf(previous) >= 0;
                    float x1 = smooth ? 2 * x - controlX : x;
                    float y1 = smooth ? 2 * y - controlY : y;
                    float x2 = baseX + parser.number();
                    float y2 = baseY + parser.number();
                    x = baseX + parser.number();
                    y = baseY + parser.number();
                    sink.cubicTo(x1, y1, x2, y2, x, y);
                    controlX = x2;
                    controlY = y2;
                    break;
                }
                case 'Q': {
                    float x1 = baseX + parser.number();
                    float y1 = baseY + parser.number();
                    x = baseX + parser.number();
                    y = baseY + parser.number();
                    sink.quadTo(x1, y1, x, y);
                    controlX = x1;
                    controlY = y1;
                    break;
                }
                case 'T': {
                    boolean smooth = "QqTt".indexOf(previous) >= 0;
                    float x1 = smooth ? 2 * x - controlX : x;
                    float y1 = smooth ? 2 * y - controlY : y;
                    x = baseX + parser.number();
                    y = baseY + parser.number();
                    sink.quadTo(x1, y1, x, y);
                    controlX = x1;
                    controlY = y1;
                    break;
                }
                case 'Z':
                    sink.close();
                    x = startX;
                    y = startY;
                    break;
                default:
                    throw new IllegalArgumentException("Unsupported path command '" + command + "'");
            }
            previous = command;
            if (Character.toUpperCase(command) == 'Z') command = 0;
        }
    }

    private static final class Parser {
        private final String data;
        private int position;

        Parser(String data) {
            this.data = data;
        }

        boolean atEnd() {
            return position >= data.length();
        }

        char peek() {
            return data.charAt(position);
        }

        void advance() {
            position++;
        }

        void skipSeparators() {
            while (!atEnd() && (Character.isWhitespace(peek()) || peek() == ',')) position++;
        }

        float number() {
            skipSeparators();
            int start = position;
            if (!atEnd() && (peek() == '-' || peek() == '+')) position++;
            boolean dot = false;
            boolean exponent = false;
            while (!atEnd()) {
                char c = peek();
                if (Character.isDigit(c)) {
                    position++;
                } else if (c == '.' && !dot && !exponent) {
                    dot = true;
                    position++;
                } else if ((c == 'e' || c == 'E') && !exponent && position > start) {
                    exponent = true;
                    position++;
                    if (!atEnd() && (peek() == '-' || peek() == '+')) position++;
                } else {
                    break;
                }
            }
            if (start == position) throw new IllegalArgumentException("Number expected at " + start + " in " + data);
            return Float.parseFloat(data.substring(start, position));
        }
    }
}
