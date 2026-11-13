package org.skysongdev.skysongnpcs.storage;

import org.skysongdev.skysongnpcs.picks.PickGrant;
import org.skysongdev.skysongnpcs.picks.PickSpend;
import org.skysongdev.skysongnpcs.picks.PromotionSpend;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Keeps proficiency picks in memory, for tests that have no database. */
public class InMemoryPickStore implements PickStore {
    private final Map<UUID, PickGrant> grants = new LinkedHashMap<>();
    private final List<PickSpend> spends = new ArrayList<>();
    private final List<PromotionSpend> promotions = new ArrayList<>();

    @Override
    public synchronized List<PromotionSpend> loadPromotions() {
        return new ArrayList<>(promotions);
    }

    @Override
    public synchronized void savePromotion(PromotionSpend promotion) {
        deletePromotion(promotion.grant(), promotion.step());
        promotions.add(promotion);
    }

    @Override
    public synchronized void deletePromotion(UUID grant, int step) {
        promotions.removeIf(saved -> saved.grant().equals(grant) && saved.step() == step);
    }

    @Override
    public synchronized List<PickGrant> loadGrants() {
        return new ArrayList<>(grants.values());
    }

    @Override
    public synchronized List<PickSpend> loadSpends() {
        return new ArrayList<>(spends);
    }

    @Override
    public synchronized void saveGrant(PickGrant grant) {
        grants.put(grant.id(), grant);
    }

    @Override
    public synchronized void deleteGrant(UUID grant) {
        grants.remove(grant);
        spends.removeIf(spend -> spend.grant().equals(grant));
        promotions.removeIf(promotion -> promotion.grant().equals(grant));
    }

    @Override
    public synchronized void saveSpend(PickSpend spend) {
        spends.removeIf(saved -> saved.grant().equals(spend.grant()) && saved.proficiency().equals(spend.proficiency()));
        spends.add(spend);
    }

    @Override
    public synchronized void deleteSpend(UUID grant, String proficiency) {
        spends.removeIf(spend -> spend.grant().equals(grant) && spend.proficiency().equals(proficiency));
    }

    @Override
    public synchronized void deleteAll(UUID npc) {
        grants.values().removeIf(grant -> {
            if (grant.npc().equals(npc)) {
                spends.removeIf(spend -> spend.grant().equals(grant.id()));
                promotions.removeIf(promotion -> promotion.grant().equals(grant.id()));
                return true;
            }
            return false;
        });
    }

    @Override
    public void close() {
    }
}
