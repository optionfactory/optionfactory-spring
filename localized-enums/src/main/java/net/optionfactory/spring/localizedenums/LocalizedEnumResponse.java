package net.optionfactory.spring.localizedenums;

public class LocalizedEnumResponse {

    public String category;
    public String name;
    public String value;

    public static LocalizedEnumResponse of(String category, String name, String value) {
        final var r = new LocalizedEnumResponse();
        r.category = category;
        r.name = name;
        r.value = value;
        return r;
    }

}
