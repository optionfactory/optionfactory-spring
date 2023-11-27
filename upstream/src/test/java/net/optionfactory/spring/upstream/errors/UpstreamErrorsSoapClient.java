package net.optionfactory.spring.upstream.errors;

import net.optionfactory.spring.upstream.Upstream;
import net.optionfactory.spring.upstream.soap.calc.AddResponse;
import org.springframework.web.service.annotation.GetExchange;

public interface UpstreamErrorsSoapClient {

    @GetExchange
    @Upstream.Error("""
                    #xpath_bool('//AddResult/text() != "8"')
                    """)
    AddResponse callWithXpath();

}
