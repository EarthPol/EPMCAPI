package com.earthpol.epmcapi.integration;

import net.milkbowl.vault.economy.Economy;
import org.bukkit.Bukkit;
import java.util.UUID;

/** Loaded only when Vault is enabled; call on the game thread. */
public final class EconomyIntegration {
    private EconomyIntegration() {}

    public static Double balance(UUID uuid) {
        var provider = Bukkit.getServicesManager().getRegistration(Economy.class);
        return provider == null ? null : provider.getProvider().getBalance(Bukkit.getOfflinePlayer(uuid));
    }
}
