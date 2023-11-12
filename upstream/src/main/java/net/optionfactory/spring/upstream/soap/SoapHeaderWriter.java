package net.optionfactory.spring.upstream.soap;

import jakarta.xml.soap.SOAPHeader;

public interface SoapHeaderWriter {

    public static SoapHeaderWriter NONE = null;

    public void write(SOAPHeader header);
}
