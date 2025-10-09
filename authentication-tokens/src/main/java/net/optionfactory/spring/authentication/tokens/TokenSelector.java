package net.optionfactory.spring.authentication.tokens;

public record TokenSelector(String headerName, String authScheme) {
}
