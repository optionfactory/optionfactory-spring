package net.optionfactory.spring.upstream.paths;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.MissingNode;
import java.io.IOException;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import net.optionfactory.spring.upstream.contexts.InvocationContext;
import net.optionfactory.spring.upstream.contexts.ResponseContext;

public class JsonPath {

    public static final MethodHandle JSON_PATH_METHOD_HANDLE = jsonPathMethodHandle();
    private final InvocationContext.HttpMessageConverters converters;
    private final ResponseContext response;

    public JsonPath(InvocationContext.HttpMessageConverters converters, ResponseContext response) {
        this.converters = converters;
        this.response = response;
    }

    public JsonNode path(String path) {
        try {
            return converters.convert(response.body().forInspection(true).bytes(), JsonNode.class, response.headers().getContentType()).findPath(path);
        } catch (IOException | RuntimeException ex) {
            return MissingNode.getInstance();
        }
    }

    private static MethodHandle jsonPathMethodHandle() {
        try {
            return MethodHandles.publicLookup().findVirtual(JsonPath.class, "path", MethodType.methodType(JsonNode.class, String.class));
        } catch (ReflectiveOperationException ex) {
            throw new IllegalStateException(ex);
        }
    }

    public static MethodHandle boundMethodHandle(InvocationContext.HttpMessageConverters converters, ResponseContext response) {
        return JSON_PATH_METHOD_HANDLE.bindTo(new JsonPath(converters, response));
    }

}
