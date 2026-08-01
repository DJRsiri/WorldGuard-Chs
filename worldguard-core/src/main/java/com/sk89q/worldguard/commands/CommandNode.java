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

import java.util.List;
import java.util.Map;
import javax.annotation.Nullable;

/**
 * 命令树节点。一个命令对应一个节点，子命令作为 children。
 */
final class CommandNode {

    final List<String> aliases;
    /** 可静态检查的权限节点；null 表示不过滤（权限动态判定或无需权限） */
    @Nullable final String permission;
    /** 按位置声明的参数补全类型 */
    final List<ParamType> params;
    /** value flag（如 'w'）→ 其值的补全类型（如 WORLD） */
    final Map<Character, ParamType> valueFlags;
    /** 子命令；非空则该节点是子命令容器 */
    final List<CommandNode> children;
    /** ENUM 类型参数的候选值 */
    @Nullable final List<String> enumValues;
    /** 最后一个位置参数是否可重复（如 <members...>） */
    final boolean repeatLastParam;

    CommandNode(List<String> aliases, @Nullable String permission, List<ParamType> params,
                Map<Character, ParamType> valueFlags, List<CommandNode> children,
                @Nullable List<String> enumValues, boolean repeatLastParam) {
        this.aliases = aliases;
        this.permission = permission;
        this.params = params;
        this.valueFlags = valueFlags;
        this.children = children;
        this.enumValues = enumValues;
        this.repeatLastParam = repeatLastParam;
    }

    boolean matches(String alias) {
        for (String a : aliases) {
            if (a.equalsIgnoreCase(alias)) {
                return true;
            }
        }
        return false;
    }
}
