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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.annotation.Nullable;

import static com.sk89q.worldguard.commands.ParamType.ENUM;
import static com.sk89q.worldguard.commands.ParamType.FLAG;
import static com.sk89q.worldguard.commands.ParamType.FLAG_VALUE;
import static com.sk89q.worldguard.commands.ParamType.NONE;
import static com.sk89q.worldguard.commands.ParamType.PLAYER;
import static com.sk89q.worldguard.commands.ParamType.REGION;
import static com.sk89q.worldguard.commands.ParamType.SUBCOMMAND;
import static com.sk89q.worldguard.commands.ParamType.WORLD;
import static java.util.Arrays.asList;
import static java.util.Collections.emptyList;
import static java.util.Collections.emptyMap;
import static java.util.Collections.singletonList;
import static java.util.Collections.singletonMap;

/**
 * 命令 tab 补全处理器。持有静态命令树，按输入定位命令节点并返回候选。
 */
public final class TabCompleteHandler {

    private TabCompleteHandler() {
    }

    // ---- 命令树构建工具 ----

    private static CommandNode cmd(String alias, @Nullable String perm, ParamType... params) {
        return new CommandNode(singletonList(alias), perm, Arrays.asList(params), emptyMap(),
                emptyList(), null, false);
    }

    private static CommandNode cmdAliases(List<String> aliases, @Nullable String perm, ParamType... params) {
        return new CommandNode(aliases, perm, Arrays.asList(params), emptyMap(),
                emptyList(), null, false);
    }

    private static CommandNode cmdValueFlag(String alias, @Nullable String perm, List<ParamType> params,
                                            char valueFlag, ParamType valueFlagType, boolean repeatLast) {
        return new CommandNode(singletonList(alias), perm, params,
                singletonMap(valueFlag, valueFlagType), emptyList(), null, repeatLast);
    }

    private static CommandNode cmdValueFlag(String alias, @Nullable String perm, List<ParamType> params,
                                            char valueFlag, ParamType valueFlagType) {
        return cmdValueFlag(alias, perm, params, valueFlag, valueFlagType, false);
    }

    private static CommandNode cmdValueFlagAliases(List<String> aliases, @Nullable String perm, List<ParamType> params,
                                                   char valueFlag, ParamType valueFlagType, boolean repeatLast) {
        return new CommandNode(aliases, perm, params,
                singletonMap(valueFlag, valueFlagType), emptyList(), null, repeatLast);
    }

    private static CommandNode cmdValueFlagAliases(List<String> aliases, @Nullable String perm, List<ParamType> params,
                                                   char valueFlag, ParamType valueFlagType) {
        return cmdValueFlagAliases(aliases, perm, params, valueFlag, valueFlagType, false);
    }

    private static CommandNode cmdEnum(String alias, @Nullable String perm, int enumParamCount, String... values) {
        ParamType[] params = new ParamType[enumParamCount];
        Arrays.fill(params, ENUM);
        return new CommandNode(singletonList(alias), perm, Arrays.asList(params), emptyMap(),
                emptyList(), Arrays.asList(values), false);
    }

    private static CommandNode cmdEnumAliases(List<String> aliases, @Nullable String perm, int enumParamCount,
                                              String... values) {
        ParamType[] params = new ParamType[enumParamCount];
        Arrays.fill(params, ENUM);
        return new CommandNode(aliases, perm, Arrays.asList(params), emptyMap(),
                emptyList(), Arrays.asList(values), false);
    }

    private static CommandNode parent(String alias, CommandNode... children) {
        return new CommandNode(singletonList(alias), null, singletonList(SUBCOMMAND), emptyMap(),
                Arrays.asList(children), null, false);
    }

    private static CommandNode parentAliases(List<String> aliases, CommandNode... children) {
        return new CommandNode(aliases, null, singletonList(SUBCOMMAND), emptyMap(),
                Arrays.asList(children), null, false);
    }

    // ---- 命令树 ----

    private static final List<CommandNode> TOP_LEVEL = Arrays.asList(
            // /region (/rg)
            parentAliases(Arrays.asList("rg", "region", "regions"),
                    cmdAliases(asList("define", "def", "d", "create"), "worldguard.region.define", NONE, PLAYER),
                    cmdValueFlagAliases(asList("redefine", "update", "move"), null, singletonList(REGION), 'w', WORLD),
                    cmd("claim", "worldguard.region.claim", NONE),
                    cmdValueFlagAliases(asList("select", "sel", "s"), null, singletonList(REGION), 'w', WORLD),
                    cmdValueFlagAliases(asList("info", "i"), null, singletonList(REGION), 'w', WORLD),
                    cmdValueFlag("list", "worldguard.region.list", singletonList(NONE), 'w', WORLD),
                    cmdValueFlagAliases(asList("flag", "f"), null, asList(REGION, FLAG, FLAG_VALUE), 'w', WORLD),
                    cmdValueFlag("flags", null, singletonList(REGION), 'w', WORLD),
                    cmdValueFlagAliases(asList("setpriority", "priority", "pri"), null, asList(REGION, NONE), 'w', WORLD),
                    cmdValueFlagAliases(asList("setparent", "parent", "par"), null, asList(REGION, REGION), 'w', WORLD),
                    cmdValueFlagAliases(asList("remove", "delete", "del", "rem"), null, singletonList(REGION), 'w', WORLD),
                    cmdValueFlagAliases(asList("load", "reload"), "worldguard.region.load", singletonList(WORLD), 'w', WORLD),
                    cmdValueFlagAliases(asList("save", "write"), "worldguard.region.save", singletonList(WORLD), 'w', WORLD),
                    cmdEnum("migratedb", "worldguard.region.migratedb", 2, "yaml", "sql"),
                    cmd("migrateuuid", "worldguard.region.migrateuuid"),
                    cmdValueFlag("migrateheights", "worldguard.region.migrateheights", singletonList(WORLD), 'w', WORLD),
                    cmdValueFlagAliases(asList("teleport", "tp"), null, singletonList(REGION), 'w', WORLD),
                    cmdEnumAliases(asList("toggle-bypass", "bypass"), "worldguard.region.toggle-bypass", 1, "on", "off"),
                    cmdValueFlagAliases(asList("addmember", "addmem", "am"), null, asList(REGION, PLAYER), 'w', WORLD, true),
                    cmdValueFlagAliases(asList("addowner", "ao"), null, asList(REGION, PLAYER), 'w', WORLD, true),
                    cmdValueFlagAliases(asList("removemember", "remmember", "removemem", "remmem", "rm"), null, asList(REGION, PLAYER), 'w', WORLD, true),
                    cmdValueFlagAliases(asList("removeowner", "remowner", "ro"), null, asList(REGION, PLAYER), 'w', WORLD, true)),
            // /worldguard (/wg)
            parentAliases(Arrays.asList("wg", "worldguard"),
                    cmd("version", null),
                    cmd("reload", "worldguard.reload"),
                    cmd("report", "worldguard.report"),
                    cmd("profile", "worldguard.profile", NONE),
                    cmd("stopprofile", "worldguard.profile"),
                    cmd("flushstates", "worldguard.flushstates", PLAYER),
                    cmd("running", "worldguard.running"),
                    parent("debug",
                            cmd("testbreak", "worldguard.debug.event", PLAYER),
                            cmd("testplace", "worldguard.debug.event", PLAYER),
                            cmd("testinteract", "worldguard.debug.event", PLAYER),
                            cmd("testdamage", "worldguard.debug.event", PLAYER))),
            // 顶层单命令
            cmd("stopfire", "worldguard.fire-toggle.stop", WORLD),
            cmd("allowfire", "worldguard.fire-toggle.stop", WORLD),
            cmdEnum("halt-activity", "worldguard.halt-activity", 1, "confirm"),
            cmd("god", "worldguard.god", PLAYER),
            cmd("ungod", "worldguard.god", PLAYER),
            cmd("heal", "worldguard.heal", PLAYER),
            cmd("slay", "worldguard.slay", PLAYER),
            cmd("locate", "worldguard.locate", PLAYER),
            cmd("stack", "worldguard.stack"));

    // ---- 入口 ----

    /**
     * 为指定命令的当前输入生成补全候选。
     *
     * @param alias 命令名（如 "rg"/"region"）
     * @param args  命令名后的全部 token；最后一个为当前输入前缀
     */
    public static List<String> complete(String alias, String[] args, Actor actor, TabCompletionSource source) {
        CommandNode top = TOP_LEVEL.stream()
                .filter(n -> n.matches(alias))
                .findFirst()
                .orElse(null);
        if (top == null) {
            return emptyList();
        }
        return completeNode(top, args, 0, actor, source);
    }

    private static List<String> completeNode(CommandNode node, String[] args, int argIdx,
                                             Actor actor, TabCompletionSource source) {
        if (!node.children.isEmpty()) {
            // 跳过 flag 参数，定位子命令 token
            int idx = argIdx;
            while (idx < args.length && isFlag(args[idx])) {
                if (args[idx].length() == 2 && node.valueFlags.containsKey(args[idx].charAt(1))) {
                    idx++;
                }
                idx++;
            }
            if (idx < args.length - 1) {
                // 子命令已完整输入且其后还有参数 → 下钻
                int subIdx = idx; // 捕获为 effectively-final，供 lambda 使用
                CommandNode sub = node.children.stream()
                        .filter(c -> c.matches(args[subIdx]))
                        .findFirst()
                        .orElse(null);
                if (sub != null) {
                    return completeNode(sub, args, idx + 1, actor, source);
                }
                return emptyList();
            }
            // 当前正在输入子命令名
            String current = idx < args.length ? args[idx] : "";
            return subcommandSuggestions(node, current, actor, source);
        }
        return completeParams(node, args, argIdx, actor, source);
    }

    private static List<String> subcommandSuggestions(CommandNode node, String current,
                                                      Actor actor, TabCompletionSource source) {
        List<String> result = new ArrayList<>();
        for (CommandNode child : node.children) {
            if (!source.hasPermission(actor, child.permission)) {
                continue;
            }
            for (String alias : child.aliases) {
                if (startsWithIgnoreCase(alias, current)) {
                    result.add(alias);
                }
            }
        }
        return result;
    }

    private static List<String> completeParams(CommandNode node, String[] args, int argIdx,
                                               Actor actor, TabCompletionSource source) {
        Map<Character, String> flagValues = new HashMap<>();
        List<String> positionArgs = new ArrayList<>();
        String current = "";
        Character pendingValueFlag = null; // value flag 的值正被输入（null=无）

        int i = argIdx;
        for (; i < args.length; i++) {
            String token = args[i];
            if (i == args.length - 1) {
                current = token;
                break;
            }
            if (isFlag(token) && token.length() == 2) {
                char flag = token.charAt(1);
                if (node.valueFlags.containsKey(flag)) {
                    if (i + 1 == args.length - 1) {
                        // 最后一个 token 是当前前缀，即正在输入该 value flag 的值
                        pendingValueFlag = flag;
                        current = args[i + 1];
                        i++;
                    } else {
                        flagValues.put(flag, args[i + 1]);
                        i++;
                    }
                }
                continue;
            }
            positionArgs.add(token);
        }

        // 正在输入某个 value flag 的值（如 "-w <前缀>"）
        if (pendingValueFlag != null) {
            ParamType valueType = node.valueFlags.get(pendingValueFlag);
            return suggestForType(node, valueType, positionArgs, flagValues,
                    current, actor, source);
        }
        // 当前 token 是 value flag 名（如 "-w"）→ 补全该 flag 的值
        if (current.length() == 2 && current.charAt(0) == '-') {
            ParamType flagValueType = node.valueFlags.get(current.charAt(1));
            if (flagValueType != null) {
                return suggestForType(node, flagValueType, positionArgs, flagValues,
                        current, actor, source);
            }
            return emptyList();
        }

        // 位置参数补全
        int pos = positionArgs.size();
        if (pos < node.params.size()) {
            return suggestForType(node, node.params.get(pos), positionArgs, flagValues,
                    current, actor, source);
        }
        // 超出声明参数位，若最后一个参数可重复则继续补全
        if (!node.params.isEmpty() && node.repeatLastParam) {
            return suggestForType(node, node.params.get(node.params.size() - 1), positionArgs,
                    flagValues, current, actor, source);
        }
        return emptyList();
    }

    private static List<String> suggestForType(CommandNode node, ParamType type,
                                               List<String> positionArgs, Map<Character, String> flagValues,
                                               String current, Actor actor, TabCompletionSource source) {
        List<String> candidates;
        switch (type) {
            case REGION: {
                String world = flagValues.get('w');
                if (world == null) {
                    world = source.getCurrentWorldName(actor);
                }
                candidates = source.getRegionNames(world);
                break;
            }
            case PLAYER:
                candidates = source.getOnlinePlayerNames();
                break;
            case FLAG:
                candidates = source.getFlagNames();
                break;
            case FLAG_VALUE: {
                int flagPos = node.params.indexOf(FLAG);
                if (flagPos >= 0 && flagPos < positionArgs.size()) {
                    String flagName = positionArgs.get(flagPos);
                    List<String> values = source.getFlagValueSuggestions(flagName);
                    candidates = values != null ? values : emptyList();
                } else {
                    candidates = emptyList();
                }
                break;
            }
            case WORLD:
                candidates = source.getWorldNames();
                break;
            case ENUM:
                candidates = node.enumValues != null ? node.enumValues : emptyList();
                break;
            default:
                return emptyList();
        }
        return filterPrefix(candidates, current);
    }

    private static List<String> filterPrefix(List<String> candidates, String current) {
        List<String> result = new ArrayList<>();
        for (String candidate : candidates) {
            if (startsWithIgnoreCase(candidate, current)) {
                result.add(candidate);
            }
        }
        return result;
    }

    private static boolean startsWithIgnoreCase(String text, String prefix) {
        return text.regionMatches(true, 0, prefix, 0, prefix.length());
    }

    private static boolean isFlag(String token) {
        return !token.isEmpty() && token.charAt(0) == '-';
    }
}
