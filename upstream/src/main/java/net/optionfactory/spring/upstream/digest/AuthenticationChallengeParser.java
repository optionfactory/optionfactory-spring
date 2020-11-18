package net.optionfactory.spring.upstream.digest;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

public class AuthenticationChallengeParser {

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
        final AuthenticationChallenge result = new AuthenticationChallenge();
        result.scheme = scheme.toLowerCase();
        result.params = new HashMap<>();
        String key;
        String value;
        while (state.more()) {
            key = naked(state, ',', '=');
            value = null;
            if (state.more() && (state.peek() == '=')) {
                state.pos++; // consume '='
                value = maybeQuoted(state, ',');
            }
            if (state.more() && (state.peek() == ',')) {
                state.pos++; // consume ','
            }
            if (key != null && !(key.equals("") && value == null)) {
                result.params.put(key, value);
            }
        }
        return result;
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

    public static class AuthenticationChallenge {

        public String scheme;
        public Map<String, String> params;
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
