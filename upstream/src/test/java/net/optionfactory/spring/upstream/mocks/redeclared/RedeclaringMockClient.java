package net.optionfactory.spring.upstream.mocks.redeclared;

import java.util.Map;
import net.optionfactory.spring.upstream.mocks.MockClient;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;

public interface RedeclaringMockClient extends MockClient {

    @Override
    ResponseEntity<Map<String, String>> add(@RequestParam String para, @PathVariable String parb);

}
