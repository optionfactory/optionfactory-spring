package net.optionfactory.spring.localizedenums;

@LocalizedEnum(category = "with-bodies")
public enum AnEnumWithBodies {
    PLAIN,
    WITH_BODY {
        @Override
        public String toString() {
            return "custom";
        }
    };
}
