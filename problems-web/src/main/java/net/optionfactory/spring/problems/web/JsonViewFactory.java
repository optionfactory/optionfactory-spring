package net.optionfactory.spring.problems.web;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.function.Supplier;
import org.springframework.web.servlet.View;
import org.springframework.web.servlet.view.json.MappingJackson2JsonView;

public class JsonViewFactory implements Supplier<View> {

    private final ObjectMapper mapper;

    public JsonViewFactory(ObjectMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public View get() {
        final MappingJackson2JsonView view = new MappingJackson2JsonView();
        view.setExtractValueFromSingleKeyModel(true);
        view.setObjectMapper(mapper);
        view.setContentType("application/json;charset=UTF-8");
        return view;
    }

}
