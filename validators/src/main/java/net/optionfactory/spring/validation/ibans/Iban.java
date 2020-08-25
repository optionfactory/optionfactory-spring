package net.optionfactory.spring.validation.ibans;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;
import javax.validation.Constraint;
import javax.validation.ConstraintValidator;
import javax.validation.ConstraintValidatorContext;
import javax.validation.Payload;
import net.optionfactory.spring.validation.ibans.Iban.IbanValidator;


@Target({ElementType.FIELD, ElementType.METHOD, ElementType.PARAMETER, ElementType.CONSTRUCTOR, ElementType.ANNOTATION_TYPE, ElementType.TYPE_USE})
@Retention(RetentionPolicy.RUNTIME)
@Constraint(validatedBy = IbanValidator.class)
@Documented
public @interface Iban {

    String message() default "Iban non valido";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};

    public static class IbanFormat {

        public int len;
        public Pattern regex;

        public static IbanFormat of(int len, String regex) {
            IbanFormat i = new IbanFormat();
            i.len = len;
            i.regex = Pattern.compile(regex);
            return i;
        }

    }

    public static class IbanValidator implements ConstraintValidator<Iban, String> {

        private static final Map<String, IbanFormat> COUNTRY_CODE_TO_FORMAT = new ConcurrentHashMap<>();

        static {
            COUNTRY_CODE_TO_FORMAT.put("AD", IbanFormat.of(24, "^AD\\d{10}[A-Z0-9]{12}$")); //  Andorra
            COUNTRY_CODE_TO_FORMAT.put("AE", IbanFormat.of(23, "^AE\\d{21}$")); //  United Arab Emirates
            COUNTRY_CODE_TO_FORMAT.put("AL", IbanFormat.of(28, "^AL\\d{10}[A-Z0-9]{16}$")); //  Albania
            COUNTRY_CODE_TO_FORMAT.put("AT", IbanFormat.of(20, "^AT\\d{18}$")); //  Austria
            COUNTRY_CODE_TO_FORMAT.put("AZ", IbanFormat.of(28, "^AZ\\d{2}[A-Z]{4}[A-Z0-9]{20}$")); //  Republic of Azerbaijan
            COUNTRY_CODE_TO_FORMAT.put("BA", IbanFormat.of(20, "^BA\\d{18}$")); //  Bosnia and Herzegovina
            COUNTRY_CODE_TO_FORMAT.put("BE", IbanFormat.of(16, "^BE\\d{14}$")); //  Belgium
            COUNTRY_CODE_TO_FORMAT.put("BG", IbanFormat.of(22, "^BG\\d{2}[A-Z]{4}\\d{6}[A-Z0-9]{8}$")); //  Bulgaria
            COUNTRY_CODE_TO_FORMAT.put("BH", IbanFormat.of(22, "^BH\\d{2}[A-Z]{4}[A-Z0-9]{14}$")); //  Bahrain (Kingdom of)
            COUNTRY_CODE_TO_FORMAT.put("BR", IbanFormat.of(29, "^BR\\d{25}[A-Z]{1}[A-Z0-9]{1}$")); //  Brazil
            COUNTRY_CODE_TO_FORMAT.put("BY", IbanFormat.of(28, "^BY\\d{2}[A-Z0-9]{4}\\d{4}[A-Z0-9]{16}$")); //  Republic of Belarus
            COUNTRY_CODE_TO_FORMAT.put("CH", IbanFormat.of(21, "^CH\\d{7}[A-Z0-9]{12}$")); //  Switzerland
            COUNTRY_CODE_TO_FORMAT.put("CR", IbanFormat.of(22, "^CR\\d{20}$")); //  Costa Rica
            COUNTRY_CODE_TO_FORMAT.put("CY", IbanFormat.of(28, "^CY\\d{10}[A-Z0-9]{16}$")); //  Cyprus
            COUNTRY_CODE_TO_FORMAT.put("CZ", IbanFormat.of(24, "^CZ\\d{22}$")); //  Czech Republic
            COUNTRY_CODE_TO_FORMAT.put("DE", IbanFormat.of(22, "^DE\\d{20}$")); //  Germany
            COUNTRY_CODE_TO_FORMAT.put("DK", IbanFormat.of(18, "^DK\\d{16}$")); //  Denmark
            COUNTRY_CODE_TO_FORMAT.put("DO", IbanFormat.of(28, "^DO\\d{2}[A-Z0-9]{4}\\d{20}$")); //  Dominican Republic
            COUNTRY_CODE_TO_FORMAT.put("EE", IbanFormat.of(20, "^EE\\d{18}$")); //  Estonia
            COUNTRY_CODE_TO_FORMAT.put("ES", IbanFormat.of(24, "^ES\\d{22}$")); //  Spain
            COUNTRY_CODE_TO_FORMAT.put("FI", IbanFormat.of(18, "^FI\\d{16}$")); //  Finland
            COUNTRY_CODE_TO_FORMAT.put("FO", IbanFormat.of(18, "^FO\\d{16}$")); //  Denmark (Faroes)
            COUNTRY_CODE_TO_FORMAT.put("FR", IbanFormat.of(27, "^FR\\d{12}[A-Z0-9]{11}\\d{2}$")); //  France
            COUNTRY_CODE_TO_FORMAT.put("GB", IbanFormat.of(22, "^GB\\d{2}[A-Z]{4}\\d{14}$")); //  United Kingdom
            COUNTRY_CODE_TO_FORMAT.put("GE", IbanFormat.of(22, "^GE\\d{2}[A-Z]{2}\\d{16}$")); //  Georgia
            COUNTRY_CODE_TO_FORMAT.put("GI", IbanFormat.of(23, "^GI\\d{2}[A-Z]{4}[A-Z0-9]{15}$")); //  Gibraltar
            COUNTRY_CODE_TO_FORMAT.put("GL", IbanFormat.of(18, "^GL\\d{16}$")); //  Denmark (Greenland)
            COUNTRY_CODE_TO_FORMAT.put("GR", IbanFormat.of(27, "^GR\\d{9}[A-Z0-9]{16}$")); //  Greece
            COUNTRY_CODE_TO_FORMAT.put("GT", IbanFormat.of(28, "^GT\\d{2}[A-Z0-9]{24}$")); //  Guatemala
            COUNTRY_CODE_TO_FORMAT.put("HR", IbanFormat.of(21, "^HR\\d{19}$")); //  Croatia
            COUNTRY_CODE_TO_FORMAT.put("HU", IbanFormat.of(28, "^HU\\d{26}$")); //  Hungary
            COUNTRY_CODE_TO_FORMAT.put("IE", IbanFormat.of(22, "^IE\\d{2}[A-Z]{4}\\d{14}$")); //  Ireland
            COUNTRY_CODE_TO_FORMAT.put("IL", IbanFormat.of(23, "^IL\\d{21}$")); //  Israel
            COUNTRY_CODE_TO_FORMAT.put("IS", IbanFormat.of(26, "^IS\\d{24}$")); //  Iceland
            COUNTRY_CODE_TO_FORMAT.put("IT", IbanFormat.of(27, "^IT\\d{2}[A-Z]{1}\\d{10}[A-Z0-9]{12}$")); //  Italy
            COUNTRY_CODE_TO_FORMAT.put("IQ", IbanFormat.of(23, "^IQ\\d{2}[A-Z]{4}\\d{15}$")); //  Iraq
            COUNTRY_CODE_TO_FORMAT.put("JO", IbanFormat.of(30, "^JO\\d{2}[A-Z]{4}\\d{4}[A-Z0-9]{18}$")); //  Jordan
            COUNTRY_CODE_TO_FORMAT.put("KW", IbanFormat.of(30, "^KW\\d{2}[A-Z]{4}[A-Z0-9]{22}$")); //  Kuwait
            COUNTRY_CODE_TO_FORMAT.put("KZ", IbanFormat.of(20, "^KZ\\d{5}[A-Z0-9]{13}$")); //  Kazakhstan
            COUNTRY_CODE_TO_FORMAT.put("LB", IbanFormat.of(28, "^LB\\d{6}[A-Z0-9]{20}$")); //  Lebanon
            COUNTRY_CODE_TO_FORMAT.put("LC", IbanFormat.of(32, "^LC\\d{2}[A-Z]{4}[A-Z0-9]{24}$")); //  Saint Lucia
            COUNTRY_CODE_TO_FORMAT.put("LI", IbanFormat.of(21, "^LI\\d{7}[A-Z0-9]{12}$")); //  Liechtenstein (Principality of)
            COUNTRY_CODE_TO_FORMAT.put("LT", IbanFormat.of(20, "^LT\\d{18}$")); //  Lithuania
            COUNTRY_CODE_TO_FORMAT.put("LU", IbanFormat.of(20, "^LU\\d{5}[A-Z0-9]{13}$")); //  Luxembourg
            COUNTRY_CODE_TO_FORMAT.put("LV", IbanFormat.of(21, "^LV\\d{2}[A-Z]{4}[A-Z0-9]{13}$")); //  Latvia
            COUNTRY_CODE_TO_FORMAT.put("MC", IbanFormat.of(27, "^MC\\d{12}[A-Z0-9]{11}\\d{2}$")); //  Monaco
            COUNTRY_CODE_TO_FORMAT.put("MD", IbanFormat.of(24, "^MD\\d{2}[A-Z0-9]{20}$")); //  Moldova
            COUNTRY_CODE_TO_FORMAT.put("ME", IbanFormat.of(22, "^ME\\d{20}$")); //  Montenegro
            COUNTRY_CODE_TO_FORMAT.put("MK", IbanFormat.of(19, "^MK\\d{5}[A-Z0-9]{10}\\d{2}$")); //  Macedonia, Former Yugoslav Republic of
            COUNTRY_CODE_TO_FORMAT.put("MR", IbanFormat.of(27, "^MR\\d{25}$")); //  Mauritania
            COUNTRY_CODE_TO_FORMAT.put("MT", IbanFormat.of(31, "^MT\\d{2}[A-Z]{4}\\d{5}[A-Z0-9]{18}$")); //  Malta
            COUNTRY_CODE_TO_FORMAT.put("MU", IbanFormat.of(30, "^MU\\d{2}[A-Z]{4}\\d{19}[A-Z]{3}$")); //  Mauritius
            COUNTRY_CODE_TO_FORMAT.put("NL", IbanFormat.of(18, "^NL\\d{2}[A-Z]{4}\\d{10}$")); //  The Netherlands
            COUNTRY_CODE_TO_FORMAT.put("NO", IbanFormat.of(15, "^NO\\d{13}$")); //  Norway
            COUNTRY_CODE_TO_FORMAT.put("PK", IbanFormat.of(24, "^PK\\d{2}[A-Z]{4}[A-Z0-9]{16}$")); //  Pakistan
            COUNTRY_CODE_TO_FORMAT.put("PL", IbanFormat.of(28, "^PL\\d{26}$")); //  Poland
            COUNTRY_CODE_TO_FORMAT.put("PS", IbanFormat.of(29, "^PS\\d{2}[A-Z]{4}[A-Z0-9]{21}$")); //  Palestine, State of
            COUNTRY_CODE_TO_FORMAT.put("PT", IbanFormat.of(25, "^PT\\d{23}$")); //  Portugal
            COUNTRY_CODE_TO_FORMAT.put("QA", IbanFormat.of(29, "^QA\\d{2}[A-Z]{4}[A-Z0-9]{21}$")); //  Qatar
            COUNTRY_CODE_TO_FORMAT.put("RO", IbanFormat.of(24, "^RO\\d{2}[A-Z]{4}[A-Z0-9]{16}$")); //  Romania
            COUNTRY_CODE_TO_FORMAT.put("RS", IbanFormat.of(22, "^RS\\d{20}$")); //  Serbia
            COUNTRY_CODE_TO_FORMAT.put("SA", IbanFormat.of(24, "^SA\\d{4}[A-Z0-9]{18}$")); //  Saudi Arabia
            COUNTRY_CODE_TO_FORMAT.put("SC", IbanFormat.of(31, "^SC\\d{2}[A-Z]{4}\\d{20}[A-Z]{3}$")); //  Seychelles
            COUNTRY_CODE_TO_FORMAT.put("SE", IbanFormat.of(24, "^SE\\d{22}$")); //  Sweden
            COUNTRY_CODE_TO_FORMAT.put("SI", IbanFormat.of(19, "^SI\\d{17}$")); //  Slovenia
            COUNTRY_CODE_TO_FORMAT.put("SK", IbanFormat.of(24, "^SK\\d{22}$")); //  Slovak Republic
            COUNTRY_CODE_TO_FORMAT.put("SM", IbanFormat.of(27, "^SM\\d{2}[A-Z]{1}\\d{10}[A-Z0-9]{12}$")); //  San Marino
            COUNTRY_CODE_TO_FORMAT.put("ST", IbanFormat.of(25, "^ST\\d{23}$")); //  Sao Tome and Principe
            COUNTRY_CODE_TO_FORMAT.put("TL", IbanFormat.of(23, "^TL\\d{21}$")); //  Timor-Leste
            COUNTRY_CODE_TO_FORMAT.put("TN", IbanFormat.of(24, "^TN\\d{22}$")); //  Tunisia
            COUNTRY_CODE_TO_FORMAT.put("TR", IbanFormat.of(26, "^TR\\d{8}[A-Z0-9]{16}$")); //  Turkey
            COUNTRY_CODE_TO_FORMAT.put("UA", IbanFormat.of(29, "^UA\\d{8}[A-Z0-9]{19}$")); //  Ukraine
            COUNTRY_CODE_TO_FORMAT.put("VG", IbanFormat.of(24, "^VG\\d{2}[A-Z]{4}\\d{16}$")); //  Virgin Islands, British
            COUNTRY_CODE_TO_FORMAT.put("XK", IbanFormat.of(20, "^XK\\d{18}$")); //  Republic of Kosovo

        }

        public static int getNumericValue(final int ch) {
            return Character.isDigit(ch) ? ch - '0' : ch - 'A' + 10;
        }

        @Override
        public boolean isValid(String value, ConstraintValidatorContext context) {
            if (value == null) {
                return true;
            }
            if (value.length() < 4) {
                return false;
            }
            final String trimmed = value.trim().toUpperCase();
            final String countryCode = trimmed.substring(0, 2);
            final String check = trimmed.substring(2, 4);
            final String rest = trimmed.substring(4);
            final IbanFormat format = COUNTRY_CODE_TO_FORMAT.get(countryCode);
            if (format == null) {
                return false;
            }
            if (trimmed.length() != format.len) {
                //Check that the total IBAN length is correct as per the country. If not, the IBAN is invalid
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
            final boolean validCheck = (total % 97) == 1 ;
            return validCheck;
        }

    }
}
