package net.optionfactory.spring.context.devtools;

import org.springframework.context.annotation.ImportSelector;
import org.springframework.core.type.AnnotationMetadata;
import org.springframework.util.ClassUtils;

public class DevToolsImportSelector implements ImportSelector {

    @Override
    public String[] selectImports(AnnotationMetadata importingClass) {
        final var devToolsAutoConfigClass = "org.springframework.boot.devtools.autoconfigure.LocalDevToolsAutoConfiguration";
        if (!ClassUtils.isPresent(devToolsAutoConfigClass, null)) {
            return new String[0];
        }
        return new String[]{devToolsAutoConfigClass};
    }

}
