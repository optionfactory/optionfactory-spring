package net.optionfactory.spring.authentication;

import org.springframework.security.core.Authentication;

public interface PrincipalMapper<T, R> {

    R map(Authentication auth, T principal);
}
