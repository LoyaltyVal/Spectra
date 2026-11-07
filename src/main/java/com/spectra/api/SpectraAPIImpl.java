package com.spectra.api;

import com.spectra.Spectra;
import com.spectra.check.Check;
import com.spectra.player.PlayerData;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

public final class SpectraAPIImpl implements SpectraAPI {

    private final Spectra plugin;

    public SpectraAPIImpl(Spectra plugin) {
        this.plugin = plugin;
    }

    @Override
    public Collection<String> getCheckIds() {
        return plugin.checks().ids();
    }

    @Override
    public boolean isTracked(UUID player) {
        return plugin.players().get(player) != null;
    }

    @Override
    public double getViolationLevel(UUID player, String checkId) {
        PlayerData data = plugin.players().get(player);
        if (data == null) return 0;
        Check check = data.check(checkId);
        return check == null ? 0 : check.vl();
    }

    @Override
    public Map<String, Double> getViolationLevels(UUID player) {
        Map<String, Double> levels = new LinkedHashMap<>();
        PlayerData data = plugin.players().get(player);
        if (data == null) return levels;
        for (Check check : data.checks()) {
            if (check.vl() > 0) levels.put(check.id(), check.vl());
        }
        return levels;
    }

    @Override
    public void resetViolations(UUID player) {
        PlayerData data = plugin.players().get(player);
        if (data != null) plugin.violations().reset(data, null);
    }

    @Override
    public boolean isExempt(UUID player, String checkId) {
        return plugin.exemptions().isExempt(player, checkId);
    }

    @Override
    public void setExempt(UUID player, String checkId, boolean exempt) {
        plugin.exemptions().set(player, checkId, exempt);
    }
}
