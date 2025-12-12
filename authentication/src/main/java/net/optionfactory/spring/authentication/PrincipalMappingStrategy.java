package net.optionfactory.spring.authentication;

import org.springframework.security.core.Authentication;

public interface PrincipalMappingStrategy<T, R> extends PrincipalMapper<T, R> {

    boolean supports(Authentication auth, Object principal);


    public static class ByType<T, R> implements PrincipalMappingStrategy<T, R> {

        private final Class<T> type;
        private final PrincipalMapper<T, R> mapper;

        public ByType(Class<T> type, PrincipalMapper<T, R> mapper) {
            this.type = type;
            this.mapper = mapper;
        }

        @Override
        public boolean supports(Authentication auth, Object principal) {
            return type.isInstance(principal);
        }

        @Override
        public R map(Authentication auth, Object principal) {
            return mapper.map(auth, (T) principal);
        }

    }

    public static class ByInstance<R> implements PrincipalMappingStrategy<Object, R> {

        private final Object old;
        private final PrincipalMapper<Object, R> mapper;
        

        public ByInstance(Object old, PrincipalMapper<Object, R> mapper) {
            this.old = old;
            this.mapper = mapper;
        }

        @Override
        public boolean supports(Authentication auth, Object principal) {
            return old.equals(principal);
        }

        @Override
        public R map(Authentication auth, Object principal) {
            return mapper.map(auth, principal);
        }

    }
}
