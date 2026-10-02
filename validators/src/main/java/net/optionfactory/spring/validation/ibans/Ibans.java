package net.optionfactory.spring.validation.ibans;

import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;

/// IBAN (ISO 13616) normalization and validation, as used by [Iban] and usable on its own.
///
/// Validation checks, in order, that the value is long enough to carry a country code and check
/// digits, that the country is one of the 75 whose national format is known here, that the length
/// and the national format match, that the check digits are not `00`, `01` or `99` (outside the
/// `02`-`98` range the checksum computation produces), and finally the ISO 7064 mod 97-10 checksum.
/// An IBAN of a country missing from the table is rejected, not accepted unchecked.
public class Ibans {

    /// The national format of the IBANs of one country.
    ///
    /// @param len the total length of the IBAN, country code and check digits included
    /// @param regex the pattern the whole, uppercase IBAN must match
    public record IbanFormat(int len, Pattern regex) {

        /// @param len the total length of the IBAN
        /// @param regex the pattern the whole IBAN must match
        /// @return the format, with `regex` compiled
        public static IbanFormat of(int len, String regex) {
            return new IbanFormat(len, Pattern.compile(regex));
        }
    }

    private static final Map<String, IbanFormat> COUNTRY_CODE_TO_FORMAT = new ConcurrentHashMap<>();

    static {
        COUNTRY_CODE_TO_FORMAT.put("AD", IbanFormat.of(24, "^AD\\d{10}[A-Z0-9]{12}$"));
        COUNTRY_CODE_TO_FORMAT.put("AE", IbanFormat.of(23, "^AE\\d{21}$"));
        COUNTRY_CODE_TO_FORMAT.put("AL", IbanFormat.of(28, "^AL\\d{10}[A-Z0-9]{16}$"));
        COUNTRY_CODE_TO_FORMAT.put("AT", IbanFormat.of(20, "^AT\\d{18}$"));
        COUNTRY_CODE_TO_FORMAT.put("AZ", IbanFormat.of(28, "^AZ\\d{2}[A-Z]{4}[A-Z0-9]{20}$"));
        COUNTRY_CODE_TO_FORMAT.put("BA", IbanFormat.of(20, "^BA\\d{18}$"));
        COUNTRY_CODE_TO_FORMAT.put("BE", IbanFormat.of(16, "^BE\\d{14}$"));
        COUNTRY_CODE_TO_FORMAT.put("BG", IbanFormat.of(22, "^BG\\d{2}[A-Z]{4}\\d{6}[A-Z0-9]{8}$"));
        COUNTRY_CODE_TO_FORMAT.put("BH", IbanFormat.of(22, "^BH\\d{2}[A-Z]{4}[A-Z0-9]{14}$"));
        COUNTRY_CODE_TO_FORMAT.put("BR", IbanFormat.of(29, "^BR\\d{25}[A-Z]{1}[A-Z0-9]{1}$"));
        COUNTRY_CODE_TO_FORMAT.put("BY", IbanFormat.of(28, "^BY\\d{2}[A-Z0-9]{4}\\d{4}[A-Z0-9]{16}$"));
        COUNTRY_CODE_TO_FORMAT.put("CH", IbanFormat.of(21, "^CH\\d{7}[A-Z0-9]{12}$"));
        COUNTRY_CODE_TO_FORMAT.put("CR", IbanFormat.of(22, "^CR\\d{20}$"));
        COUNTRY_CODE_TO_FORMAT.put("CY", IbanFormat.of(28, "^CY\\d{10}[A-Z0-9]{16}$"));
        COUNTRY_CODE_TO_FORMAT.put("CZ", IbanFormat.of(24, "^CZ\\d{22}$"));
        COUNTRY_CODE_TO_FORMAT.put("DE", IbanFormat.of(22, "^DE\\d{20}$"));
        COUNTRY_CODE_TO_FORMAT.put("DK", IbanFormat.of(18, "^DK\\d{16}$"));
        COUNTRY_CODE_TO_FORMAT.put("DO", IbanFormat.of(28, "^DO\\d{2}[A-Z0-9]{4}\\d{20}$"));
        COUNTRY_CODE_TO_FORMAT.put("EE", IbanFormat.of(20, "^EE\\d{18}$"));
        COUNTRY_CODE_TO_FORMAT.put("ES", IbanFormat.of(24, "^ES\\d{22}$"));
        COUNTRY_CODE_TO_FORMAT.put("FI", IbanFormat.of(18, "^FI\\d{16}$"));
        COUNTRY_CODE_TO_FORMAT.put("FO", IbanFormat.of(18, "^FO\\d{16}$"));
        COUNTRY_CODE_TO_FORMAT.put("FR", IbanFormat.of(27, "^FR\\d{12}[A-Z0-9]{11}\\d{2}$"));
        COUNTRY_CODE_TO_FORMAT.put("GB", IbanFormat.of(22, "^GB\\d{2}[A-Z]{4}\\d{14}$"));
        COUNTRY_CODE_TO_FORMAT.put("GE", IbanFormat.of(22, "^GE\\d{2}[A-Z]{2}\\d{16}$"));
        COUNTRY_CODE_TO_FORMAT.put("GI", IbanFormat.of(23, "^GI\\d{2}[A-Z]{4}[A-Z0-9]{15}$"));
        COUNTRY_CODE_TO_FORMAT.put("GL", IbanFormat.of(18, "^GL\\d{16}$"));
        COUNTRY_CODE_TO_FORMAT.put("GR", IbanFormat.of(27, "^GR\\d{9}[A-Z0-9]{16}$"));
        COUNTRY_CODE_TO_FORMAT.put("GT", IbanFormat.of(28, "^GT\\d{2}[A-Z0-9]{24}$"));
        COUNTRY_CODE_TO_FORMAT.put("HR", IbanFormat.of(21, "^HR\\d{19}$"));
        COUNTRY_CODE_TO_FORMAT.put("HU", IbanFormat.of(28, "^HU\\d{26}$"));
        COUNTRY_CODE_TO_FORMAT.put("IE", IbanFormat.of(22, "^IE\\d{2}[A-Z]{4}\\d{14}$"));
        COUNTRY_CODE_TO_FORMAT.put("IL", IbanFormat.of(23, "^IL\\d{21}$"));
        COUNTRY_CODE_TO_FORMAT.put("IS", IbanFormat.of(26, "^IS\\d{24}$"));
        COUNTRY_CODE_TO_FORMAT.put("IT", IbanFormat.of(27, "^IT\\d{2}[A-Z]{1}\\d{10}[A-Z0-9]{12}$"));
        COUNTRY_CODE_TO_FORMAT.put("IQ", IbanFormat.of(23, "^IQ\\d{2}[A-Z]{4}\\d{15}$"));
        COUNTRY_CODE_TO_FORMAT.put("JO", IbanFormat.of(30, "^JO\\d{2}[A-Z]{4}\\d{4}[A-Z0-9]{18}$"));
        COUNTRY_CODE_TO_FORMAT.put("KW", IbanFormat.of(30, "^KW\\d{2}[A-Z]{4}[A-Z0-9]{22}$"));
        COUNTRY_CODE_TO_FORMAT.put("KZ", IbanFormat.of(20, "^KZ\\d{5}[A-Z0-9]{13}$"));
        COUNTRY_CODE_TO_FORMAT.put("LB", IbanFormat.of(28, "^LB\\d{6}[A-Z0-9]{20}$"));
        COUNTRY_CODE_TO_FORMAT.put("LC", IbanFormat.of(32, "^LC\\d{2}[A-Z]{4}[A-Z0-9]{24}$"));
        COUNTRY_CODE_TO_FORMAT.put("LI", IbanFormat.of(21, "^LI\\d{7}[A-Z0-9]{12}$"));
        COUNTRY_CODE_TO_FORMAT.put("LT", IbanFormat.of(20, "^LT\\d{18}$"));
        COUNTRY_CODE_TO_FORMAT.put("LU", IbanFormat.of(20, "^LU\\d{5}[A-Z0-9]{13}$"));
        COUNTRY_CODE_TO_FORMAT.put("LV", IbanFormat.of(21, "^LV\\d{2}[A-Z]{4}[A-Z0-9]{13}$"));
        COUNTRY_CODE_TO_FORMAT.put("MC", IbanFormat.of(27, "^MC\\d{12}[A-Z0-9]{11}\\d{2}$"));
        COUNTRY_CODE_TO_FORMAT.put("MD", IbanFormat.of(24, "^MD\\d{2}[A-Z0-9]{20}$"));
        COUNTRY_CODE_TO_FORMAT.put("ME", IbanFormat.of(22, "^ME\\d{20}$"));
        COUNTRY_CODE_TO_FORMAT.put("MK", IbanFormat.of(19, "^MK\\d{5}[A-Z0-9]{10}\\d{2}$"));
        COUNTRY_CODE_TO_FORMAT.put("MR", IbanFormat.of(27, "^MR\\d{25}$"));
        COUNTRY_CODE_TO_FORMAT.put("MT", IbanFormat.of(31, "^MT\\d{2}[A-Z]{4}\\d{5}[A-Z0-9]{18}$"));
        COUNTRY_CODE_TO_FORMAT.put("MU", IbanFormat.of(30, "^MU\\d{2}[A-Z]{4}\\d{19}[A-Z]{3}$"));
        COUNTRY_CODE_TO_FORMAT.put("NL", IbanFormat.of(18, "^NL\\d{2}[A-Z]{4}\\d{10}$"));
        COUNTRY_CODE_TO_FORMAT.put("NO", IbanFormat.of(15, "^NO\\d{13}$"));
        COUNTRY_CODE_TO_FORMAT.put("PK", IbanFormat.of(24, "^PK\\d{2}[A-Z]{4}[A-Z0-9]{16}$"));
        COUNTRY_CODE_TO_FORMAT.put("PL", IbanFormat.of(28, "^PL\\d{26}$"));
        COUNTRY_CODE_TO_FORMAT.put("PS", IbanFormat.of(29, "^PS\\d{2}[A-Z]{4}[A-Z0-9]{21}$"));
        COUNTRY_CODE_TO_FORMAT.put("PT", IbanFormat.of(25, "^PT\\d{23}$"));
        COUNTRY_CODE_TO_FORMAT.put("QA", IbanFormat.of(29, "^QA\\d{2}[A-Z]{4}[A-Z0-9]{21}$"));
        COUNTRY_CODE_TO_FORMAT.put("RO", IbanFormat.of(24, "^RO\\d{2}[A-Z]{4}[A-Z0-9]{16}$"));
        COUNTRY_CODE_TO_FORMAT.put("RS", IbanFormat.of(22, "^RS\\d{20}$"));
        COUNTRY_CODE_TO_FORMAT.put("SA", IbanFormat.of(24, "^SA\\d{4}[A-Z0-9]{18}$"));
        COUNTRY_CODE_TO_FORMAT.put("SC", IbanFormat.of(31, "^SC\\d{2}[A-Z]{4}\\d{20}[A-Z]{3}$"));
        COUNTRY_CODE_TO_FORMAT.put("SE", IbanFormat.of(24, "^SE\\d{22}$"));
        COUNTRY_CODE_TO_FORMAT.put("SI", IbanFormat.of(19, "^SI\\d{17}$"));
        COUNTRY_CODE_TO_FORMAT.put("SK", IbanFormat.of(24, "^SK\\d{22}$"));
        COUNTRY_CODE_TO_FORMAT.put("SM", IbanFormat.of(27, "^SM\\d{2}[A-Z]{1}\\d{10}[A-Z0-9]{12}$"));
        COUNTRY_CODE_TO_FORMAT.put("ST", IbanFormat.of(25, "^ST\\d{23}$"));
        COUNTRY_CODE_TO_FORMAT.put("TL", IbanFormat.of(23, "^TL\\d{21}$"));
        COUNTRY_CODE_TO_FORMAT.put("TN", IbanFormat.of(24, "^TN\\d{22}$"));
        COUNTRY_CODE_TO_FORMAT.put("TR", IbanFormat.of(26, "^TR\\d{8}[A-Z0-9]{16}$"));
        COUNTRY_CODE_TO_FORMAT.put("UA", IbanFormat.of(29, "^UA\\d{8}[A-Z0-9]{19}$"));
        COUNTRY_CODE_TO_FORMAT.put("VA", IbanFormat.of(22, "^VA\\d{2}[A-Z0-9]{18}$"));
        COUNTRY_CODE_TO_FORMAT.put("VG", IbanFormat.of(24, "^VG\\d{2}[A-Z]{4}\\d{16}$"));
        COUNTRY_CODE_TO_FORMAT.put("XK", IbanFormat.of(20, "^XK\\d{18}$"));
    }

    /// The value of an IBAN character in the mod 97 checksum: `0`-`9` are worth 0 to 9, `A`-`Z` 10
    /// to 35.
    ///
    /// @param ch an uppercase ASCII letter or a digit; any other character yields a meaningless value
    /// @return the character's value
    public static int getNumericValue(final int ch) {
        return Character.isDigit(ch) ? ch - '0' : ch - 'A' + 10;
    }

    /// Brings an IBAN as people write it to its electronic form: trimmed, uppercased, with every
    /// character other than ASCII letters and digits removed, so the spaces, dashes and dots that
    /// group its digits go away. Normalization does not validate: the result may well be no IBAN.
    ///
    /// @param iban the IBAN to normalize, possibly `null`
    /// @return the normalized IBAN, or `null` when `iban` is `null`
    public static String normalize(String iban) {
        if (iban == null) {
            return null;
        }
        return iban.trim().toUpperCase(Locale.ROOT).replaceAll("[^A-Z0-9]", "");
    }

    /// Validates an IBAN in its electronic form.
    ///
    /// The value is trimmed and uppercased before checking, so surrounding whitespace and lowercase
    /// letters are tolerated; separators within it are not: [#normalize(String)] it first to accept
    /// them.
    ///
    /// @param value the IBAN, possibly `null`
    /// @return true when the value is a valid IBAN of a known country, false otherwise, `null`
    ///         included
    public static boolean isValid(String value) {
        if (value == null) {
            return false;
        }
        final String trimmed = value.trim().toUpperCase(Locale.ROOT);
        if (trimmed.length() < 4) {
            return false;
        }
        final String countryCode = trimmed.substring(0, 2);
        final String check = trimmed.substring(2, 4);
        final String rest = trimmed.substring(4);
        final IbanFormat format = COUNTRY_CODE_TO_FORMAT.get(countryCode);
        if (format == null) {
            return false;
        }
        if (trimmed.length() != format.len) {
            return false;
        }
        if (!format.regex.matcher(trimmed).matches()) {
            return false;
        }
        if ("00".equals(check) || "01".equals(check) || "99".equals(check)) {
            return false;
        }
        final String reformatted = rest + countryCode + check;
        long total = 0;
        for (int i = 0; i < reformatted.length(); i++) {
            final char nth = reformatted.charAt(i);
            final int charValue = Character.isDigit(nth) ? nth - '0' : nth - 'A' + 10;
            total = (charValue > 9 ? total * 100 : total * 10) + charValue;
            if (total > 999_999_999) {
                total = total % 97;
            }
        }
        final boolean validCheck = (total % 97) == 1;
        return validCheck;
    }
}
