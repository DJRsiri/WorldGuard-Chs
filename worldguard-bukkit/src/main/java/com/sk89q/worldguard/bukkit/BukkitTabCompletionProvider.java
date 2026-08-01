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

package com.sk89q.worldguard.bukkit;

import com.sk89q.worldedit.extension.platform.Actor;
import com.sk89q.worldedit.world.World;
import com.sk89q.worldguard.LocalPlayer;
import com.sk89q.worldguard.WorldGuard;
import com.sk89q.worldguard.commands.TabCompletionSource;
import com.sk89q.worldguard.protection.flags.EnumFlag;
import com.sk89q.worldguard.protection.flags.Flag;
import com.sk89q.worldguard.protection.flags.StateFlag;
import com.sk89q.worldguard.protection.managers.RegionManager;
import com.sk89q.worldguard.protection.regions.RegionContainer;
import org.bukkit.Bukkit;

import java.util.ArrayList;
import java.util.List;
import javax.annotation.Nullable;

/**
 * Bukkit 实现的补全数据源。
 */
public final class BukkitTabCompletionProvider implements TabCompletionSource {

    @Override
    public List<String> getOnlinePlayerNames() {
        List<String> names = new ArrayList<>();
        Bukkit.getOnlinePlayers().forEach(p -> names.add(p.getName()));
        return names;
    }

    @Override
    public List<String> getWorldNames() {
        List<String> names = new ArrayList<>();
        Bukkit.getWorlds().forEach(w -> names.add(w.getName()));
        return names;
    }

    @Override
    public List<String> getRegionNames(@Nullable String worldName) {
        List<String> names = new ArrayList<>();
        RegionContainer container = WorldGuard.getInstance().getPlatform().getRegionContainer();
        if (worldName != null) {
            World world = WorldGuard.getInstance().getPlatform().getMatcher().getWorldByName(worldName);
            if (world == null) {
                return names;
            }
            RegionManager manager = container.get(world);
            if (manager != null) {
                names.addAll(manager.getRegions().keySet());
            }
        } else {
            for (RegionManager manager : container.getLoaded()) {
                manager.getRegions().keySet().forEach(names::add);
            }
        }
        return names;
    }

    @Override
    public List<String> getFlagNames() {
        List<String> names = new ArrayList<>();
        WorldGuard.getInstance().getFlagRegistry().forEach(flag -> names.add(flag.getName()));
        return names;
    }

    @Override
    @Nullable
    public String getCurrentWorldName(Actor actor) {
        // Actor 接口不声明 getWorld()；LocalPlayer extends Player 提供 getWorld()
        if (actor instanceof LocalPlayer localPlayer) {
            World world = localPlayer.getWorld();
            return world != null ? world.getName() : null;
        }
        return null;
    }

    @Override
    public boolean hasPermission(Actor actor, @Nullable String permission) {
        return permission == null || actor.hasPermission(permission);
    }

    @Override
    @Nullable
    public List<String> getFlagValueSuggestions(String flagName) {
        Flag<?> flag = WorldGuard.getInstance().getFlagRegistry().get(flagName);
        if (flag instanceof StateFlag) {
            return List.of("true", "false");
        }
        if (flag instanceof EnumFlag) {
            List<String> values = new ArrayList<>();
            for (Object constant : ((EnumFlag<?>) flag).getEnumClass().getEnumConstants()) {
                values.add(((Enum<?>) constant).name());
            }
            return values;
        }
        return null;
    }
}
