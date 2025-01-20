package net.optionfactory.spring.authentication.resource.server;

import com.nimbusds.jose.Header;
import com.nimbusds.jwt.JWT;
import com.nimbusds.jwt.JWTParser;
import jakarta.servlet.http.HttpServletRequest;
import java.text.ParseException;
import java.util.Optional;
import java.util.function.Predicate;
import org.springframework.security.oauth2.server.resource.web.BearerTokenResolver;

public class JwtTokenResolverAdapter implements BearerTokenResolver {

    private final String headerName = "Authorization";
    private final String authScheme = "BEARER ";
    private final Predicate<Header> predicate;

    public JwtTokenResolverAdapter(Predicate<Header> predicate) {
        this.predicate = predicate;
    }

    @Override
    public String resolve(HttpServletRequest request) {
        return searchToken(request, headerName, authScheme)
                .flatMap(token -> {
                    try{
                        final JWT jwt = JWTParser.parse(token);
                        final var accepted = predicate.test(jwt.getHeader());
                        return accepted ? Optional.of(token) : Optional.empty();
                    }catch(ParseException ex){
                        return null;
                    }
                })
                .orElse(null);
    }

    public static Optional<String> searchToken(HttpServletRequest request, String headerName, String authScheme) {
        return Optional.ofNullable(request.getHeader(headerName))
                .filter(value -> value.toUpperCase().startsWith(authScheme))
                .map(value -> value.substring(authScheme.length()).trim());
    }
}
