package net.optionfactory.spring.upstream.buffering;

import java.io.FilterInputStream;
import java.io.IOException;
import org.springframework.http.client.ClientHttpResponse;

public class HttpInputMessageInputStream extends FilterInputStream {

    private final ClientHttpResponse chr;

    public HttpInputMessageInputStream(ClientHttpResponse chr) throws IOException {
        super(chr.getBody());
        this.chr = chr;
    }

    @Override
    public void close() throws IOException {
        super.close();
        chr.close();
    }

}
