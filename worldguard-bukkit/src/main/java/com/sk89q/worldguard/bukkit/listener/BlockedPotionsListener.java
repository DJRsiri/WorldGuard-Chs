/*
 * WorldGuard, a suite of tools for Minecraft
 * Copyright (C) sk89q <http://www.sk89q.com>
 * Copyright (C) WorldGuard team and contributors
 *
 * This program is free software: you can redistribute it and/or modify it
 * under the terms of the GNU Lesser General Public License as published by the
 * Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE. See the GNU Lesser General Public License
 * for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */

package com.sk89q.worldguard.bukkit.listener;

import com.sk89q.worldguard.bukkit.BukkitWorldConfiguration;
import com.sk89q.worldguard.bukkit.WorldGuardPlugin;
import com.sk89q.worldguard.bukkit.event.entity.DamageEntityEvent;
import com.sk89q.worldguard.bukkit.event.inventory.UseItemEvent;
import com.sk89q.worldguard.bukkit.util.Entities;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.Arrow;
import org.bukkit.entity.Player;
import org.bukkit.entity.SpectralArrow;
import org.bukkit.event.EventHandler;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.PotionMeta;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.potion.PotionType;

import java.util.ArrayList;
import java.util.List;

/**
 * Handles blocked potions.
 */
public class BlockedPotionsListener extends AbstractListener {

    /**
     * Construct the listener.
     *
     * @param plugin an instance of WorldGuardPlugin
     */
    public BlockedPotionsListener(WorldGuardPlugin plugin) {
        super(plugin);
    }

    private PotionEffectType getBlockedEffectByArrow(Arrow arrow, BukkitWorldConfiguration wcfg) {
        List<PotionEffect> effects = new ArrayList<>();
        PotionType potionType = arrow.getBasePotionType();
        if (potionType != null) {
            effects.addAll(potionType.getPotionEffects());
        }
        effects.addAll(arrow.getCustomEffects());
        for (PotionEffect potionEffect : effects) {
            if (wcfg.blockPotions.contains(potionEffect.getType())) {
                return potionEffect.getType();
            }
        }
        return null;
    }


    @EventHandler
    public void onProjectile(DamageEntityEvent event) {
        if (!(event.getOriginalEvent() instanceof EntityDamageByEntityEvent originalEvent)) {
            return;
        }
        if (!Entities.isPotionArrow(originalEvent.getDamager())) {
            return;
        }

        BukkitWorldConfiguration wcfg = getWorldConfig(event.getWorld());
        PotionEffectType blockedEffect = null;
        if (originalEvent.getDamager() instanceof SpectralArrow) {
            if (wcfg.blockPotions.contains(PotionEffectType.GLOWING)) {
                blockedEffect = PotionEffectType.GLOWING;
            }
        } else if (originalEvent.getDamager() instanceof Arrow arrow) {
            blockedEffect = getBlockedEffectByArrow(arrow, wcfg);
        }
        if (blockedEffect != null) {
            Player player = event.getCause().getFirstPlayer();
            if (player != null) {
                if (getPlugin().hasPermission(player, "worldguard.override.potions")) {
                    return;
                }
                player.sendMessage(ChatColor.RED + "抱歉，带有 "
                        + blockedEffect.getName() + " 效果的箭矢目前已被禁用。");
            }
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onItemInteract(UseItemEvent event) {
        BukkitWorldConfiguration wcfg = getWorldConfig(event.getWorld());
        ItemStack item = event.getItemStack();

        if (item.getType() != Material.POTION
                && item.getType() != Material.SPLASH_POTION
                && item.getType() != Material.LINGERING_POTION) {
            return;
        }

        if (!wcfg.blockPotions.isEmpty()) {
            PotionEffectType blockedEffect = null;

            if (!(item.getItemMeta() instanceof PotionMeta meta)) {
                return;
            }

            // Find the first blocked effect
            List<PotionEffect> effects = new ArrayList<>();
            if (meta.getBasePotionType() != null) {
                effects.addAll(meta.getBasePotionType().getPotionEffects());
            }
            effects.addAll(meta.getCustomEffects());
            for (PotionEffect potionEffect : effects) {
                if (wcfg.blockPotions.contains(potionEffect.getType())) {
                    blockedEffect = potionEffect.getType();
                    break;
                }
            }

            if (blockedEffect != null) {
                Player player = event.getCause().getFirstPlayer();

                if (player != null) {
                    if (getPlugin().hasPermission(player, "worldguard.override.potions")) {
                        if (wcfg.blockPotionsAlways && (item.getType() == Material.SPLASH_POTION
                                || item.getType() == Material.LINGERING_POTION)) {
                            player.sendMessage(ChatColor.RED + "抱歉，带有 " +
                                    blockedEffect.getName() + " 效果的药水无法投掷，" +
                                    "即使你拥有绕过权限，" +
                                    "因为存在限制（且已开启过于严格的药水拦截）。");
                            event.setCancelled(true);
                        }
                    } else {
                        player.sendMessage(ChatColor.RED + "抱歉，带有 "
                                + blockedEffect.getName() + " 效果的药水目前已被禁用。");
                        event.setCancelled(true);
                    }
                } else {
                    event.setCancelled(true);
                }
            }
        }
    }

}
