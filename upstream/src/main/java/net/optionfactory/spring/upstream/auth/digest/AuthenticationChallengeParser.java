package net.optionfactory.spring.upstream.auth.digest;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

/// Parses the value of a `WWW-Authenticate` header carrying a single challenge, such as
/// `Digest realm="r", nonce="n", qop="auth,auth-int"`.
///
/// The parser is lenient: values may be quoted or not, commas inside quoted values are kept, the
/// surrounding quotes are removed while escaped quotes inside the value are kept verbatim (backslash
/// included), and empty entries are skipped. A header carrying several challenges is not split: it
/// yields the first scheme, with the following challenges mixed into its parameters.
///
/// Instances are stateless and thread-safe.
public class AuthenticationChallengeParser {

    /// @param str the header value
    /// @return the challenge, with its scheme lowercased and its parameter names as sent; a parameter
    /// without `=` maps to `null`
    /// @throws IllegalStateException when the value is `null` or has no scheme
    public AuthenticationChallenge parse(String str) {
        if (str == null) {
            throw new IllegalStateException("Null authentication challenge");
        }
        final ParserState state = new ParserState();
        state.chars = str.toCharArray();
        state.pos = 0;
        final String scheme = naked(state, ' ');
        if (scheme == null) {
            throw new IllegalStateException("Empty authentication challenge");
        }
        final Map<String, String> params = new HashMap<>();
        String key;
        String value;
        while (state.more()) {
            key = naked(state, ',', '=');
            value = null;
            if (state.more() && (state.peek() == '=')) {
                state.pos++;
                value = maybeQuoted(state, ',');
            }
            if (state.more() && (state.peek() == ',')) {
                state.pos++;
            }
            if (key != null && !(key.equals("") && value == null)) {
                params.put(key, value);
            }
        }
        return new AuthenticationChallenge(scheme.toLowerCase(), params);
    }
    

    private String naked(ParserState state, char... terminators) {
        final int ts = state.pos;
        int te = state.pos;
        while (state.more()) {
            if (Arrays.binarySearch(terminators, state.peek()) > -1) {
                break;
            }
            te++;
            state.pos++;
        }
        final String token = state.extract(ts, te);
        final String stripped = token.strip();
        return stripped.isEmpty() ? null : stripped;
    }

    private String maybeQuoted(ParserState state, char terminator) {
        final int ts = state.pos;
        int te = state.pos;
        boolean quoted = false;
        boolean charEscaped = false;
        while (state.more()) {
            final char ch = state.peek();
            if (!quoted && ch == terminator) {
                break;
            }
            if (!charEscaped && ch == '"') {
                quoted = !quoted;
            }
            charEscaped = !charEscaped && ch == '\\';
            te++;
            state.pos++;
        }
        final String token = state.extract(ts, te);
        final String stripped = token.strip();
        if(stripped.isEmpty()){
            return null;
        }
        if(stripped.length() >= 2 && stripped.charAt(0) == '"' && stripped.charAt(stripped.length() - 1) == '"'){
            return stripped.substring(1, stripped.length() - 1);
        }
        return stripped;
    }    

    /// A parsed authentication challenge.
    ///
    /// @param scheme the lowercased authentication scheme, e.g. `digest`
    /// @param params the challenge parameters, unquoted, by name
    public record AuthenticationChallenge(String scheme, Map<String, String> params) {
    }

    private static class ParserState {

        public char[] chars;
        public int pos;

        public boolean more() {
            return pos < chars.length;
        }

        public char peek() {
            return chars[pos];
        }

        public String extract(int ts, int te) {
            return te < ts ? "" : new String(chars, ts, te - ts).strip();
        }

    }


}
