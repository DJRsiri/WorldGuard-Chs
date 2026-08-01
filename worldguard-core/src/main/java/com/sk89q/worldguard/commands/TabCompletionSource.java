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
