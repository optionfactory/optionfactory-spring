package net.optionfactory.spring.authentication;

import jakarta.servlet.FilterChain;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.authentication.AuthenticationTrustResolverImpl;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;

public class AuthenticationsCoalescingFilterTest {

    public record AppPrincipal(String id) {
    }

    private final FilterChain chain = (request, response) -> {
    };

    @BeforeEach
    public void clearContext() {
        SecurityContextHolder.clearContext();
    }

    private AuthenticationsCoalescingFilter<AppPrincipal> filter(List<PrincipalMappingStrategy<?, AppPrincipal>> mappers) {
        return new AuthenticationsCoalescingFilter<>(
                SecurityContextHolder.getContextHolderStrategy(),
                new HttpSessionSecurityContextRepository(),
                new AuthenticationTrustResolverImpl(),
                mappers,
                AppPrincipal.class);
    }

    private void run(AuthenticationsCoalescingFilter<AppPrincipal> filter) throws Exception {
        filter.doFilter(new MockHttpServletRequest(), new MockHttpServletResponse(), chain);
    }

    @Test
    public void aForeignPrincipalIsReplacedByTheApplicationsOwn() throws Exception {
        SecurityContextHolder.getContext().setAuthentication(
                new TestingAuthenticationToken("someone", "credentials", "ROLE_USER"));

        run(filter(List.of(new PrincipalMappingStrategy.ByType<>(String.class, (auth, s) -> new AppPrincipal(s)))));

        final var authentication = SecurityContextHolder.getContext().getAuthentication();
        Assertions.assertEquals(new AppPrincipal("someone"), authentication.getPrincipal(), "the principal is the one the mapping produced");
        Assertions.assertEquals("ROLE_USER", authentication.getAuthorities().iterator().next().getAuthority(), "the authorities are the original authentication's");
    }

    /// Spring's anonymous authentication carries the string `anonymousUser`, which is not an
    /// identity to normalise. Before this, an application that added the filter without a mapping
    /// for it failed every anonymous request with `unmappable principal`.
    @Test
    public void anAnonymousRequestIsLeftAloneWhenNothingMapsIt() throws Exception {
        final var anonymous = new AnonymousAuthenticationToken(
                "key", "anonymousUser", AuthorityUtils.createAuthorityList("ROLE_ANONYMOUS"));
        SecurityContextHolder.getContext().setAuthentication(anonymous);

        run(filter(List.of(new PrincipalMappingStrategy.ByType<>(Integer.class, (auth, i) -> new AppPrincipal(i.toString())))));

        Assertions.assertSame(anonymous, SecurityContextHolder.getContext().getAuthentication(), "an unmapped anonymous authentication is left as spring made it");
    }

    @Test
    public void anAnonymousRequestIsStillMappedWhenTheApplicationWantsAGuestPrincipal() throws Exception {
        SecurityContextHolder.getContext().setAuthentication(new AnonymousAuthenticationToken(
                "key", "anonymousUser", AuthorityUtils.createAuthorityList("ROLE_ANONYMOUS")));

        run(filter(List.of(new PrincipalMappingStrategy.ByInstance<>("anonymousUser", (auth, p) -> new AppPrincipal("guest")))));

        final var authentication = SecurityContextHolder.getContext().getAuthentication();
        Assertions.assertInstanceOf(AnonymousAuthenticationToken.class, authentication, "a mapped anonymous authentication is still recognisable as anonymous");
        Assertions.assertEquals(new AppPrincipal("guest"), authentication.getPrincipal(), "the anonymous principal is the one the mapping produced");
    }

    @Test
    public void anAuthenticatedPrincipalNothingMapsIsStillAMisconfiguration() {
        SecurityContextHolder.getContext().setAuthentication(
                new TestingAuthenticationToken(42, "credentials", "ROLE_USER"));

        final var filter = filter(List.of(new PrincipalMappingStrategy.ByType<>(String.class, (auth, s) -> new AppPrincipal(s))));

        Assertions.assertThrows(IllegalStateException.class, () -> run(filter), "an authenticated principal no strategy supports fails the request");
    }

    @Test
    public void anApplicationPrincipalIsLeftUntouched() throws Exception {
        final var already = new TestingAuthenticationToken(new AppPrincipal("x"), "credentials", "ROLE_USER");
        SecurityContextHolder.getContext().setAuthentication(already);

        run(filter(List.of()));

        Assertions.assertSame(already, SecurityContextHolder.getContext().getAuthentication(), "a principal already of the application type is not wrapped");
    }

    @Test
    public void aRequestWithoutAuthenticationProceedsUnauthenticated() throws Exception {
        final var proceeded = new AtomicBoolean(false);
        final var filter = filter(List.of(new PrincipalMappingStrategy.ByType<>(String.class, (auth, s) -> new AppPrincipal(s))));

        filter.doFilter(new MockHttpServletRequest(), new MockHttpServletResponse(), (request, response) -> proceeded.set(true));

        Assertions.assertTrue(proceeded.get(), "a request without authentication proceeds down the chain");
        Assertions.assertNull(SecurityContextHolder.getContext().getAuthentication(), "no authentication is made up for an unauthenticated request");
    }

    @Test
    public void anAuthenticatedPrincipalIsWrappedKeepingTheOriginalAsSource() throws Exception {
        final var original = new TestingAuthenticationToken("someone", "credentials", "ROLE_USER");
        SecurityContextHolder.getContext().setAuthentication(original);

        run(filter(List.of(new PrincipalMappingStrategy.ByType<>(String.class, (auth, s) -> new AppPrincipal(s)))));

        final var coalesced = Assertions.assertInstanceOf(CoalescingAuthentication.class, SecurityContextHolder.getContext().getAuthentication(), "an authenticated principal is wrapped in a CoalescingAuthentication");
        Assertions.assertSame(original, coalesced.source(), "the original authentication stays reachable as the source");
    }

    @Test
    public void theFirstSupportingStrategyMapsThePrincipal() throws Exception {
        SecurityContextHolder.getContext().setAuthentication(new TestingAuthenticationToken("someone", "credentials", "ROLE_USER"));

        run(filter(List.of(
                new PrincipalMappingStrategy.ByType<>(CharSequence.class, (auth, s) -> new AppPrincipal("first")),
                new PrincipalMappingStrategy.ByType<>(String.class, (auth, s) -> new AppPrincipal("second")))));

        Assertions.assertEquals(new AppPrincipal("first"), SecurityContextHolder.getContext().getAuthentication().getPrincipal(), "strategies are consulted in order and the first supporting one wins");
    }

    @Test
    public void aStrategyMappingAnAuthenticatedPrincipalToNullIsAMisconfiguration() {
        SecurityContextHolder.getContext().setAuthentication(new TestingAuthenticationToken("someone", "credentials", "ROLE_USER"));

        final var filter = filter(List.of(
                new PrincipalMappingStrategy.ByType<>(String.class, (auth, s) -> null),
                new PrincipalMappingStrategy.ByType<>(Object.class, (auth, s) -> new AppPrincipal("fallback"))));

        Assertions.assertThrows(IllegalStateException.class, () -> run(filter), "a null mapping fails the request instead of falling through to later strategies");
    }

    @Test
    public void theCoalescedContextIsSavedToTheRepository() throws Exception {
        SecurityContextHolder.getContext().setAuthentication(new TestingAuthenticationToken("someone", "credentials", "ROLE_USER"));
        final var saved = new AtomicReference<SecurityContext>();
        final var repository = new HttpSessionSecurityContextRepository() {
            @Override
            public void saveContext(SecurityContext context, HttpServletRequest request, HttpServletResponse response) {
                saved.set(context);
            }
        };
        final var filter = new AuthenticationsCoalescingFilter<>(
                SecurityContextHolder.getContextHolderStrategy(),
                repository,
                new AuthenticationTrustResolverImpl(),
                List.<PrincipalMappingStrategy<?, AppPrincipal>>of(new PrincipalMappingStrategy.ByType<>(String.class, (auth, s) -> new AppPrincipal(s))),
                AppPrincipal.class);

        run(filter);

        Assertions.assertNotNull(saved.get(), "the replaced context is saved, so that later requests load it already coalesced");
        Assertions.assertEquals(new AppPrincipal("someone"), saved.get().getAuthentication().getPrincipal(), "the saved context carries the mapped principal");
    }

}
