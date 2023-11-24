package net.optionfactory.spring.upstream.soap.calc;

import net.optionfactory.spring.upstream.Upstream;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.service.annotation.PostExchange;

public interface CalculatorClient {

    @PostExchange
    @Upstream.SoapAction("http://tempuri.org/Add")
    AddResponse add(@RequestBody Add ntw);

    @PostExchange
    @Upstream.SoapAction("http://tempuri.org/WrongSoapAction")
    AddResponse faultingAdd(@RequestBody Add ntw);

}
