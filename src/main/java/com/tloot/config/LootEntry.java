package com.tloot.config;

import org.bukkit.Material;

public class LootEntry {

    private final Material material;
    private final String command;
    private final int amountMin;
    private final int amountMax;
    private final int weight;

    /** 物品奖励 */
    public LootEntry(Material material, int amountMin, int amountMax, int weight) {
        this.material = material;
        this.command = null;
        this.amountMin = amountMin;
        this.amountMax = amountMax;
        this.weight = weight;
    }

    /** 指令奖励 */
    public LootEntry(String command, int weight) {
        this.material = null;
        this.command = command;
        this.amountMin = 0;
        this.amountMax = 0;
        this.weight = weight;
    }

    public boolean isCommand() {
        return command != null;
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
