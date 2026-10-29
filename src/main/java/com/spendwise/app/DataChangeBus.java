package com.spendwise.app;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Minimal application event bus used to keep Swing screens in sync.
 * When any entity changes (transaction, category, budget, settings) the
 * responsible service layer signals it here and every open screen refreshes
 * itself, so all dashboard values always come from the database.
 */
public final class DataChangeBus {

    public enum Topic {
        TRANSACTIONS,
        CATEGORIES,
        BUDGETS,
        SETTINGS
    }

    private final Map<Topic, List<Runnable>> listeners = new ConcurrentHashMap<>();

    public void on(Topic topic, Runnable listener) {
        listeners.computeIfAbsent(topic, key -> new CopyOnWriteArrayList<>()).add(listener);
    }

    public void fire(Topic topic) {
        for (Runnable listener : listeners.getOrDefault(topic, List.of())) {
            listener.run();
        }
    }

    /** Fires a change to every topic (used after settings/destructive operations). */
    public void fireAll() {
        for (Topic topic : Topic.values()) {
            fire(topic);
        }
    }
}
