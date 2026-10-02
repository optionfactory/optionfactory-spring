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

/// Creates the decoders verifying the OIDC id tokens received at login: the same as spring's
/// `OidcIdTokenDecoderFactory`, except that the issuer's jwk set is fetched through a configurable
/// `ClientHttpRequestFactory`, so that proxies, timeouts and tls settings apply to it too.
///
/// The algorithm is resolved per client registration, RS256 by default, and decides how the id
/// token is verified, as the OIDC core specification (3.1.3.7, "ID Token Validation") prescribes:
///
/// - a signature algorithm (`RS*`, `PS*`, `ES*`) verifies with the issuer's keys, fetched from the
///   registration's jwk set uri;
/// - a mac algorithm (`HS*`) verifies with the UTF-8 bytes of the registration's client secret,
///   with no fetch at all.
///
/// Once verified, the token is validated by `JwtTimestampValidator` and `OidcIdTokenValidator` (the
/// issuer, when the registration declares one; an audience naming this client; `azp`, `iat` and
/// `exp`) unless another validator is configured, and its claims are converted to the types
/// `OidcIdToken` expects.
///
/// A decoder is created once per registration id and cached: changes to a registration with the
/// same id are not picked up.
///
/// `oauth2Login` picks the factory up as a `JwtDecoderFactory<ClientRegistration>` bean:
///
/// ```java
/// @Bean
/// public JwtDecoderFactory<ClientRegistration> idTokenDecoderFactory(ClientHttpRequestFactory requestFactory) {
///     return new ConfigurableOidcIdTokenDecoderFactory(requestFactory);
/// }
/// ```
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

    /// @param oauthHttpRequestFactory the factory the jwk sets are fetched with
    public ConfigurableOidcIdTokenDecoderFactory(ClientHttpRequestFactory oauthHttpRequestFactory) {
        this.restTemplate = new RestTemplate(oauthHttpRequestFactory);
    }

    /// The default converters of the id token claims: `iss` to a `URL`; `aud` and `amr` to string
    /// collections; `nonce` to a string; `exp`, `iat`, `auth_time` and `updated_at` to `Instant`s;
    /// `email_verified` and `phone_number_verified` to booleans.
    ///
    /// @return the converters, keyed by claim name
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

    /// @param clientRegistration the registration whose id tokens are decoded
    /// @return the decoder for that registration id, created on first use
    /// @throws OAuth2AuthenticationException with error code `missing_signature_verifier` when no
    /// verifier can be made: a signature algorithm without a jwk set uri, a mac algorithm without a
    /// client secret, or no algorithm at all
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

    /// Sets the factory of the validators checking the verified id token. The default composes
    /// `JwtTimestampValidator` and `OidcIdTokenValidator`: a replacement should keep their checks.
    ///
    /// @param jwtValidatorFactory provides the validator for a registration
    public void setJwtValidatorFactory(Function<ClientRegistration, OAuth2TokenValidator<Jwt>> jwtValidatorFactory) {
        Assert.notNull(jwtValidatorFactory, "jwtValidatorFactory cannot be null");
        this.jwtValidatorFactory = jwtValidatorFactory;
    }

    /// Sets the resolver of the algorithm expected on a registration's id tokens, which decides how
    /// they are verified. The default resolves to RS256 for every registration.
    ///
    /// @param jwsAlgorithmResolver provides the expected algorithm for a registration
    public void setJwsAlgorithmResolver(Function<ClientRegistration, JwsAlgorithm> jwsAlgorithmResolver) {
        Assert.notNull(jwsAlgorithmResolver, "jwsAlgorithmResolver cannot be null");
        this.jwsAlgorithmResolver = jwsAlgorithmResolver;
    }

    /// Sets the factory of the converters of the id token claims. The default is a
    /// `ClaimTypeConverter` with [#createDefaultClaimTypeConverters()] for every registration; a
    /// factory returning `null` leaves the claims as parsed.
    ///
    /// @param claimTypeConverterFactory provides the converter for a registration
    public void setClaimTypeConverterFactory(Function<ClientRegistration, Converter<Map<String, Object>, Map<String, Object>>> claimTypeConverterFactory) {
        Assert.notNull(claimTypeConverterFactory, "claimTypeConverterFactory cannot be null");
        this.claimTypeConverterFactory = claimTypeConverterFactory;
    }
}
