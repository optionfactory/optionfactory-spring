package net.optionfactory.spring.authentication;

import org.springframework.security.core.Authentication;

public interface PrincipalMapper<T> {

    boolean supports(Authentication auth, Object principal);

    T map(Authentication auth, Object principal);

}
