package net.optionfactory.spring.upstream;

import java.util.Map;

public interface UpstreamContextTransformer<CTX> {

    Map<String, String> toMap(CTX ctx);

    CTX fromMap(Map<String, String> map);

    String toLogPrefix(CTX ctx);

    public static class Null<T> implements UpstreamContextTransformer<T> {

        @Override
        public String toLogPrefix(T ctx) {
            return "";
        }

        @Override
        public Map<String, String> toMap(T ctx) {
            return Map.of();
        }

        @Override
        public T fromMap(Map<String, String> map) {
            return null;
        }

    }

}
