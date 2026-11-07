package com.spectra.check;

public enum CheckCategory {
    COMBAT("Combat"),
    MOVEMENT("Movement"),
    PLAYER("Player"),
    PACKET("Packet");

    private final String displayName;

    CheckCategory(String displayName) {
        this.displayName = displayName;
    }

    public String displayName() {
        return displayName;
    }
}
