package com.mcp.adapter;

import com.mcp.adapter.mysql.MySqlAdapter;
import com.mcp.adapter.tdengine.TDengineAdapter;
import com.mcp.adapter.spi.DatasourceAdapter;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

/**
 * 数据库适配器注册中心，按 dialectType 分发适配器实例，业务层通过此 Bean 获取具体实现。
 *
 * <p>当前已注册方言：
 * <ul>
 *   <li>{@code mysql} → {@link MySqlAdapter}</li>
 *   <li>{@code tdengine} → {@link com.mcp.adapter.tdengine.TDengineAdapter}</li>
 * </ul>
 *
 * <p>新增数据库方言时，在构造器中调用 {@link #register} 即可，无需修改任何业务代码。
 * @author ouyanghang
 */
@Component
public class AdapterRegistry {

    /** key 为 dialectType 小写字符串，与 {@link DatasourceAdapter#dialectType()} 一致。 */
    private final Map<String, DatasourceAdapter> registry = new HashMap<>();

    public AdapterRegistry() {
        register(new MySqlAdapter());
        register(new TDengineAdapter());
    }

    private void register(DatasourceAdapter adapter) {
        registry.put(adapter.dialectType(), adapter);
    }

    /**
     * 按方言类型获取适配器。
     *
     * @param dialectType 方言类型字符串（大小写不敏感），如 {@code "mysql"}、{@code "TDengine"}
     * @return 对应的适配器实例
     * @throws IllegalArgumentException 未注册该方言时抛出
     */
    public DatasourceAdapter getAdapter(String dialectType) {
        DatasourceAdapter adapter = registry.get(dialectType.toLowerCase());
        if (adapter == null) {
            throw new IllegalArgumentException("No adapter registered for dialect: " + dialectType);
        }
        return adapter;
    }
}


