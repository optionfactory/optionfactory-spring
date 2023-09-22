package net.optionfactory.spring.validation.taxcodes;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.IntStream;
import net.optionfactory.spring.validation.taxcodes.ItalianTaxCode.Type;
import static net.optionfactory.spring.validation.taxcodes.ItalianTaxCodeValidator.CODICE_FISCALE_LENGTH;
import static net.optionfactory.spring.validation.taxcodes.ItalianTaxCodeValidator.PARTITA_IVA_LENGTH;

public class ItalianTaxCodes {

    public static String normalize(String taxcode) {
        if (taxcode == null) {
            return null;
        }
        return taxcode.trim().toUpperCase().replaceAll("[^A-Z0-9]", "");
    }

    public static boolean isValid(String taxcode, Type type) {
        if (taxcode == null) {
            return false;
        }
        final int len = taxcode.length();
        if (len == 0) {
            return true;
        }
        final char lastCharacter = taxcode.charAt(len - 1);
        if ((type == Type.PARTITA_IVA || type == Type.ANY) && len == PARTITA_IVA_LENGTH) {
            return controlCodePartitaIva(taxcode)
                    .map(cc -> cc == lastCharacter)
                    .orElse(false);
        }
        if ((type == Type.CODICE_FISCALE || type == Type.ANY) && len == CODICE_FISCALE_LENGTH) {
            return controlCodeCodiceFiscale(taxcode)
                    .map(cc -> cc == lastCharacter)
                    .orElse(false);
        }
        return false;
    }

    public static Optional<Character> controlCodePartitaIva(String piva) {
        var x = IntStream.of(0, 2, 4, 6, 8)
                .map(piva::charAt)
                .map(c -> Character.digit(c, 10))
                .reduce(0, Integer::sum);
        var ys = IntStream.of(1, 3, 5, 7, 9)
                .map(piva::charAt)
                .map(c -> Character.digit(c, 10))
                .toArray();

        var y = IntStream.of(ys).map(v -> v * 2).reduce(0, Integer::sum);
        var z = IntStream.of(ys).filter(v -> v >= 5).count();
        var t = (x + y + z) % 10;
        var c = (10 - t) % 10;
        return Optional.of(Character.forDigit((int) c, 10));
    }

    public static Optional<Character> controlCodeCodiceFiscale(String fiscalCode) {
        final boolean oddsAreValid = IntStream.of(0, 2, 4, 6, 8, 10, 12, 14)
                .mapToObj(fiscalCode::charAt)
                .allMatch(ODD_CODES::containsKey);

        if (!oddsAreValid) {
            return Optional.empty();
        }

        final int oddsSum = IntStream.of(0, 2, 4, 6, 8, 10, 12, 14)
                .mapToObj(fiscalCode::charAt)
                .map(ODD_CODES::get)
                .reduce(0, Integer::sum);

        final boolean evenAreValid = IntStream.of(1, 3, 5, 7, 9, 11, 13)
                .mapToObj(fiscalCode::charAt)
                .allMatch(EVEN_CODES::containsKey);

        if (!evenAreValid) {
            return Optional.empty();
        }

        final int evenSum = IntStream.of(1, 3, 5, 7, 9, 11, 13)
                .mapToObj(fiscalCode::charAt)
                .map(EVEN_CODES::get)
                .reduce(0, Integer::sum);

        final int code = (oddsSum + evenSum) % 26;
        return Optional.of((char) (code + 65));
    }

    private static final Map<Character, Integer> ODD_CODES = new ConcurrentHashMap<>();
    private static final Map<Character, Integer> EVEN_CODES = new ConcurrentHashMap<>();

    static {

        ODD_CODES.put('0', 1);
        ODD_CODES.put('1', 0);
        ODD_CODES.put('2', 5);
        ODD_CODES.put('3', 7);
        ODD_CODES.put('4', 9);
        ODD_CODES.put('5', 13);
        ODD_CODES.put('6', 15);
        ODD_CODES.put('7', 17);
        ODD_CODES.put('8', 19);
        ODD_CODES.put('9', 21);
        ODD_CODES.put('A', 1);
        ODD_CODES.put('B', 0);
        ODD_CODES.put('C', 5);
        ODD_CODES.put('D', 7);
        ODD_CODES.put('E', 9);
        ODD_CODES.put('F', 13);
        ODD_CODES.put('G', 15);
        ODD_CODES.put('H', 17);
        ODD_CODES.put('I', 19);
        ODD_CODES.put('J', 21);
        ODD_CODES.put('K', 2);
        ODD_CODES.put('L', 4);
        ODD_CODES.put('M', 18);
        ODD_CODES.put('N', 20);
        ODD_CODES.put('O', 11);
        ODD_CODES.put('P', 3);
        ODD_CODES.put('Q', 6);
        ODD_CODES.put('R', 8);
        ODD_CODES.put('S', 12);
        ODD_CODES.put('T', 14);
        ODD_CODES.put('U', 16);
        ODD_CODES.put('V', 10);
        ODD_CODES.put('W', 22);
        ODD_CODES.put('X', 25);
        ODD_CODES.put('Y', 24);
        ODD_CODES.put('Z', 23);

        EVEN_CODES.put('0', 0);
        EVEN_CODES.put('1', 1);
        EVEN_CODES.put('2', 2);
        EVEN_CODES.put('3', 3);
        EVEN_CODES.put('4', 4);
        EVEN_CODES.put('5', 5);
        EVEN_CODES.put('6', 6);
        EVEN_CODES.put('7', 7);
        EVEN_CODES.put('8', 8);
        EVEN_CODES.put('9', 9);
        EVEN_CODES.put('A', 0);
        EVEN_CODES.put('B', 1);
        EVEN_CODES.put('C', 2);
        EVEN_CODES.put('D', 3);
        EVEN_CODES.put('E', 4);
        EVEN_CODES.put('F', 5);
        EVEN_CODES.put('G', 6);
        EVEN_CODES.put('H', 7);
        EVEN_CODES.put('I', 8);
        EVEN_CODES.put('J', 9);
        EVEN_CODES.put('K', 10);
        EVEN_CODES.put('L', 11);
        EVEN_CODES.put('M', 12);
        EVEN_CODES.put('N', 13);
        EVEN_CODES.put('O', 14);
        EVEN_CODES.put('P', 15);
        EVEN_CODES.put('Q', 16);
        EVEN_CODES.put('R', 17);
        EVEN_CODES.put('S', 18);
        EVEN_CODES.put('T', 19);
        EVEN_CODES.put('U', 20);
        EVEN_CODES.put('V', 21);
        EVEN_CODES.put('W', 22);
        EVEN_CODES.put('X', 23);
        EVEN_CODES.put('Y', 24);
        EVEN_CODES.put('Z', 25);

    }

}
