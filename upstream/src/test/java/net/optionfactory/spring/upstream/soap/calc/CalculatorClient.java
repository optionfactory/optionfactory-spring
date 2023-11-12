package net.optionfactory.spring.upstream.soap.calc;

import net.optionfactory.spring.upstream.soap.UpstreamSoapAction;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.service.annotation.PostExchange;


public interface CalculatorClient {

    @PostExchange
    @UpstreamSoapAction("http://tempuri.org/Add")
    AddResponse add(@RequestBody Add ntw);
    
    @PostExchange
    @UpstreamSoapAction("http://tempuri.org/WrongSoapAction")
    AddResponse faultingAdd(@RequestBody Add ntw);


}
