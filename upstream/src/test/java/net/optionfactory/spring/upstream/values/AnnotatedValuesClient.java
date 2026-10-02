package net.optionfactory.spring.upstream.values;

import net.optionfactory.spring.upstream.Upstream;
import net.optionfactory.spring.upstream.expressions.Expressions;

public interface AnnotatedValuesClient {

    @Upstream.Header(key = "X-#{#endpoint}", value = "#name")
    @Upstream.Header(key = "X-Static", value = "'skipped'", condition = "#name == 'nobody'")
    @Upstream.Header(key = "X-Static", value = "'kept'")
    @Upstream.Cookie(value = "first=#{#name}")
    @Upstream.Cookie(value = "skipped=1", condition = "false")
    @Upstream.Cookie(value = "second=2")
    @Upstream.QueryParam(key = "q", value = "#name")
    @Upstream.QueryParam(key = "skipped", value = "'1'", condition = "false")
    @Upstream.QueryParam(key = "page", value = "2", valueType = Expressions.Type.STATIC)
    @Upstream.PathVariable(key = "id", value = "#name")
    @Upstream.PathVariable(key = "id", value = "#name + '-last'")
    void annotated(String name);

    @Upstream.QueryParam(key = "skipped", value = "'1'", condition = "false")
    void conditionsNeverHold(String name);

    void bare(String name);
}
