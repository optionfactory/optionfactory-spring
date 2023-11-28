package net.optionfactory.spring.upstream.paths;

import com.fasterxml.jackson.databind.JsonNode;
import java.io.IOException;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import net.optionfactory.spring.upstream.UpstreamHttpInterceptor;
import net.optionfactory.spring.upstream.contexts.ResponseContext;

public class JsonPath {

    public static final MethodHandle JSON_PATH_METHOD_HANDLE = jsonPathMethodHandle();
    private final UpstreamHttpInterceptor.HttpMessageConverters converters;
    private final ResponseContext response;

    public JsonPath(UpstreamHttpInterceptor.HttpMessageConverters converters, ResponseContext response) {
        this.converters = converters;
        this.response = response;
    }

    public JsonNode path(String path) throws IOException {
        return converters.convert(response.body(), JsonNode.class, response.headers().getContentType()).findPath(path);
    }

    private static MethodHandle jsonPathMethodHandle() {
        try {
            return MethodHandles.publicLookup().findVirtual(JsonPath.class, "path", MethodType.methodType(JsonNode.class, String.class));
        } catch (ReflectiveOperationException ex) {
            throw new IllegalStateException(ex);
        }
    }

    public static MethodHandle boundMethodHandle(UpstreamHttpInterceptor.HttpMessageConverters converters, ResponseContext response) {
        return JSON_PATH_METHOD_HANDLE.bindTo(new JsonPath(converters, response));
    }

}
