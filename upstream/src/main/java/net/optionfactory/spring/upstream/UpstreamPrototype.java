package net.optionfactory.spring.upstream;

public interface UpstreamPrototype<T> {

    UpstreamBuilder<T> builder();
}
