package net.optionfactory.spring.context.devtools;

import org.springframework.context.annotation.ImportSelector;
import org.springframework.core.type.AnnotationMetadata;
import org.springframework.util.ClassUtils;

/// Imports spring boot devtools' `LocalDevToolsAutoConfiguration` when devtools is on the
/// classpath, and nothing otherwise.
///
/// The configuration is referenced by name so that devtools can stay an optional, development-only
/// dependency: an application imports this selector unconditionally, and a production build that
/// leaves devtools out starts without it.
///
/// ```java
/// @Configuration
/// @Import(DevToolsImportSelector.class)
/// public class AppConfig {
/// }
/// ```
public class DevToolsImportSelector implements ImportSelector {

    /// @param importingClass the metadata of the importing class, unused
    /// @return the devtools auto-configuration class name when it is present, no imports otherwise
    @Override
    public String[] selectImports(AnnotationMetadata importingClass) {
        final var devToolsAutoConfigClass = "org.springframework.boot.devtools.autoconfigure.LocalDevToolsAutoConfiguration";
        if (!ClassUtils.isPresent(devToolsAutoConfigClass, null)) {
            return new String[0];
        }
        return new String[]{devToolsAutoConfigClass};
    }

}
