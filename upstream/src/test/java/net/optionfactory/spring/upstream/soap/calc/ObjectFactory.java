package net.optionfactory.spring.upstream.soap.calc;

import jakarta.xml.bind.annotation.XmlRegistry;

@XmlRegistry
public class ObjectFactory {
    public Add createAdd() {
        return new Add();
    }
    public AddResponse createAddResponse() {
        return new AddResponse();
    }
}
