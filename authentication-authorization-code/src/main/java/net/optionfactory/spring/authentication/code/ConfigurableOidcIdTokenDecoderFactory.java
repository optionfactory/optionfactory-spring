package net.optionfactory.spring.authentication.code;

import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.core.convert.TypeDescriptor;
import org.springframework.core.convert.converter.Converter;
import org.springframework.http.client.ClientHttpRequestFactory;
import org.springframework.security.oauth2.client.oidc.authentication.OidcIdTokenValidator;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.converter.ClaimConversionService;
import org.springframework.security.oauth2.core.converter.ClaimTypeConverter;
import org.springframework.security.oauth2.core.oidc.IdTokenClaimNames;
import org.springframework.security.oauth2.core.oidc.OidcIdToken;
import org.springframework.security.oauth2.core.oidc.StandardClaimNames;
import org.springframework.security.oauth2.jose.jws.JwsAlgorithm;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtDecoderFactory;
import org.springframework.security.oauth2.jwt.JwtTimestampValidator;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.util.Assert;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestTemplate;

/**
* same as OidcIdTokenDecoderFactory, but so we can configure the clienthHttpRequestFactory used by NimbusJwtDecoder
 */
public final class ConfigurableOidcIdTokenDecoderFactory implements JwtDecoderFactory<ClientRegistration> {

    private static final String MISSING_SIGNATURE_VERIFIER_ERROR_CODE = "missing_signature_verifier";
    private static Map<JwsAlgorithm, String> jcaAlgorithmMappings = new HashMap<JwsAlgorithm, String>() {
        {
            put(MacAlgorithm.HS256, "HmacSHA256");
            put(MacAlgorithm.HS384, "HmacSHA384");
            put(MacAlgorithm.HS512, "HmacSHA512");
        }
    };
    private static final Converter<Map<String, Object>, Map<String, Object>> DEFAULT_CLAIM_TYPE_CONVERTER = new ClaimTypeConverter(createDefaultClaimTypeConverters());
    private final Map<String, JwtDecoder> jwtDecoders = new ConcurrentHashMap<>();
    private Function<ClientRegistration, OAuth2TokenValidator<Jwt>> jwtValidatorFactory = clientRegistration -> new DelegatingOAuth2TokenValidator<>(new JwtTimestampValidator(), new OidcIdTokenValidator(clientRegistration));
    private Function<ClientRegistration, JwsAlgorithm> jwsAlgorithmResolver = clientRegistration -> SignatureAlgorithm.RS256;
    private Function<ClientRegistration, Converter<Map<String, Object>, Map<String, Object>>> claimTypeConverterFactory = clientRegistration -> DEFAULT_CLAIM_TYPE_CONVERTER;
    
    private final RestTemplate restTemplate;

    public ConfigurableOidcIdTokenDecoderFactory(ClientHttpRequestFactory oauthHttpRequestFactory) {
        this.restTemplate = new RestTemplate(oauthHttpRequestFactory);
    }

    /**
     * Returns the default {@link Converter}'s used for type conversion of claim
     * values for an {@link OidcIdToken}.
     *
     * @return a {@link Map} of {@link Converter}'s keyed by
     * {@link IdTokenClaimNames claim name}
     */
    public static Map<String, Converter<Object, ?>> createDefaultClaimTypeConverters() {
        Converter<Object, ?> booleanConverter = getConverter(TypeDescriptor.valueOf(Boolean.class));
        Converter<Object, ?> instantConverter = getConverter(TypeDescriptor.valueOf(Instant.class));
        Converter<Object, ?> urlConverter = getConverter(TypeDescriptor.valueOf(URL.class));
        Converter<Object, ?> stringConverter = getConverter(TypeDescriptor.valueOf(String.class));
        Converter<Object, ?> collectionStringConverter = getConverter(
                TypeDescriptor.collection(Collection.class, TypeDescriptor.valueOf(String.class)));

        Map<String, Converter<Object, ?>> claimTypeConverters = new HashMap<>();
        claimTypeConverters.put(IdTokenClaimNames.ISS, urlConverter);
        claimTypeConverters.put(IdTokenClaimNames.AUD, collectionStringConverter);
        claimTypeConverters.put(IdTokenClaimNames.NONCE, stringConverter);
        claimTypeConverters.put(IdTokenClaimNames.EXP, instantConverter);
        claimTypeConverters.put(IdTokenClaimNames.IAT, instantConverter);
        claimTypeConverters.put(IdTokenClaimNames.AUTH_TIME, instantConverter);
        claimTypeConverters.put(IdTokenClaimNames.AMR, collectionStringConverter);
        claimTypeConverters.put(StandardClaimNames.EMAIL_VERIFIED, booleanConverter);
        claimTypeConverters.put(StandardClaimNames.PHONE_NUMBER_VERIFIED, booleanConverter);
        claimTypeConverters.put(StandardClaimNames.UPDATED_AT, instantConverter);
        return claimTypeConverters;
    }

    private static Converter<Object, ?> getConverter(TypeDescriptor targetDescriptor) {
        final TypeDescriptor sourceDescriptor = TypeDescriptor.valueOf(Object.class);
        return source -> ClaimConversionService.getSharedInstance().convert(source, sourceDescriptor, targetDescriptor);
    }

    @Override
    public JwtDecoder createDecoder(ClientRegistration clientRegistration) {
        Assert.notNull(clientRegistration, "clientRegistration cannot be null");
        return this.jwtDecoders.computeIfAbsent(clientRegistration.getRegistrationId(), key -> {
            NimbusJwtDecoder jwtDecoder = buildDecoder(clientRegistration);
            jwtDecoder.setJwtValidator(this.jwtValidatorFactory.apply(clientRegistration));
            Converter<Map<String, Object>, Map<String, Object>> claimTypeConverter
                    = this.claimTypeConverterFactory.apply(clientRegistration);
            if (claimTypeConverter != null) {
                jwtDecoder.setClaimSetConverter(claimTypeConverter);
            }
            return jwtDecoder;
        });
    }

    private NimbusJwtDecoder buildDecoder(ClientRegistration clientRegistration) {
        JwsAlgorithm jwsAlgorithm = this.jwsAlgorithmResolver.apply(clientRegistration);
        if (jwsAlgorithm != null && SignatureAlgorithm.class.isAssignableFrom(jwsAlgorithm.getClass())) {
            // https://openid.net/specs/openid-connect-core-1_0.html#IDTokenValidation
            //
            // 6. If the ID Token is received via direct communication between the Client
            // and the Token Endpoint (which it is in this flow),
            // the TLS server validation MAY be used to validate the issuer in place of checking the token signature.
            // The Client MUST validate the signature of all other ID Tokens according to JWS [JWS]
            // using the algorithm specified in the JWT alg Header Parameter.
            // The Client MUST use the keys provided by the Issuer.
            //
            // 7. The alg value SHOULD be the default of RS256 or the algorithm sent by the Client
            // in the id_token_signed_response_alg parameter during Registration.

            String jwkSetUri = clientRegistration.getProviderDetails().getJwkSetUri();
            if (!StringUtils.hasText(jwkSetUri)) {
                OAuth2Error oauth2Error = new OAuth2Error(
                        MISSING_SIGNATURE_VERIFIER_ERROR_CODE,
                        "Failed to find a Signature Verifier for Client Registration: '"
                        + clientRegistration.getRegistrationId()
                        + "'. Check to ensure you have configured the JwkSet URI.",
                        null
                );
                throw new OAuth2AuthenticationException(oauth2Error, oauth2Error.toString());
            }
            return NimbusJwtDecoder.withJwkSetUri(jwkSetUri).restOperations(restTemplate).jwsAlgorithm((SignatureAlgorithm) jwsAlgorithm).build();
        } else if (jwsAlgorithm != null && MacAlgorithm.class.isAssignableFrom(jwsAlgorithm.getClass())) {
            // https://openid.net/specs/openid-connect-core-1_0.html#IDTokenValidation
            //
            // 8. If the JWT alg Header Parameter uses a MAC based algorithm such as HS256, HS384, or HS512,
            // the octets of the UTF-8 representation of the client_secret
            // corresponding to the client_id contained in the aud (audience) Claim
            // are used as the key to validate the signature.
            // For MAC based algorithms, the behavior is unspecified if the aud is multi-valued or
            // if an azp value is present that is different than the aud value.

            String clientSecret = clientRegistration.getClientSecret();
            if (!StringUtils.hasText(clientSecret)) {
                OAuth2Error oauth2Error = new OAuth2Error(
                        MISSING_SIGNATURE_VERIFIER_ERROR_CODE,
                        "Failed to find a Signature Verifier for Client Registration: '"
                        + clientRegistration.getRegistrationId()
                        + "'. Check to ensure you have configured the client secret.",
                        null
                );
                throw new OAuth2AuthenticationException(oauth2Error, oauth2Error.toString());
            }
            SecretKeySpec secretKeySpec = new SecretKeySpec(
                    clientSecret.getBytes(StandardCharsets.UTF_8), jcaAlgorithmMappings.get(jwsAlgorithm));
            return NimbusJwtDecoder.withSecretKey(secretKeySpec).macAlgorithm((MacAlgorithm) jwsAlgorithm).build();
        }

        OAuth2Error oauth2Error = new OAuth2Error(
                MISSING_SIGNATURE_VERIFIER_ERROR_CODE,
                "Failed to find a Signature Verifier for Client Registration: '"
                + clientRegistration.getRegistrationId()
                + "'. Check to ensure you have configured a valid JWS Algorithm: '"
                + jwsAlgorithm + "'",
                null
        );
        throw new OAuth2AuthenticationException(oauth2Error, oauth2Error.toString());
    }

    /**
     * Sets the factory that provides an {@link OAuth2TokenValidator}, which is
     * used by the {@link JwtDecoder}. The default composes
     * {@link JwtTimestampValidator} and {@link OidcIdTokenValidator}.
     *
     * @param jwtValidatorFactory the factory that provides an
     * {@link OAuth2TokenValidator}
     */
    public void setJwtValidatorFactory(Function<ClientRegistration, OAuth2TokenValidator<Jwt>> jwtValidatorFactory) {
        Assert.notNull(jwtValidatorFactory, "jwtValidatorFactory cannot be null");
        this.jwtValidatorFactory = jwtValidatorFactory;
    }

    /**
     * Sets the resolver that provides the expected
     * {@link JwsAlgorithm JWS algorithm} used for the signature or MAC on the
     * {@link OidcIdToken ID Token}. The default resolves to
     * {@link SignatureAlgorithm#RS256 RS256} for all
     * {@link ClientRegistration clients}.
     *
     * @param jwsAlgorithmResolver the resolver that provides the expected
     * {@link JwsAlgorithm JWS algorithm} for a specific
     * {@link ClientRegistration client}
     */
    public void setJwsAlgorithmResolver(Function<ClientRegistration, JwsAlgorithm> jwsAlgorithmResolver) {
        Assert.notNull(jwsAlgorithmResolver, "jwsAlgorithmResolver cannot be null");
        this.jwsAlgorithmResolver = jwsAlgorithmResolver;
    }

    /**
     * Sets the factory that provides a {@link Converter} used for type
     * conversion of claim values for an {@link OidcIdToken}. The default is
     * {@link ClaimTypeConverter} for all {@link ClientRegistration clients}.
     *
     * @param claimTypeConverterFactory the factory that provides a
     * {@link Converter} used for type conversion of claim values for a specific
     * {@link ClientRegistration client}
     */
    public void setClaimTypeConverterFactory(Function<ClientRegistration, Converter<Map<String, Object>, Map<String, Object>>> claimTypeConverterFactory) {
        Assert.notNull(claimTypeConverterFactory, "claimTypeConverterFactory cannot be null");
        this.claimTypeConverterFactory = claimTypeConverterFactory;
    }
}
