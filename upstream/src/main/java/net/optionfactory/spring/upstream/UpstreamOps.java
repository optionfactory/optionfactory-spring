package net.optionfactory.spring.upstream;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import net.optionfactory.spring.upstream.UpstreamInterceptor.PrepareContext;
import net.optionfactory.spring.upstream.UpstreamInterceptor.RequestContext;
import net.optionfactory.spring.upstream.UpstreamInterceptor.ResponseContext;
import org.springframework.lang.Nullable;

public class UpstreamOps {

    public static <CTX> boolean defaultFaultStrategy(PrepareContext<CTX> prepare, RequestContext request, ResponseContext response) {
        if (response.status == null) {
            return false;
        }
        return response.status.is4xxClientError() || response.status.is5xxServerError();
    }

    public static <CTX> String defaultRequestToString(PrepareContext<CTX> prepare, RequestContext request, ResponseContext response) {
        try ( var is = request.body.getInputStream()) {
            return copyToString(is, StandardCharsets.UTF_8, 256*1024);
        } catch (IOException ex) {
            return "(binary)";
        }

    }

    public static <CTX> String defaultResponseToString(PrepareContext<CTX> prepare, RequestContext request, ResponseContext response) {
        try ( var is = response.body.getInputStream()) {
            return copyToString(is, StandardCharsets.UTF_8, 256*1024);
        } catch (IOException ex) {
            return "(binary)";
        }

    }

    public static String copyToString(@Nullable InputStream in, Charset charset, int maxChars) throws IOException {
        if (in == null) {
            return "";
        }
        final StringBuilder out = new StringBuilder(4096);
        final InputStreamReader reader = new InputStreamReader(in, charset);
        final char[] buffer = new char[4096];
        int charsRead;
        while ((charsRead = reader.read(buffer)) != -1 && maxChars > 0) {
            out.append(buffer, 0, Math.min(charsRead, maxChars));
            maxChars -= charsRead;
        }
        return out.toString();
    }

}
