package net.optionfactory.spring.upstream.contexts;

import java.lang.reflect.Method;

public record EndpointDescriptor(String upstream, String name, Method method, Integer principalParamIndex) {

}
