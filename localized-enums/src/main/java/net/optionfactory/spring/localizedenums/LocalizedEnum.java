package net.optionfactory.spring.localizedenums;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/// Marks an enum for localization: [ResourceBundleEnumsLocalizationService] scans for it and
/// translates each constant under the `<prefix>.<category>.<NAME>` bundle key.
///
/// ```java
/// @LocalizedEnum(category = "order-status")
/// public enum OrderStatus {
///     PENDING, SHIPPED, DELIVERED
/// }
/// ```
///
/// Annotate enums only: a scanned class that is not an enum fails the construction of the service
/// with a `ClassCastException`.
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface LocalizedEnum {

    /// The category grouping the constants of the enum in the bundle keys and in
    /// [EnumsLocalizationService#values(java.util.Optional, java.util.Locale)]. Several enums may
    /// share one category, their constants are then listed together.
    ///
    /// @return the category, or blank (the default) to use the enum's simple name
    public String category() default "";

}
