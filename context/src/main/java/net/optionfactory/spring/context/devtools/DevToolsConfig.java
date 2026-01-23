package net.optionfactory.spring.context.devtools;

import org.springframework.context.annotation.ImportSelector;
import org.springframework.core.type.AnnotationMetadata;
import org.springframework.util.ClassUtils;

public class DevToolsConfig implements ImportSelector {

    @Override
    public String[] selectImports(AnnotationMetadata importingClassMetadata) {
        final var devToolsAutoConfigClass = "org.springframework.boot.devtools.autoconfigure.LocalDevToolsAutoConfiguration";
        if (ClassUtils.isPresent(devToolsAutoConfigClass, null)) {
            return new String[]{
                devToolsAutoConfigClass
            };
        }
        return new String[]{};
    }
}
