package net.optionfactory.spring.upstream.contexts;

import java.time.Instant;

public record ExceptionContext(Instant at, String message){

}
