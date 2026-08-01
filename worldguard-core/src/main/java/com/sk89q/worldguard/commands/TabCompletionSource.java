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

package com.sk89q.worldguard.commands;

import com.sk89q.worldedit.extension.platform.Actor;

import java.util.List;
import javax.annotation.Nullable;

/**
 * 补全数据源 SPI。worldguard-core 定义接口，worldguard-bukkit 提供实现。
 */
public interface TabCompletionSource {

    List<String> getOnlinePlayerNames();

    List<String> getWorldNames();

    /**
     * @param worldName 目标世界名；null 表示当前世界
     */
    List<String> getRegionNames(@Nullable String worldName);

    List<String> getFlagNames();

    @Nullable
    String getCurrentWorldName(Actor actor);

    /**
     * @param permission null 表示无权限要求，恒返回 true
     */
    boolean hasPermission(Actor actor, @Nullable String permission);

    /**
     * 基于 flag 类型返回值候选；null 表示无候选（如 Integer/Location/自定义类型）
     */
    @Nullable
    List<String> getFlagValueSuggestions(String flagName);
}
