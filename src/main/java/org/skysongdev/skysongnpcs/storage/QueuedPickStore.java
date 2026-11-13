package org.skysongdev.skysongnpcs.storage;

import org.skysongdev.skysongnpcs.picks.PickGrant;
import org.skysongdev.skysongnpcs.picks.PickSpend;
import org.skysongdev.skysongnpcs.picks.PromotionSpend;

import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Consumer;

/** Runs another pick store's writes in the background, the way {@link QueuedNpcStore} does. */
public final class QueuedPickStore implements PickStore {
    private final PickStore store;
    private final BackgroundWriter writer;

    public QueuedPickStore(PickStore store, Consumer<RuntimeException> onFailure) {
        this.store = Objects.requireNonNull(store, "store");
        this.writer = new BackgroundWriter("SkysongNPCs pick writer", onFailure);
    }

    @Override
    public List<PickGrant> loadGrants() {
        return store.loadGrants();
    }

    @Override
    public List<PickSpend> loadSpends() {
        return store.loadSpends();
    }

    @Override
    public List<PromotionSpend> loadPromotions() {
        return store.loadPromotions();
    }

    @Override
    public void savePromotion(PromotionSpend promotion) {
        writer.submit(() -> store.savePromotion(promotion));
    }

    @Override
    public void deletePromotion(UUID grant, int step) {
        writer.submit(() -> store.deletePromotion(grant, step));
    }

    @Override
    public void saveGrant(PickGrant grant) {
        writer.submit(() -> store.saveGrant(grant));
    }

    @Override
    public void deleteGrant(UUID grant) {
        writer.submit(() -> store.deleteGrant(grant));
    }

    @Override
    public void saveSpend(PickSpend spend) {
        writer.submit(() -> store.saveSpend(spend));
    }

    @Override
    public void deleteSpend(UUID grant, String proficiency) {
        writer.submit(() -> store.deleteSpend(grant, proficiency));
    }

    @Override
    public void deleteAll(UUID npc) {
        writer.submit(() -> store.deleteAll(npc));
    }

    @Override
    public void close() {
        try {
            writer.close();
        } finally {
            store.close();
        }
    }
}
