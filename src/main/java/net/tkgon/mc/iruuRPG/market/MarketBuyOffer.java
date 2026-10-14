package net.tkgon.mc.iruuRPG.market;

import org.bukkit.inventory.ItemStack;

/** The operator ("iruuRPG") buys this item from players at a fixed price. {@code remaining < 0} means no limit. */
public final class MarketBuyOffer {

    private final long id;
    private final ItemStack item; // one piece
    private final long unitPrice;
    private int remaining;

    public MarketBuyOffer(long id, ItemStack item, long unitPrice, int remaining) {
        this.id = id;
        this.item = item.clone();
        this.item.setAmount(1);
        this.unitPrice = unitPrice;
        this.remaining = remaining;
    }

    public long id() {
        return id;
    }

    public ItemStack item() {
        return item.clone();
    }

    public long unitPrice() {
        return unitPrice;
    }

    public int remaining() {
        return remaining;
    }

    public void setRemaining(int remaining) {
        this.remaining = remaining;
    }

    public boolean unlimited() {
        return remaining < 0;
    }
}
