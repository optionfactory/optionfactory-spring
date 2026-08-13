package net.optionfactory.spring.localizedenums;

public record LocalizedEnumResponse(String category, String name, String value) {

    public static LocalizedEnumResponse of(String category, String name, String value) {
        return new LocalizedEnumResponse(category, name, value);
    }
}
