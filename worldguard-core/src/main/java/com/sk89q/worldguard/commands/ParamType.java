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
