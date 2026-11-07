package com.spectra.check;

import com.spectra.player.PlayerData;

import java.util.function.Function;

/** A registered check type: its metadata, current settings and a factory for per-player instances. */
public final class CheckDefinition {

    private final String id;
    private final String name;
    private final CheckCategory category;
    private final String description;
    private final boolean defaultExperimental;
    private final Function<PlayerData, ? extends Check> factory;
    private volatile CheckSettings settings;

    CheckDefinition(String id, String name, CheckCategory category, String description,
                    boolean defaultExperimental, Function<PlayerData, ? extends Check> factory) {
        this.id = id;
        this.name = name;
        this.category = category;
        this.description = description;
        this.defaultExperimental = defaultExperimental;
        this.factory = factory;
    }

    Check create(PlayerData data) {
        Check check = factory.apply(data);
        check.bind(this);
        return check;
    }

    public String id() { return id; }
    public String name() { return name; }
    public CheckCategory category() { return category; }
    public String description() { return description; }
    public boolean defaultExperimental() { return defaultExperimental; }
    public CheckSettings settings() { return settings; }

    void settings(CheckSettings settings) {
        this.settings = settings;
    }
}
