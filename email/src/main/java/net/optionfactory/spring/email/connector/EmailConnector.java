package net.optionfactory.spring.email.connector;

import net.optionfactory.spring.problems.Result;
import org.springframework.core.io.Resource;

public interface EmailConnector {

    Result<Void> send(Resource email);

}
