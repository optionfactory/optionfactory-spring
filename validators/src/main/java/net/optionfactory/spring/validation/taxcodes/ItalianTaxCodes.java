package net.optionfactory.spring.validation.taxcodes;

import java.time.DateTimeException;
import java.time.LocalDate;
import java.time.Month;
import java.time.Year;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.IntStream;
import static net.optionfactory.spring.validation.taxcodes.ItalianTaxCodeValidator.CODICE_FISCALE_LENGTH;
import static net.optionfactory.spring.validation.taxcodes.ItalianTaxCodeValidator.PARTITA_IVA_LENGTH;

public class ItalianTaxCodes {

    public enum Type {
        CODICE_FISCALE, PARTITA_IVA, ANY;
    }

    
    
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

    private static String unmapHomocody(String segment) {
        final var sb = new StringBuilder();
        for (char c : segment.toCharArray()) {
            sb.append(HOMOCODY_REVERSE.getOrDefault(c, c));
        }
        return sb.toString();
    }

    public static LocalDate guessBirthDate(String value, LocalDate referenceDate) {
        final var fiscalCode = normalize(value);
        if (fiscalCode == null || fiscalCode.length() != CODICE_FISCALE_LENGTH) {
            return null;
        }

        final var yearChars = fiscalCode.substring(6, 8);
        final var monthChar = fiscalCode.charAt(8);
        final var dayChars = fiscalCode.substring(9, 11);

        final int twoDigitYear = Integer.parseInt(unmapHomocody(yearChars));
        final int rawDayInt = Integer.parseInt(unmapHomocody(dayChars));

        if (!MONTH_CODES.containsKey(monthChar)) {
            return null;
        }
        final var month = MONTH_CODES.get(monthChar);
        final int day = rawDayInt > 40 ? rawDayInt - 40 : rawDayInt;

        final int referenceYear = referenceDate.getYear();
        final int currentCentury = (referenceYear / 100) * 100;
        final var firstGuessYear = currentCentury + twoDigitYear;
        final var candidateYear = day > month.length(Year.isLeap(firstGuessYear)) ? firstGuessYear - 100 : firstGuessYear;
        try {
            final var candidateDate = LocalDate.of(candidateYear, month, day);
            return candidateDate.isAfter(referenceDate) ? candidateDate.minusYears(100) : candidateDate;
        } catch (DateTimeException ex) {
            return null;
        }
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
    private static final Map<Character, Month> MONTH_CODES = new ConcurrentHashMap<>();    
    private static final Map<Character, Character> HOMOCODY = new ConcurrentHashMap<>();
    private static final Map<Character, Character> HOMOCODY_REVERSE = new ConcurrentHashMap<>();

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
        
        MONTH_CODES.put('A', Month.JANUARY);
        MONTH_CODES.put('B', Month.FEBRUARY);
        MONTH_CODES.put('C', Month.MARCH);
        MONTH_CODES.put('D', Month.APRIL);
        MONTH_CODES.put('E', Month.MAY);
        MONTH_CODES.put('H', Month.JUNE);
        MONTH_CODES.put('L', Month.JULY);
        MONTH_CODES.put('M', Month.AUGUST);
        MONTH_CODES.put('P', Month.SEPTEMBER);
        MONTH_CODES.put('R', Month.OCTOBER);
        MONTH_CODES.put('S', Month.NOVEMBER);
        MONTH_CODES.put('T', Month.DECEMBER);

        HOMOCODY.put('0', 'L');
        HOMOCODY.put('1', 'M');
        HOMOCODY.put('2', 'N');
        HOMOCODY.put('3', 'P');
        HOMOCODY.put('4', 'Q');
        HOMOCODY.put('5', 'R');
        HOMOCODY.put('6', 'S');
        HOMOCODY.put('7', 'T');
        HOMOCODY.put('8', 'U');
        HOMOCODY.put('9', 'V');

        HOMOCODY_REVERSE.put('L', '0');
        HOMOCODY_REVERSE.put('M', '1');
        HOMOCODY_REVERSE.put('N', '2');
        HOMOCODY_REVERSE.put('P', '3');
        HOMOCODY_REVERSE.put('Q', '4');
        HOMOCODY_REVERSE.put('R', '5');
        HOMOCODY_REVERSE.put('S', '6');
        HOMOCODY_REVERSE.put('T', '7');
        HOMOCODY_REVERSE.put('U', '8');
        HOMOCODY_REVERSE.put('V', '9');
    }

}
