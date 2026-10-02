package net.optionfactory.spring.upstream.buffering;

import java.io.FilterInputStream;
import java.io.IOException;
import org.springframework.http.client.ClientHttpResponse;

/// The body of a response, closing the response itself when closed: handing it to a caller hands
/// over the connection, released when the caller closes the stream.
public class HttpInputMessageInputStream extends FilterInputStream {

    private final ClientHttpResponse chr;

    /// @param chr the response whose body is exposed
    /// @throws IOException when the body cannot be obtained
    public HttpInputMessageInputStream(ClientHttpResponse chr) throws IOException {
        super(chr.getBody());
        this.chr = chr;
    }

    /// Closes the body and then the response.
    ///
    /// @throws IOException when the body cannot be closed
    @Override
    public void close() throws IOException {
        super.close();
        chr.close();
    }

}
