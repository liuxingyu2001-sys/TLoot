package com.tloot.config;

import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;

public class LootEntry {

    private final Material material;
    /** LiuLib 物品规格在解析期构建好的模板物品（发放时 clone）；null = 纯原版材质条目。 */
    private final ItemStack template;
    private final String command;
    private final int amountMin;
    private final int amountMax;
    private final int weight;

    /** 物品奖励（仅原版材质名，老路径）。 */
    public LootEntry(Material material, int amountMin, int amountMax, int weight) {
        this(null, material, amountMin, amountMax, weight);
    }

    /**
     * 物品奖励（LiuLib 完整物品规格，如 {@code base64:...} / {@code lib:...} / CE id）。
     *
     * @param template 由 LiuLib 在配置解析期构建的模板物品，发放时按数量 clone
     */
    public LootEntry(ItemStack template, int amountMin, int amountMax, int weight) {
        this(template, template.getType(), amountMin, amountMax, weight);
    }

    private LootEntry(ItemStack template, Material material,
                      int amountMin, int amountMax, int weight) {
        this.template = template;
        this.material = material;
        this.command = null;
        this.amountMin = amountMin;
        this.amountMax = amountMax;
        this.weight = weight;
    }

    /** 指令奖励 */
    public LootEntry(String command, int weight) {
        this.template = null;
        this.material = null;
        this.command = command;
        this.amountMin = 0;
        this.amountMax = 0;
        this.weight = weight;
    }

    public boolean isCommand() {
        return command != null;
    }

    /**
     * 按数量生成发放用物品。
     *
     * <p>LiuLib 规格条目从模板 clone（名称/Lore/附魔/PDC/数据组件与配置完全一致），超过单堆叠上限时
     * 拆成多堆，保证总数不丢失；纯原版材质条目保持原有 {@code new ItemStack(material, amount)} 行为。</p>
     */
    public List<ItemStack> createItems(int amount) {
        int count = Math.max(1, amount);
        List<ItemStack> out = new ArrayList<>();
        if (template != null) {
            int max = Math.max(1, template.getMaxStackSize());
            int remaining = count;
            while (remaining > 0) {
                ItemStack stack = template.clone();
                int take = Math.min(remaining, max);
                stack.setAmount(take);
                out.add(stack);
                remaining -= take;
            }
            return out;
        }
        if (material != null) {
            out.add(new ItemStack(material, count));
        }
        return out;
    }

    public Material getMaterial() {
        return material;
    }

    public String getCommand() {
        return command;
    }

    public int getAmountMin() {
        return amountMin;
    }

    public int getAmountMax() {
        return amountMax;
    }

    public int getWeight() {
        return weight;
    }
}
