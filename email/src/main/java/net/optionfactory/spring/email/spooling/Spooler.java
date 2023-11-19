package net.optionfactory.spring.email.spooling;

import java.nio.file.Path;
import java.util.List;

public interface Spooler<T> {

    List<Path> spool(T value);
    
}
