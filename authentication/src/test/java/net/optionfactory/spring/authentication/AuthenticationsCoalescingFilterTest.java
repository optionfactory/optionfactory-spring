package net.optionfactory.spring.authentication;

import jakarta.servlet.FilterChain;
import java.util.List;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.authentication.AuthenticationTrustResolverImpl;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.authority.AuthorityUtils;
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
        Assertions.assertEquals(new AppPrincipal("someone"), authentication.getPrincipal());
        Assertions.assertEquals("ROLE_USER", authentication.getAuthorities().iterator().next().getAuthority());
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

        Assertions.assertSame(anonymous, SecurityContextHolder.getContext().getAuthentication());
    }

    @Test
    public void anAnonymousRequestIsStillMappedWhenTheApplicationWantsAGuestPrincipal() throws Exception {
        SecurityContextHolder.getContext().setAuthentication(new AnonymousAuthenticationToken(
                "key", "anonymousUser", AuthorityUtils.createAuthorityList("ROLE_ANONYMOUS")));

        run(filter(List.of(new PrincipalMappingStrategy.ByInstance<>("anonymousUser", (auth, p) -> new AppPrincipal("guest")))));

        final var authentication = SecurityContextHolder.getContext().getAuthentication();
        Assertions.assertInstanceOf(AnonymousAuthenticationToken.class, authentication);
        Assertions.assertEquals(new AppPrincipal("guest"), authentication.getPrincipal());
    }

    @Test
    public void anAuthenticatedPrincipalNothingMapsIsStillAMisconfiguration() {
        SecurityContextHolder.getContext().setAuthentication(
                new TestingAuthenticationToken(42, "credentials", "ROLE_USER"));

        final var filter = filter(List.of(new PrincipalMappingStrategy.ByType<>(String.class, (auth, s) -> new AppPrincipal(s))));

        Assertions.assertThrows(IllegalStateException.class, () -> run(filter));
    }

    @Test
    public void anApplicationPrincipalIsLeftUntouched() throws Exception {
        final var already = new TestingAuthenticationToken(new AppPrincipal("x"), "credentials", "ROLE_USER");
        SecurityContextHolder.getContext().setAuthentication(already);

        run(filter(List.of()));

        Assertions.assertSame(already, SecurityContextHolder.getContext().getAuthentication());
    }

}
