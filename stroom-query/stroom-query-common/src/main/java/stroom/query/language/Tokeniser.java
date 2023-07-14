package stroom.query.language;

import stroom.query.language.QuotedStringToken.Builder;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class Tokeniser {

    private static final Map<String, Pattern> PATTERN_CACHE = new ConcurrentHashMap<>();

    private static final char DOUBLE_QUOTE_CHAR = '\"';
    private static final char SINGLE_QUOTE_CHAR = '\'';
    private static final char ESCAPE_CHAR = '\\';

    private List<Token> tokens;

    private Tokeniser(final String string) {
        if (string == null || string.isBlank()) {
            throw new TokenException(null, "Empty query");
        }

        char[] chars = string.toCharArray();
        final Token unknown = new Token(TokenType.UNKNOWN, chars, 0, chars.length - 1);
        tokens = Collections.singletonList(unknown);

        // Tag Comments
        split("//.*", 0, TokenType.COMMENT);
        split("/\\*([^*]|[\\r\\n]|(\\*+([^*/]|[\\r\\n])))*\\*+/", 0, TokenType.BLOCK_COMMENT);

        // Tag quoted strings.
        extractQuotedTokens();

        // Tag keywords.
        TokenType.KEYWORDS.forEach(token -> tagKeyword(token.toString().toLowerCase(Locale.ROOT), token));
        // Treat other conjunctions as keywords.
        tagKeyword("by", TokenType.BY);
        tagKeyword("as", TokenType.AS);
        tagKeyword("between", TokenType.BETWEEN);

        // Tag functions.
        split("([a-z-A-Z_]+)([\\s]*\\()", 1, TokenType.FUNCTION_NAME);

        // Tag brackets.
        split("\\(", 0, TokenType.OPEN_BRACKET);
        split("\\)", 0, TokenType.CLOSE_BRACKET);

        // Tag whitespace.
        split("(^|\\s)(is[\\s]+null)(\\s|$)", 2, TokenType.IS_NULL);
        split("(^|\\s)(is[\\s]+not[\\s]+null)(\\s|$)", 2, TokenType.IS_NOT_NULL);

        // Tag whitespace.
        split("\\s+", 0, TokenType.WHITESPACE);

//        // Tag pipes.
//        split("\\|", 0, TokenType.PIPE);

        // Tag dates.
        split("\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}\\.\\d{3}Z?", 0, TokenType.STRING);

        split(",", 0, TokenType.COMMA);
        split("\\^", 0, TokenType.ORDER);
        split("/", 0, TokenType.DIVISION);
        split("\\*", 0, TokenType.MULTIPLICATION);
        split("%", 0, TokenType.MODULUS);
        split("\\+", 0, TokenType.PLUS);
        split("\\-", 0, TokenType.MINUS);
        split("!=", 0, TokenType.NOT_EQUALS);
        split("<=", 0, TokenType.LESS_THAN_OR_EQUAL_TO);
        split(">=", 0, TokenType.GREATER_THAN_OR_EQUAL_TO);
        split("<", 0, TokenType.LESS_THAN);
        split(">", 0, TokenType.GREATER_THAN);
        split("=", 0, TokenType.EQUALS);

        // Tag numbers.
        split("(^|\\s|\\))([-]?[0-9]+(\\.[0-9]+)?)(\\s|\\(|$)", 2, TokenType.NUMBER);

        // Tag everything else as a string.
        categorise(TokenType.STRING);
    }

    private void tagKeyword(final String pattern, final TokenType tokenType) {
        split("(^|\\s|\\))(" + pattern + ")(\\s|\\(|$)", 2, tokenType);
    }

    public static List<Token> parse(final String string) {
        return new Tokeniser(string).tokens;
    }

    private void categorise(final TokenType tokenType) {
        final List<Token> out = new ArrayList<>();
        for (final Token token : tokens) {
            if (TokenType.UNKNOWN.equals(token.getTokenType())) {
                final Token.Builder builder = new Token.Builder()
                        .tokenType(tokenType)
                        .chars(token.getChars())
                        .start(token.getStart())
                        .end(token.getEnd());
                out.add(builder.build());
            } else {
                out.add(token);
            }
        }
        this.tokens = out;
    }

    private void split(final String regex,
                       final int group,
                       final TokenType tokenType) {
        final Pattern pattern = PATTERN_CACHE
                .computeIfAbsent(regex, k -> Pattern.compile(k, Pattern.CASE_INSENSITIVE));
        final List<Token> out = new ArrayList<>();
        for (final Token token : tokens) {
            if (TokenType.UNKNOWN.equals(token.getTokenType())) {
                final String str = new String(token.getChars(),
                        token.getStart(),
                        token.getEnd() - token.getStart() + 1);
                final Matcher matcher = pattern.matcher(str);

                int lastPos = 0;
                while (matcher.find()) {
                    final int start = matcher.start(group);
                    final int end = matcher.end(group) - 1;

                    if (start > lastPos) {
                        out.add(new Token(token.getTokenType(),
                                token.getChars(),
                                token.getStart() + lastPos,
                                token.getStart() + start - 1));
                    }

                    out.add(new Token(tokenType, token.getChars(), token.getStart() + start, token.getStart() + end));
                    lastPos = end + 1;
                }

                if (token.getStart() + lastPos <= token.getEnd()) {
                    out.add(new Token(token.getTokenType(),
                            token.getChars(),
                            token.getStart() + lastPos,
                            token.getEnd()));
                }
            } else {
                out.add(token);
            }
        }
        this.tokens = out;
    }

    private void extractQuotedTokens() {
        final List<Token> out = new ArrayList<>();
        for (final Token token : tokens) {
            if (TokenType.UNKNOWN.equals(token.getTokenType())) {
                // Break the string into quoted text blocks.
                int lastPos = token.getStart();
                boolean inQuote = false;
                boolean escape = false;
                TokenType outputType = null;
                for (int i = token.getStart(); i <= token.getEnd(); i++) {
                    final char c = token.getChars()[i];
                    if (inQuote) {
                        if (escape) {
                            escape = false;
                        } else {
                            if (c == ESCAPE_CHAR) {
                                escape = true;
                            } else if ((c == DOUBLE_QUOTE_CHAR && outputType == TokenType.DOUBLE_QUOTED_STRING)
                                    || (c == SINGLE_QUOTE_CHAR && outputType == TokenType.SINGLE_QUOTED_STRING)) {
                                inQuote = false;
                                if (lastPos < i) {
                                    final QuotedStringToken quotedStringToken = new Builder()
                                            .tokenType(outputType)
                                            .chars(token.getChars())
                                            .start(lastPos)
                                            .end(i)
                                            .build();
                                    out.add(quotedStringToken);
                                }
                                lastPos = i + 1;
                            }
                        }
                    } else if (c == DOUBLE_QUOTE_CHAR || c == SINGLE_QUOTE_CHAR) {
                        outputType = c == DOUBLE_QUOTE_CHAR
                                ? TokenType.DOUBLE_QUOTED_STRING
                                : TokenType.SINGLE_QUOTED_STRING;
                        inQuote = true;
                        if (lastPos < i) {
                            out.add(new Token(TokenType.UNKNOWN, token.getChars(), lastPos, i - 1));
                        }
                        lastPos = i;
                    }
                }

                if (lastPos <= token.getEnd()) {
                    out.add(new Token(TokenType.UNKNOWN, token.getChars(), lastPos, token.getEnd()));
                }
            } else {
                out.add(token);
            }
        }
        this.tokens = out;
    }
}
