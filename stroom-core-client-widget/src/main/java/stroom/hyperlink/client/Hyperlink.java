package stroom.hyperlink.client;

import stroom.svg.shared.SvgImage;

import com.google.gwt.http.client.URL;

import java.util.Objects;

/**
 * This class is used to detect table cell values that contain URL's to be turned into hyperlinks.
 * <p>
 * [:text](http://some-url/:id){:hyperlinkTarget}
 */
public class Hyperlink {

    private final String text;
    private final String href;
    private final String type;
    private final SvgImage icon;

    public Hyperlink(final String text,
                     final String href,
                     final String type,
                     final SvgImage icon) {
        this.text = text;
        this.href = href;
        this.type = type;
        this.icon = icon;
    }

    public static Hyperlink create(final String value) {
        return create(value, 0);
    }

    public static Hyperlink create(final String value, final int pos) {
        Hyperlink hyperlink = null;

        int index = pos;
        final String text = nextToken(value, index, '[', ']');
        if (text != null) {
            index = index + text.length() + 2;
            final String href = nextToken(value, index, '(', ')');
            if (href != null) {
                index = index + href.length() + 2;
                final String type = nextToken(value, index, '{', '}');
                hyperlink = new Builder().text(text).href(href).type(type).build();
            }
        }

        return hyperlink;
    }

    private static String nextToken(final String value, final int pos, final char startChar, final char endChar) {
        if (value.length() <= pos + 2 || value.charAt(pos) != startChar) {
            return null;
        }

        final StringBuilder sb = new StringBuilder();
        for (int i = pos + 1; i < value.length(); i++) {
            final char c = value.charAt(i);
            if (c == endChar) {
                return sb.toString();
            } else if (c == '[' || c == ']' || c == '(' || c == ')') {
                // Unexpected token
                return null;
            } else {
                sb.append(c);
            }
        }
        return null;
    }

    public String getText() {
        return decode(text);
    }

    public String getHref() {
        return decode(href);
    }

    public String getType() {
        return decode(type);
    }

    public SvgImage getIcon() {
        return icon;
    }

    private String decode(final String string) {
        // Hyperlink values are URLEncoded within the link dashboard function so they need to be decoded when used.
        if (string == null) {
            return null;
        }
        return URL.decodeQueryString(string);
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final Hyperlink hyperlink = (Hyperlink) o;
        return Objects.equals(text, hyperlink.text) &&
                Objects.equals(href, hyperlink.href) &&
                Objects.equals(type, hyperlink.type);
    }

    @Override
    public int hashCode() {
        return Objects.hash(text, href, type);
    }

    @Override
    public String toString() {
        final StringBuilder sb = new StringBuilder();
        if (text != null) {
            sb.append("[");
            sb.append(text);
            sb.append("]");
        }
        if (href != null) {
            sb.append("(");
            sb.append(href);
            sb.append(")");
        }
        if (type != null) {
            sb.append("{");
            sb.append(type);
            sb.append("}");
        }
        return sb.toString();
    }

    public static Builder builder() {
        return new Builder();
    }

    public Builder copy() {
        return new Builder(this);
    }

    public static final class Builder {

        private String text;
        private String href;
        private String type;
        private SvgImage icon;

        private Builder() {
        }

        private Builder(final Hyperlink hyperlink) {
            this.text = hyperlink.text;
            this.href = hyperlink.href;
            this.type = hyperlink.type;
            this.icon = hyperlink.icon;
        }

        public Builder text(final String text) {
            this.text = text;
            return this;
        }

        public Builder href(final String href) {
            this.href = href;
            return this;
        }

        public Builder type(final String type) {
            this.type = type;
            return this;
        }

        public Builder icon(final SvgImage icon) {
            this.icon = icon;
            return this;
        }

        public Hyperlink build() {
            return new Hyperlink(
                    text,
                    href,
                    type,
                    icon);
        }
    }
}
