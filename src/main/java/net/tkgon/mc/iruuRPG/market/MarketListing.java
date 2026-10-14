package net.tkgon.mc.iruuRPG.market;

import org.bukkit.inventory.ItemStack;

import java.util.UUID;

/** One item stack for sale: who sells it, how many are left, the unit price and when it can be bought. */
public final class MarketListing {

    private final long id;
    private final UUID sellerId;
    private final String sellerName;
    private final ItemStack item; // one piece; the amount is {@link #remaining}
    private int remaining;
    private final long unitPrice;
    private final long listedAt;
    private final long availableAt;
    private final long expiresAt;

    public MarketListing(long id, UUID sellerId, String sellerName, ItemStack item, int remaining, long unitPrice,
                         long listedAt, long availableAt, long expiresAt) {
        this.id = id;
        this.sellerId = sellerId;
        this.sellerName = sellerName;
        this.item = item.clone();
        this.item.setAmount(1);
        this.remaining = remaining;
        this.unitPrice = unitPrice;
        this.listedAt = listedAt;
        this.availableAt = availableAt;
        this.expiresAt = expiresAt;
    }

    public long id() {
        return id;
    }

    public UUID sellerId() {
        return sellerId;
    }

    public String sellerName() {
        return sellerName;
    }

    /** A copy of the item with amount 1. */
    public ItemStack item() {
        return item.clone();
    }

    public int remaining() {
        return remaining;
    }

    public void setRemaining(int remaining) {
        this.remaining = remaining;
    }

    public long unitPrice() {
        return unitPrice;
    }

    public long listedAt() {
        return listedAt;
    }

    public long availableAt() {
        return availableAt;
    }

    public long expiresAt() {
        return expiresAt;
    }

    public boolean isAvailable(long now) {
        return now >= availableAt && now < expiresAt;
    }

    public boolean isExpired(long now) {
        return now >= expiresAt;
    }
}
