package net.tkgon.mc.iruuRPG.mob;

import java.util.concurrent.ThreadLocalRandom;

public record MobDropItem(
        String itemId,
        double dropChance,
        int minAmount,
        int maxAmount
) {
    public MobDropItem {
        if (itemId == null || itemId.isBlank()) {
            throw new IllegalArgumentException("Drop item id must not be blank");
        }

        itemId = itemId.trim();
        dropChance = normalizeChance(dropChance);
        minAmount = Math.max(1, minAmount);
        maxAmount = Math.max(minAmount, maxAmount);
    }

    public boolean rolls(ThreadLocalRandom random) {
        return dropChance >= 1.0 || random.nextDouble() < dropChance;
    }

    public int rollAmount(ThreadLocalRandom random) {
        if (minAmount == maxAmount) return minAmount;
        return random.nextInt(minAmount, maxAmount + 1);
    }

    private static double normalizeChance(double raw) {
        double value = raw > 1.0 ? raw / 100.0 : raw;
        return Math.max(0.0, Math.min(1.0, value));
    }
}
