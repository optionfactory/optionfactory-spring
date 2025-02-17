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
                .sum();
        var ys = IntStream.of(1, 3, 5, 7, 9)
                .map(piva::charAt)
                .map(c -> Character.digit(c, 10))
                .toArray();

        var y = IntStream.of(ys).map(v -> v * 2).sum();
        var z = IntStream.of(ys).filter(v -> v >= 5).count();
        var t = (x + y + z) % 10;
        var c = (10 - t) % 10;
        return Optional.of(Character.forDigit((int) c, 10));
    }

    private static IntStream oddChars(String fiscalCode) {
        return IntStream.of(0, 2, 4, 6, 8, 10, 12, 14).map(fiscalCode::charAt);
    }

    private static IntStream evenChars(String fiscalCode) {
        return IntStream.of(1, 3, 5, 7, 9, 11, 13).map(fiscalCode::charAt);
    }

    public static Optional<Character> controlCodeCodiceFiscale(String fiscalCode) {
        if (!oddChars(fiscalCode).allMatch(ODD_CODES::containsKey)) {
            return Optional.empty();
        }
        if (!evenChars(fiscalCode).allMatch(EVEN_CODES::containsKey)) {
            return Optional.empty();
        }
        final int oddsSum = oddChars(fiscalCode).map(ODD_CODES::get).sum();
        final int evenSum = evenChars(fiscalCode).map(EVEN_CODES::get).sum();
        final int code = (oddsSum + evenSum) % 26;
        return Optional.of((char) (code + 65));
    }

    private static final Map<Integer, Integer> ODD_CODES = new ConcurrentHashMap<>();
    private static final Map<Integer, Integer> EVEN_CODES = new ConcurrentHashMap<>();

    static {

        ODD_CODES.put((int) '0', 1);
        ODD_CODES.put((int) '1', 0);
        ODD_CODES.put((int) '2', 5);
        ODD_CODES.put((int) '3', 7);
        ODD_CODES.put((int) '4', 9);
        ODD_CODES.put((int) '5', 13);
        ODD_CODES.put((int) '6', 15);
        ODD_CODES.put((int) '7', 17);
        ODD_CODES.put((int) '8', 19);
        ODD_CODES.put((int) '9', 21);
        ODD_CODES.put((int) 'A', 1);
        ODD_CODES.put((int) 'B', 0);
        ODD_CODES.put((int) 'C', 5);
        ODD_CODES.put((int) 'D', 7);
        ODD_CODES.put((int) 'E', 9);
        ODD_CODES.put((int) 'F', 13);
        ODD_CODES.put((int) 'G', 15);
        ODD_CODES.put((int) 'H', 17);
        ODD_CODES.put((int) 'I', 19);
        ODD_CODES.put((int) 'J', 21);
        ODD_CODES.put((int) 'K', 2);
        ODD_CODES.put((int) 'L', 4);
        ODD_CODES.put((int) 'M', 18);
        ODD_CODES.put((int) 'N', 20);
        ODD_CODES.put((int) 'O', 11);
        ODD_CODES.put((int) 'P', 3);
        ODD_CODES.put((int) 'Q', 6);
        ODD_CODES.put((int) 'R', 8);
        ODD_CODES.put((int) 'S', 12);
        ODD_CODES.put((int) 'T', 14);
        ODD_CODES.put((int) 'U', 16);
        ODD_CODES.put((int) 'V', 10);
        ODD_CODES.put((int) 'W', 22);
        ODD_CODES.put((int) 'X', 25);
        ODD_CODES.put((int) 'Y', 24);
        ODD_CODES.put((int) 'Z', 23);

        EVEN_CODES.put((int) '0', 0);
        EVEN_CODES.put((int) '1', 1);
        EVEN_CODES.put((int) '2', 2);
        EVEN_CODES.put((int) '3', 3);
        EVEN_CODES.put((int) '4', 4);
        EVEN_CODES.put((int) '5', 5);
        EVEN_CODES.put((int) '6', 6);
        EVEN_CODES.put((int) '7', 7);
        EVEN_CODES.put((int) '8', 8);
        EVEN_CODES.put((int) '9', 9);
        EVEN_CODES.put((int) 'A', 0);
        EVEN_CODES.put((int) 'B', 1);
        EVEN_CODES.put((int) 'C', 2);
        EVEN_CODES.put((int) 'D', 3);
        EVEN_CODES.put((int) 'E', 4);
        EVEN_CODES.put((int) 'F', 5);
        EVEN_CODES.put((int) 'G', 6);
        EVEN_CODES.put((int) 'H', 7);
        EVEN_CODES.put((int) 'I', 8);
        EVEN_CODES.put((int) 'J', 9);
        EVEN_CODES.put((int) 'K', 10);
        EVEN_CODES.put((int) 'L', 11);
        EVEN_CODES.put((int) 'M', 12);
        EVEN_CODES.put((int) 'N', 13);
        EVEN_CODES.put((int) 'O', 14);
        EVEN_CODES.put((int) 'P', 15);
        EVEN_CODES.put((int) 'Q', 16);
        EVEN_CODES.put((int) 'R', 17);
        EVEN_CODES.put((int) 'S', 18);
        EVEN_CODES.put((int) 'T', 19);
        EVEN_CODES.put((int) 'U', 20);
        EVEN_CODES.put((int) 'V', 21);
        EVEN_CODES.put((int) 'W', 22);
        EVEN_CODES.put((int) 'X', 23);
        EVEN_CODES.put((int) 'Y', 24);
        EVEN_CODES.put((int) 'Z', 25);

    }

}
