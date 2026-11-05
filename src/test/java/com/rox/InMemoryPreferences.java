package com.rox;

import java.util.HashMap;
import java.util.Map;
import java.util.prefs.AbstractPreferences;
import java.util.prefs.BackingStoreException;

/** {@link java.util.prefs.Preferences} kept in memory, so tests never touch the user's own. */
final class InMemoryPreferences extends AbstractPreferences {
    private final Map<String, String> values = new HashMap<>();
    private final Map<String, InMemoryPreferences> children = new HashMap<>();
    /** what {@link #flushSpi} throws, if anything */
    BackingStoreException flushFailure;
    int flushes;

    InMemoryPreferences(){
        this(null, "");
    }

    private InMemoryPreferences(final InMemoryPreferences parent, final String name){
        super(parent, name);
    }

    @Override
    protected void putSpi(final String key, final String value){
        values.put(key, value);
    }

    @Override
    protected String getSpi(final String key){
        return values.get(key);
    }

    @Override
    protected void removeSpi(final String key){
        values.remove(key);
    }

    @Override
    protected void removeNodeSpi(){
    }

    @Override
    protected String[] keysSpi(){
        return values.keySet().toArray(String[]::new);
    }

    @Override
    protected String[] childrenNamesSpi(){
        return children.keySet().toArray(String[]::new);
    }

    @Override
    protected AbstractPreferences childSpi(final String name){
        return children.computeIfAbsent(name, n -> new InMemoryPreferences(this, n));
    }

    @Override
    protected void syncSpi(){
    }

    @Override
    protected void flushSpi() throws BackingStoreException {
        flushes++;
        if (flushFailure != null){
            throw flushFailure;
        }
    }
}
