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

/**
 * 命令参数位对应的补全类型。
 */
public enum ParamType {
    SUBCOMMAND, // 子命令名（自动从 children 获取）
    REGION,     // 已有区域名
    PLAYER,     // 在线玩家名
    FLAG,       // 已注册 flag 名
    FLAG_VALUE, // 基于已选 flag 类型的值
    WORLD,      // 世界名
    ENUM,       // 命令节点声明的枚举值
    NONE        // 不补全（id、数字、页码等）
}
