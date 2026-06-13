package net.optionfactory.spring.downstream.plugin.e2e;

import net.optionfactory.spring.downstream.Downstream;
import org.jspecify.annotations.Nullable;

public class ScanTarget {

    public enum Role {
        ADMIN, USER, GUEST
    }

    public record User(String id, Role role, @Nullable String email) {

    }

    public static class Page<T> {
        public T[] data;
        public int totalPages;
    }

    public static class E2eController {

        @Downstream.Method(clients = {"test-client"})
        public Page<User> getUsers() {
            return null;
        }

        @Downstream.Method(clients = {"test-client"})
        public void deleteUser(String id) {
        }

        @Downstream.Method(clients = {"other-client"})
        public String ignoredEndpoint() {
            return null;
        }
    }
}
