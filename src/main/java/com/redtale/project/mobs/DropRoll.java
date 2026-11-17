package com.redtale.project.mobs;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Random;

/** 掉落掷骰（M6 第四层，纯函数）：一张掉落表 + 一个 Random → 这次的 xp 白值与聚合后件数。零 MC 依赖、无可变静态态。 */
public final class DropRoll {
    /** 一次掷骰的结果：某物品的最终件数（同 item 已聚合）。 */
    public record Rolled(String item, int count) {}
    /** 整张表的产出：xp 是白值（原样带出），items 不可变。 */
    public record Result(int xp, List<Rolled> items) {}

    private DropRoll() {}

    /** 按一张掉落表掷一次；def 为 null → Result(0, List.of())。random 为 null → 用 new Random(0L)。 */
    public static Result roll(DropSpec.Definition def, Random random) {
        if (def == null) return new Result(0, List.of());
        Random r = random != null ? random : new Random(0L);
        LinkedHashMap<String, Integer> sums = new LinkedHashMap<>();
        List<DropSpec.Entry> drops = def.drops();
        if (drops != null) {
            for (DropSpec.Entry e : drops) {
                if (e == null) continue;
                for (int n = 0; n < e.rolls(); n++) {
                    if (r.nextDouble() >= e.chance()) continue;
                    int amount = e.max() > e.min() ? e.min() + r.nextInt(e.max() - e.min() + 1) : e.min();
                    if (amount <= 0) continue;
                    Integer prev = sums.get(e.item());
                    sums.put(e.item(), prev == null ? amount : prev + amount);
                }
            }
        }
        List<Rolled> items = new ArrayList<>();
        for (String item : sums.keySet()) {
            int count = sums.get(item);
            if (count <= 0) continue;
            items.add(new Rolled(item, Math.min(64, count)));
        }
        return new Result(def.xp(), List.copyOf(items));
    }
}
