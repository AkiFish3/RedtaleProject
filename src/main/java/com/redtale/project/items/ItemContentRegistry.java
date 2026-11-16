package com.redtale.project.items;

import com.redtale.project.core.config.ConfigFile;
import com.redtale.project.core.config.ConfigStore;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 全部已载入的内容定义（{@code items/*.json5}）。
 *
 * <p>不可变快照：reload 时整个换掉，正在跑的线程读旧快照也不会看到半份配置
 * （与 {@code AttackSpeedRules} 同样的做法）。内容 id 重复视为错误 —— 后者会静默覆盖前者，
 * 那是最难查的一类配置问题，宁可报出来。
 */
public final class ItemContentRegistry {

    public static final ItemContentRegistry EMPTY = new ItemContentRegistry(Map.of());

    private final Map<String, ItemContent> byId;

    private ItemContentRegistry(Map<String, ItemContent> byId) {
        this.byId = Collections.unmodifiableMap(byId);
    }

    /**
     * 从 {@code items/} 下的配置文件解析。
     *
     * <p>单个文件的语义错误只跳过那一条（R1-a）：一件装备的属性名写错，
     * 不该让整批内容都载不进来。
     */
    public static ItemContentRegistry load(List<ConfigFile> all, List<String> warnings) {
        Map<String, ItemContent> map = new LinkedHashMap<>();
        for (ConfigFile file : ConfigStore.ofDirectory(all, "items")) {
            ItemContent content = ItemContent.parse(file.root(), file.path(), warnings);
            if (content == null) {
                continue;
            }
            ItemContent prev = map.put(content.id(), content);
            if (prev != null) {
                warnings.add(file.path() + "：内容 id \"" + content.id() + "\" 与 "
                        + prev.source() + " 重复，后者覆盖前者");
            }
        }
        return new ItemContentRegistry(map);
    }

    public ItemContent get(String id) {
        return byId.get(id);
    }

    public int size() {
        return byId.size();
    }

    public List<ItemContent> all() {
        return List.copyOf(byId.values());
    }

    /** 只读视图（M3 镶嵌结算要按 id 批量查宝石定义，避免每颗都走一次 {@link #get}）。 */
    public Map<String, ItemContent> allById() {
        return byId;
    }
}
