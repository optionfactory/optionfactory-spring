package net.optionfactory.spring.downstream.plugin.processing;

import com.palantir.javapoet.ClassName;
import java.util.Collection;
import java.util.Map;
import net.optionfactory.spring.downstream.plugin.processing.PayloadsScanner.PayloadType;
import net.optionfactory.spring.downstream.plugin.processing.TypesMapper.PayloadInfo;

public class TypesRegistry {

    private final Map<Class<?>, PayloadInfo> mappings;

    public TypesRegistry(Map<Class<?>, PayloadInfo> mappings) {
        this.mappings = mappings;
    }

    public Collection<Map.Entry<Class<?>, PayloadInfo>> allMappings() {
        return mappings.entrySet();
    }

    public boolean isRegistered(Class<?> clazz) {
        return clazz != null && mappings.containsKey(clazz);
    }

    public boolean isRoot(Class<?> clazz) {
        Class<?> declaring = clazz.getDeclaringClass();
        return declaring == null || !isRegistered(declaring);
    }

    public ClassName getClassName(Class<?> clazz) {
        final var info = mappings.get(clazz);
        return info != null ? info.className() : null;
    }

    public PayloadType getType(Class<?> clazz) {
        final var info = mappings.get(clazz);
        return info != null ? info.type() : null;
    }
}
