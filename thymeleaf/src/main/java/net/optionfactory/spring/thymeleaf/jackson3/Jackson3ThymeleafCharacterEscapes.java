package net.optionfactory.spring.thymeleaf.jackson3;

import tools.jackson.core.SerializableString;
import tools.jackson.core.io.CharacterEscapes;
import tools.jackson.core.io.SerializedString;

/**
 * Replaces JacksonThymeleafCharacterEscapes for Jackson 3.
 * @author rferranti
 */
public class Jackson3ThymeleafCharacterEscapes extends CharacterEscapes {

    private static final int[] CHARACTER_ESCAPES;
    private static final SerializableString SLASH_ESCAPE;
    private static final SerializableString AMPERSAND_ESCAPE;
    static {
        CHARACTER_ESCAPES = CharacterEscapes.standardAsciiEscapesForJSON();
        CHARACTER_ESCAPES['/'] = CharacterEscapes.ESCAPE_CUSTOM;
        CHARACTER_ESCAPES['&'] = CharacterEscapes.ESCAPE_CUSTOM;
        SLASH_ESCAPE = new SerializedString("\\/");
        AMPERSAND_ESCAPE = new SerializedString("\\u0026");
    }

    @Override
    public int[] getEscapeCodesForAscii() {
        return CHARACTER_ESCAPES;
    }

    @Override
    public SerializableString getEscapeSequence(final int ch) {
        if (ch == '/') {
            return SLASH_ESCAPE;
        }
        if (ch == '&') {
            return AMPERSAND_ESCAPE;
        }
        return null;
    }

}
