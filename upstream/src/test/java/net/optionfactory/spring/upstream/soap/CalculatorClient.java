/*
 */
package net.optionfactory.spring.upstream.soap;

import net.optionfactory.spring.upstream.soap.calculator.Add;
import net.optionfactory.spring.upstream.soap.calculator.AddResponse;
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
