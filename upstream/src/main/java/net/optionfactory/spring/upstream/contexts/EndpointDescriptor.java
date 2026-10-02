package net.optionfactory.spring.upstream.contexts;

import java.lang.reflect.Method;

/// Describes an endpoint of an upstream client, as computed once when the client is built.
///
/// @param upstream the upstream name, see [net.optionfactory.spring.upstream.Upstream]
/// @param name the endpoint name, see [net.optionfactory.spring.upstream.Upstream.Endpoint]
/// @param method the interface method
/// @param principalParamIndex the index of the `@Upstream.Principal` parameter, or `null` when there
/// is none
public record EndpointDescriptor(String upstream, String name, Method method, Integer principalParamIndex) {

}
