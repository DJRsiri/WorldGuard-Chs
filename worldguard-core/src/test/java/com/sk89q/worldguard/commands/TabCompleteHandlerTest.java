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
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import javax.annotation.Nullable;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.empty;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TabCompleteHandlerTest {

    /** 可控的测试数据源 */
    private static final TabCompletionSource STUB = new TabCompletionSource() {
        @Override
        public List<String> getOnlinePlayerNames() {
            return Arrays.asList("Notch", "Herobrine", "Alex");
        }

        @Override
        public List<String> getWorldNames() {
            return Arrays.asList("world", "world_nether", "world_the_end");
        }

        @Override
        public List<String> getRegionNames(@Nullable String worldName) {
            return Arrays.asList("spawn", "arena", "myregion");
        }

        @Override
        public List<String> getFlagNames() {
            return Arrays.asList("pvp", "build", "greeting");
        }

        @Override
        @Nullable
        public String getCurrentWorldName(Actor actor) {
            return "world";
        }

        @Override
        public boolean hasPermission(Actor actor, @Nullable String permission) {
            return true; // 默认模拟全权限玩家
        }

        @Override
        @Nullable
        public List<String> getFlagValueSuggestions(String flagName) {
            if ("pvp".equals(flagName)) {
                return Arrays.asList("true", "false");
            }
            return null;
        }
    };

    /** 只放行 null 权限和 worldguard.region.define 的限制数据源，用于权限过滤测试 */
    private static final TabCompletionSource RESTRICTED = new TabCompletionSource() {
        @Override
        public List<String> getOnlinePlayerNames() {
            return Collections.emptyList();
        }

        @Override
        public List<String> getWorldNames() {
            return Collections.emptyList();
        }

        @Override
        public List<String> getRegionNames(@Nullable String worldName) {
            return Collections.emptyList();
        }

        @Override
        public List<String> getFlagNames() {
            return Collections.emptyList();
        }

        @Override
        @Nullable
        public String getCurrentWorldName(Actor actor) {
            return "world";
        }

        @Override
        public boolean hasPermission(Actor actor, @Nullable String permission) {
            return permission == null || "worldguard.region.define".equals(permission);
        }

        @Override
        @Nullable
        public List<String> getFlagValueSuggestions(String flagName) {
            return null;
        }
    };

    /** 所有权限都通过的 actor */
    private static final Actor ALLOW = null;

    private List<String> complete(String alias, String... args) {
        return TabCompleteHandler.complete(alias, args, ALLOW, STUB);
    }

    private List<String> completeWith(String alias, TabCompletionSource source, String... args) {
        return TabCompleteHandler.complete(alias, args, ALLOW, source);
    }

    @Test
    void completesAllSubcommandsOfRg() {
        List<String> result = complete("rg");
        assertThat(result, containsInAnyOrder(
                "define", "def", "d", "create",
                "redefine", "update", "move",
                "claim",
                "select", "sel", "s",
                "info", "i",
                "list",
                "flag", "f",
                "flags",
                "setpriority", "priority", "pri",
                "setparent", "parent", "par",
                "remove", "delete", "del", "rem",
                "load", "reload",
                "save", "write",
                "migratedb", "migrateuuid", "migrateheights",
                "teleport", "tp",
                "toggle-bypass", "bypass",
                "addmember", "addmem", "am",
                "addowner", "ao",
                "removemember", "remmember", "removemem", "remmem", "rm",
                "removeowner", "remowner", "ro"));
    }

    @Test
    void filtersSubcommandsByPrefix() {
        List<String> result = complete("rg", "de");
        assertTrue(result.contains("define"));
        assertTrue(result.contains("delete")); // delete 是 remove 的别名
        assertTrue(!result.contains("claim"));
    }

    @Test
    void filtersSubcommandsByPermission() {
        // RESTRICTED 只放行 null 权限和 worldguard.region.define
        List<String> result = completeWith("rg", RESTRICTED);
        assertTrue(result.contains("define")); // 有 worldguard.region.define
        assertTrue(result.contains("info"));   // 依赖区域，permission=null，不过滤
        assertTrue(!result.contains("claim")); // worldguard.region.claim 被拒
        assertTrue(!result.contains("load"));  // worldguard.region.load 被拒
    }

    @Test
    void completesRegionParam() {
        assertThat(complete("rg", "info", ""),
                containsInAnyOrder("spawn", "arena", "myregion"));
        assertThat(complete("rg", "info", "my"),
                containsInAnyOrder("myregion"));
    }

    @Test
    void doesNotCompleteNumericOrIdParams() {
        assertThat(complete("rg", "define", ""), empty()); // id 不补全
        assertThat(complete("rg", "setpriority", "spawn", ""), empty()); // priority 数字不补全
    }

    @Test
    void completesFlagNames() {
        assertThat(complete("rg", "flag", "spawn", ""),
                containsInAnyOrder("pvp", "build", "greeting"));
    }

    @Test
    void completesFlagValuesByFlagType() {
        assertThat(complete("rg", "flag", "spawn", "pvp", ""),
                containsInAnyOrder("true", "false"));
    }

    @Test
    void completesWorldForValueFlag() {
        assertThat(complete("rg", "load", "-w", ""),
                containsInAnyOrder("world", "world_nether", "world_the_end"));
    }

    @Test
    void completesEnumValues() {
        assertThat(complete("rg", "toggle-bypass", ""),
                containsInAnyOrder("on", "off"));
        assertThat(complete("rg", "migratedb", ""),
                containsInAnyOrder("yaml", "sql"));
    }

    @Test
    void completesPlayerNames() {
        assertThat(complete("god", "N"),
                containsInAnyOrder("Notch"));
        assertThat(complete("rg", "addmember", "spawn", ""),
                containsInAnyOrder("Notch", "Herobrine", "Alex"));
    }

    @Test
    void completesNestedDebugSubcommands() {
        assertThat(complete("wg", "debug", ""),
                containsInAnyOrder("testbreak", "testplace", "testinteract", "testdamage"));
    }

    @Test
    void completesTopLevelAliasVariants() {
        // region/regions 是 rg 的别名
        assertThat(complete("region", "info", "my"),
                containsInAnyOrder("myregion"));
        assertThat(complete("regions", "info", "my"),
                containsInAnyOrder("myregion"));
    }

    @Test
    void skipsValueFlagTokensWhenPositioning() {
        // -w world 后位置参数是 REGION
        assertThat(complete("rg", "info", "-w", "world_nether", "my"),
                containsInAnyOrder("myregion"));
    }

    @Test
    void returnsEmptyForUnknownTopLevelCommand() {
        assertThat(complete("nonexistent", ""), empty());
    }
}
