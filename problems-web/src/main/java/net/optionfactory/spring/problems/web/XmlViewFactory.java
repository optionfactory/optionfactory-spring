package net.optionfactory.spring.problems.web;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.function.Supplier;
import org.springframework.web.servlet.View;
import org.springframework.web.servlet.view.xml.MappingJackson2XmlView;

public class XmlViewFactory implements Supplier<View> {

    private final ObjectMapper mapper;

    public XmlViewFactory(ObjectMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public View get() {
        final MappingJackson2XmlView view = new MappingJackson2XmlView();
        view.setObjectMapper(mapper);
        view.setContentType("application/xml;charset=UTF-8");
        return view;
    }

}
