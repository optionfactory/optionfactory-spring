package net.optionfactory.spring.upstream.paths;

import java.io.IOException;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import net.optionfactory.spring.upstream.contexts.InvocationContext;
import net.optionfactory.spring.upstream.contexts.ResponseContext;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.MissingNode;

/// Looks up a field in a json response body; exposed to the response expressions as the
/// `#json_path('<field>')` function.
///
/// The argument is a field name, not a json path expression: the result is the value of the first
/// field with that name found anywhere in the document, depth first. Any failure (an unavailable
/// body, a body that is not json, a missing field) yields a `MissingNode`, whose `asBoolean()`,
/// `asString()` and `asInt()` return `false`, `""` and `0`, so that an expression never fails on
/// it.
///
/// ```java
/// @Upstream.ErrorOnResponse("#json_path('success').asBoolean() == false")
/// ```
public class JsonPath {

    /// The unbound [#path(String)] handle, from which [#boundMethodHandle] derives the expression
    /// function.
    public static final MethodHandle JSON_PATH_METHOD_HANDLE = jsonPathMethodHandle();
    private final InvocationContext.MessageConverters converters;
    private final ResponseContext response;

    /// @param converters the client's message converters, used to read the body as a `JsonNode`
    /// according to the response `Content-Type`
    /// @param response the response to inspect, whose body must be buffered
    public JsonPath(InvocationContext.MessageConverters converters, ResponseContext response) {
        this.converters = converters;
        this.response = response;
    }

    /// Parses the body again on every call.
    ///
    /// @param path the name of the field to look for
    /// @return the value of the first field with that name, or a `MissingNode` when there is none
    /// or the body cannot be read as json
    public JsonNode path(String path) {
        try {
            return converters.convert(response.body().forInspection(true).bytes(), JsonNode.class, response.headers()).findPath(path);
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

    /// @param converters the client's message converters
    /// @param response the response to inspect
    /// @return a `(String) -> JsonNode` handle looking up fields in the response, suitable for a
    /// SpEL function variable
    public static MethodHandle boundMethodHandle(InvocationContext.MessageConverters converters, ResponseContext response) {
        return JSON_PATH_METHOD_HANDLE.bindTo(new JsonPath(converters, response));
    }

}
