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

package com.sk89q.worldguard.commands.region;

import static com.google.common.base.Preconditions.checkNotNull;

import com.google.common.base.Strings;
import com.google.common.collect.Sets;
import com.sk89q.minecraft.util.commands.Command;
import com.sk89q.minecraft.util.commands.CommandContext;
import com.sk89q.minecraft.util.commands.CommandException;
import com.sk89q.minecraft.util.commands.CommandPermissionsException;
import com.sk89q.worldedit.WorldEdit;
import com.sk89q.worldedit.command.util.AsyncCommandBuilder;
import com.sk89q.worldedit.extension.platform.Actor;
import com.sk89q.worldedit.extension.platform.Capability;
import com.sk89q.worldedit.regions.Region;
import com.sk89q.worldedit.util.Location;
import com.sk89q.worldedit.util.formatting.component.ErrorFormat;
import com.sk89q.worldedit.util.formatting.component.LabelFormat;
import com.sk89q.worldedit.util.formatting.component.SubtleFormat;
import com.sk89q.worldedit.util.formatting.text.Component;
import com.sk89q.worldedit.util.formatting.text.TextComponent;
import com.sk89q.worldedit.util.formatting.text.event.ClickEvent;
import com.sk89q.worldedit.util.formatting.text.event.HoverEvent;
import com.sk89q.worldedit.util.formatting.text.format.TextColor;
import com.sk89q.worldedit.util.formatting.text.format.TextDecoration;
import com.sk89q.worldedit.world.World;
import com.sk89q.worldedit.world.gamemode.GameModes;
import com.sk89q.worldguard.LocalPlayer;
import com.sk89q.worldguard.WorldGuard;
import com.sk89q.worldguard.commands.task.RegionAdder;
import com.sk89q.worldguard.commands.task.RegionLister;
import com.sk89q.worldguard.commands.task.RegionManagerLoader;
import com.sk89q.worldguard.commands.task.RegionManagerSaver;
import com.sk89q.worldguard.commands.task.RegionRemover;
import com.sk89q.worldguard.config.ConfigurationManager;
import com.sk89q.worldguard.config.WorldConfiguration;
import com.sk89q.worldguard.internal.permission.RegionPermissionModel;
import com.sk89q.worldguard.protection.ApplicableRegionSet;
import com.sk89q.worldguard.protection.FlagValueCalculator;
import com.sk89q.worldguard.protection.flags.Flag;
import com.sk89q.worldguard.protection.flags.FlagContext;
import com.sk89q.worldguard.protection.flags.Flags;
import com.sk89q.worldguard.protection.flags.InvalidFlagFormat;
import com.sk89q.worldguard.protection.flags.RegionGroup;
import com.sk89q.worldguard.protection.flags.RegionGroupFlag;
import com.sk89q.worldguard.protection.flags.registry.FlagRegistry;
import com.sk89q.worldguard.protection.managers.RegionManager;
import com.sk89q.worldguard.protection.managers.RemovalStrategy;
import com.sk89q.worldguard.protection.managers.migration.DriverMigration;
import com.sk89q.worldguard.protection.managers.migration.MigrationException;
import com.sk89q.worldguard.protection.managers.migration.UUIDMigration;
import com.sk89q.worldguard.protection.managers.migration.WorldHeightMigration;
import com.sk89q.worldguard.protection.managers.storage.DriverType;
import com.sk89q.worldguard.protection.managers.storage.RegionDriver;
import com.sk89q.worldguard.protection.regions.GlobalProtectedRegion;
import com.sk89q.worldguard.protection.regions.ProtectedPolygonalRegion;
import com.sk89q.worldguard.protection.regions.ProtectedRegion;
import com.sk89q.worldguard.protection.regions.ProtectedRegion.CircularInheritanceException;
import com.sk89q.worldguard.protection.regions.RegionContainer;
import com.sk89q.worldguard.protection.util.WorldEditRegionConverter;
import com.sk89q.worldguard.session.Session;
import com.sk89q.worldguard.util.Enums;
import com.sk89q.worldguard.util.logging.LoggerToChatHandler;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;

/**
 * Implements the /region commands for WorldGuard.
 */
public final class RegionCommands extends RegionCommandsBase {

    private static final Logger log = Logger.getLogger(RegionCommands.class.getCanonicalName());
    private final WorldGuard worldGuard;

    public RegionCommands(WorldGuard worldGuard) {
        checkNotNull(worldGuard);
        this.worldGuard = worldGuard;
    }

    private static TextComponent passthroughFlagWarning = TextComponent.empty()
            .append(TextComponent.of("警告：", TextColor.RED, Sets.newHashSet(TextDecoration.BOLD)))
            .append(ErrorFormat.wrap(" 该规则与穿越区域无关。"))
            .append(TextComponent.newline())
            .append(TextComponent.of("它会覆盖建筑检查。如果你不确定这意味着什么，请参阅 ")
                    .append(TextComponent.of("[此文档页面]", TextColor.AQUA)
                            .clickEvent(ClickEvent.of(ClickEvent.Action.OPEN_URL,
                                    "https://worldguard.enginehub.org/en/latest/regions/flags/#overrides")))
                    .append(TextComponent.of(" 了解更多信息。")));
    private static TextComponent buildFlagWarning = TextComponent.empty()
            .append(TextComponent.of("警告：", TextColor.RED, Sets.newHashSet(TextDecoration.BOLD)))
            .append(ErrorFormat.wrap(" 设置此规则并非保护所必需。"))
            .append(TextComponent.newline())
            .append(TextComponent.of("设置此规则将完全覆盖默认保护，并适用于" +
                    "成员、非成员、活塞、沙物理以及所有其他可能修改方块的事物。"))
            .append(TextComponent.newline())
            .append(TextComponent.of("只有在你确信自己在做什么时才设置此规则。请参阅 ")
                    .append(TextComponent.of("[此文档页面]", TextColor.AQUA)
                            .clickEvent(ClickEvent.of(ClickEvent.Action.OPEN_URL,
                                    "https://worldguard.enginehub.org/en/latest/regions/flags/#protection-related")))
                    .append(TextComponent.of(" 了解更多信息。")));

    /**
     * Defines a new region.
     * 
     * @param args the arguments
     * @param sender the sender
     * @throws CommandException any error
     */
    @Command(aliases = {"define", "def", "d", "create"},
             usage = "[-w <world>] <id> [<owner1> [<owner2> [<owners...>]]]",
             flags = "ngw:",
             desc = "定义一个区域",
             min = 1)
    public void define(CommandContext args, Actor sender) throws CommandException {
        warnAboutSaveFailures(sender);

        // Check permissions
        if (!getPermissionModel(sender).mayDefine()) {
            throw new CommandPermissionsException();
        }

        String id = checkRegionId(args.getString(0), false);

        World world = checkWorld(args, sender, 'w');
        RegionManager manager = checkRegionManager(world);

        checkRegionDoesNotExist(manager, id, true);

        ProtectedRegion region;

        if (args.hasFlag('g')) {
            region = new GlobalProtectedRegion(id);
        } else {
            region = checkRegionFromSelection(sender, id);
        }

        RegionAdder task = new RegionAdder(manager, region);
        task.addOwnersFromCommand(args, 2);

        final String description = String.format("正在添加区域 '%s'", region.getId());
        AsyncCommandBuilder.wrap(task, sender)
                .registerWithSupervisor(worldGuard.getSupervisor(), description)
                .onSuccess((Component) null,
                        t -> {
                            sender.print(String.format("已创建一个名为 '%s' 的新区域。", region.getId()));
                            warnAboutDimensions(sender, region);
                            informNewUser(sender, manager, region);
                            checkSpawnOverlap(sender, world, region);
                        })
                .onFailure(String.format("无法添加区域 '%s'", region.getId()), worldGuard.getExceptionConverter())
                .buildAndExec(worldGuard.getExecutorService());
    }

    /**
     * Re-defines a region with a new selection.
     *
     * @param args the arguments
     * @param sender the sender
     * @throws CommandException any error
     */
    @Command(aliases = {"redefine", "update", "move"},
             usage = "[-w <world>] <id>",
             desc = "重新定义区域的形状",
             flags = "gw:",
             min = 1, max = 1)
    public void redefine(CommandContext args, Actor sender) throws CommandException {
        warnAboutSaveFailures(sender);

        String id = checkRegionId(args.getString(0), false);

        World world = checkWorld(args, sender, 'w');
        RegionManager manager = checkRegionManager(world);

        ProtectedRegion existing = checkExistingRegion(manager, id, false);

        // Check permissions
        if (!getPermissionModel(sender).mayRedefine(existing)) {
            throw new CommandPermissionsException();
        }

        ProtectedRegion region;

        if (args.hasFlag('g')) {
            region = new GlobalProtectedRegion(id);
        } else {
            region = checkRegionFromSelection(sender, id);
        }

        region.copyFrom(existing);

        RegionAdder task = new RegionAdder(manager, region);

        final String description = String.format("正在更新区域 '%s'", region.getId());
        AsyncCommandBuilder.wrap(task, sender)
                .registerWithSupervisor(worldGuard.getSupervisor(), description)
                .sendMessageAfterDelay("（请稍候... " + description + "）")
                .onSuccess((Component) null,
                        t -> {
                            sender.print(String.format("区域 '%s' 已更新为新区域。", region.getId()));
                            warnAboutDimensions(sender, region);
                            informNewUser(sender, manager, region);
                            checkSpawnOverlap(sender, world, region);
                        })
                .onFailure(String.format("无法更新区域 '%s'", region.getId()), worldGuard.getExceptionConverter())
                .buildAndExec(worldGuard.getExecutorService());
    }

    /**
     * Claiming command for users.
     *
     * <p>This command is a joke and it needs to be rewritten. It was contributed
     * code :(</p>
     *
     * @param args the arguments
     * @param sender the sender
     * @throws CommandException any error
     */
    @Command(aliases = {"claim"},
             usage = "<id>",
             desc = "认领一个区域",
             min = 1, max = 1)
    public void claim(CommandContext args, Actor sender) throws CommandException {
        warnAboutSaveFailures(sender);

        LocalPlayer player = worldGuard.checkPlayer(sender);
        RegionPermissionModel permModel = getPermissionModel(player);

        // Check permissions
        if (!permModel.mayClaim()) {
            throw new CommandPermissionsException();
        }

        String id = checkRegionId(args.getString(0), false);

        RegionManager manager = checkRegionManager(player.getWorld());

        checkRegionDoesNotExist(manager, id, false);
        ProtectedRegion region = checkRegionFromSelection(player, id);

        WorldConfiguration wcfg = WorldGuard.getInstance().getPlatform().getGlobalStateManager().get(player.getWorld());

        // Check whether the player has created too many regions
        if (!permModel.mayClaimRegionsUnbounded()) {
            int maxRegionCount = wcfg.getMaxRegionCount(player);
            if (maxRegionCount >= 0
                    && manager.getRegionCountOfPlayer(player) >= maxRegionCount) {
                throw new CommandException(
                        "你拥有的区域过多，请先删除一个再认领新区域。");
            }
        }

        ProtectedRegion existing = manager.getRegion(id);

        // Check for an existing region
        if (existing != null) {
            if (!existing.getOwners().contains(player)) {
                throw new CommandException(
                        "该区域已存在，且你并非其所有者。");
            }
        }

        // We have to check whether this region violates the space of any other region
        ApplicableRegionSet regions = manager.getApplicableRegions(region);

        // Check if this region overlaps any other region
        if (regions.size() > 0) {
            if (!regions.isOwnerOfAll(player)) {
                throw new CommandException("此区域与他人拥有的区域重叠。");
            }
        } else {
            if (wcfg.claimOnlyInsideExistingRegions) {
                throw new CommandException("你只能在已有区域内认领，" +
                        "且该区域须由你或你的群组拥有。");
            }
        }

        if (wcfg.maxClaimVolume >= Integer.MAX_VALUE) {
            throw new CommandException("配置中的最大认领体积超出了支持范围。 " +
                    "目前它必须为 " + Integer.MAX_VALUE + " 或更小。请联系服务器管理员。");
        }

        // Check claim volume
        if (!permModel.mayClaimRegionsUnbounded()) {
            if (region instanceof ProtectedPolygonalRegion) {
                throw new CommandException("/rg claim 目前不支持多边形区域。");
            }

            if (region.volume() > wcfg.maxClaimVolume) {
                player.printError("该区域过大，无法认领。");
                player.printError("最大体积： " + wcfg.maxClaimVolume + "，你的体积： " + region.volume());
                return;
            }
        }

        // Inherit from a template region
        if (!Strings.isNullOrEmpty(wcfg.setParentOnClaim)) {
            ProtectedRegion templateRegion = manager.getRegion(wcfg.setParentOnClaim);
            if (templateRegion != null) {
                try {
                    region.setParent(templateRegion);
                } catch (CircularInheritanceException e) {
                    throw new CommandException(e.getMessage());
                }
            }
        }

        region.getOwners().addPlayer(player);
        manager.addRegion(region);
        player.print(TextComponent.of(String.format("已认领一个名为 '%s' 的新区域。", id)));
    }

    /**
     * Get a WorldEdit selection from a region.
     *
     * @param args the arguments
     * @param sender the sender
     * @throws CommandException any error
     */
    @Command(aliases = {"select", "sel", "s"},
             usage = "[-w <world>] [id]",
             desc = "将区域加载为 WorldEdit 选区",
             min = 0, max = 1,
             flags = "w:")
    public void select(CommandContext args, Actor sender) throws CommandException {
        World world = checkWorld(args, sender, 'w');
        RegionManager manager = checkRegionManager(world);
        ProtectedRegion existing;
        RegionPermissionModel permissionModel = getPermissionModel(sender);

        // If no arguments were given, get the region that the player is inside
        if (args.argsLength() == 0) {
            LocalPlayer player = worldGuard.checkPlayer(sender);
            if (!player.getWorld().equals(world)) { // confusing to get current location regions in another world
                throw new CommandException("请指定区域名称。"); // just don't allow that
            }
            world = player.getWorld();
            existing = checkRegionStandingIn(manager, player, "/rg select -w \"" + world.getName() + "\" %id%", permissionModel::maySelect);
        } else {
            existing = checkExistingRegion(manager, args.getString(0), false);
        }

        // Check permissions
        if (!permissionModel.maySelect(existing)) {
            throw new CommandPermissionsException();
        }

        // Select
        setPlayerSelection(sender, existing, world);
    }

    /**
     * Get information about a region.
     *
     * @param args the arguments
     * @param sender the sender
     * @throws CommandException any error
     */
    @Command(aliases = {"info", "i"},
             usage = "[id]",
             flags = "usw:",
             desc = "获取区域信息",
             min = 0, max = 1)
    public void info(CommandContext args, Actor sender) throws CommandException {
        warnAboutSaveFailures(sender);

        World world = checkWorld(args, sender, 'w'); // Get the world
        RegionPermissionModel permModel = getPermissionModel(sender);

        // Lookup the existing region
        RegionManager manager = checkRegionManager(world);
        ProtectedRegion existing;

        if (args.argsLength() == 0) { // Get region from where the player is
            if (!(sender instanceof LocalPlayer)) {
                throw new CommandException("请使用 /region info -w 世界名称 区域名称 指定区域。");
            }

            existing = checkRegionStandingIn(manager, (LocalPlayer) sender, true,
                    "/rg info -w \"" + world.getName() + "\" %id%" + (args.hasFlag('u') ? " -u" : "") + (args.hasFlag('s') ? " -s" : ""), permModel::mayLookup);
        } else { // Get region from the ID
            existing = checkExistingRegion(manager, args.getString(0), true);
        }

        // Check permissions
        if (!permModel.mayLookup(existing)) {
            throw new CommandPermissionsException();
        }

        // Let the player select the region
        if (args.hasFlag('s')) {
            // Check permissions
            if (!permModel.maySelect(existing)) {
                throw new CommandPermissionsException();
            }

            setPlayerSelection(worldGuard.checkPlayer(sender), existing, world);
        }

        // Print region information
        RegionPrintoutBuilder printout = new RegionPrintoutBuilder(world.getName(), existing,
                args.hasFlag('u') ? null : WorldGuard.getInstance().getProfileCache(), sender);

        AsyncCommandBuilder.wrap(printout, sender)
                .registerWithSupervisor(WorldGuard.getInstance().getSupervisor(), "正在获取区域信息")
                .sendMessageAfterDelay("（请稍候...正在获取区域信息...）")
                .onSuccess((Component) null, component -> {
                    sender.print(component);
                    checkSpawnOverlap(sender, world, existing);
                })
                .onFailure("无法获取区域信息", WorldGuard.getInstance().getExceptionConverter())
                .buildAndExec(WorldGuard.getInstance().getExecutorService());
    }

    /**
     * List regions.
     *
     * @param args the arguments
     * @param sender the sender
     * @throws CommandException any error
     */
    @Command(aliases = {"list"},
             usage = "[-w world] [-p owner [-n]] [-s] [-i filter] [page]",
             desc = "获取区域列表",
             flags = "np:w:i:s",
             max = 1)
    public void list(CommandContext args, Actor sender) throws CommandException {
        warnAboutSaveFailures(sender);

        World world = checkWorld(args, sender, 'w'); // Get the world
        String ownedBy;

        // Get page
        int page = args.getInteger(0, 1);
        if (page < 1) {
            page = 1;
        }

        // -p flag to lookup a player's regions
        if (args.hasFlag('p')) {
            ownedBy = args.getFlag('p');
        } else {
            ownedBy = null; // List all regions
        }

        // Check permissions
        if (!getPermissionModel(sender).mayList(ownedBy)) {
            ownedBy = sender.getName(); // assume they only want their own
            if (!getPermissionModel(sender).mayList(ownedBy)) {
                throw new CommandPermissionsException();
            }
        }

        RegionManager manager = checkRegionManager(world);

        RegionLister task = new RegionLister(manager, sender, world.getName());
        task.setPage(page);
        if (ownedBy != null) {
            task.filterOwnedByName(ownedBy, args.hasFlag('n'));
        }

        if (args.hasFlag('s')) {
            ProtectedRegion existing = checkRegionFromSelection(sender, "tmp");
            task.filterByIntersecting(existing);
        }

        // -i string is in region id
        if (args.hasFlag('i')) {
            task.filterIdByMatch(args.getFlag('i'));
        }

        AsyncCommandBuilder.wrap(task, sender)
                .registerWithSupervisor(WorldGuard.getInstance().getSupervisor(), "正在获取区域列表")
                .sendMessageAfterDelay("（请稍候...正在获取区域列表...）")
                .onFailure("无法获取区域列表", WorldGuard.getInstance().getExceptionConverter())
                .buildAndExec(WorldGuard.getInstance().getExecutorService());
    }

    /**
     * Set a flag.
     *
     * @param args the arguments
     * @param sender the sender
     * @throws CommandException any error
     */
    @Command(aliases = {"flag", "f"},
             usage = "<id> <flag> [-w world] [-g group] [value]",
             flags = "g:w:eh:",
             desc = "设置规则",
             min = 2)
    public void flag(CommandContext args, Actor sender) throws CommandException {
        warnAboutSaveFailures(sender);

        World world = checkWorld(args, sender, 'w'); // Get the world
        String flagName = args.getString(1);
        String value = args.argsLength() >= 3 ? args.getJoinedStrings(2) : null;
        RegionGroup groupValue = null;
        FlagRegistry flagRegistry = WorldGuard.getInstance().getFlagRegistry();
        RegionPermissionModel permModel = getPermissionModel(sender);

        if (args.hasFlag('e')) {
            if (value != null) {
                throw new CommandException("你不能在设置规则值的同时使用 -e(mpty)。");
            }

            value = "";
        }

        // Lookup the existing region
        RegionManager manager = checkRegionManager(world);
        ProtectedRegion existing = checkExistingRegion(manager, args.getString(0), true);

        // Check permissions
        if (!permModel.maySetFlag(existing)) {
            throw new CommandPermissionsException();
        }
        String regionId = existing.getId();

        Flag<?> foundFlag = Flags.fuzzyMatchFlag(flagRegistry, flagName);

        // We didn't find the flag, so let's print a list of flags that the user
        // can use, and do nothing afterwards
        if (foundFlag == null) {
            AsyncCommandBuilder.wrap(new FlagListBuilder(flagRegistry, permModel, existing, world,
                                                         regionId, sender, flagName), sender)
                    .registerWithSupervisor(WorldGuard.getInstance().getSupervisor(), "无效规则命令的规则列表。")
                    .onSuccess((Component) null, sender::print)
                    .onFailure((Component) null, WorldGuard.getInstance().getExceptionConverter())
                    .buildAndExec(WorldGuard.getInstance().getExecutorService());
            return;
        } else if (value != null) {
            if (foundFlag == Flags.BUILD || foundFlag == Flags.BLOCK_BREAK || foundFlag == Flags.BLOCK_PLACE) {
                sender.print(buildFlagWarning);
                if (!sender.isPlayer()) {
                    sender.printRaw("https://worldguard.enginehub.org/en/latest/regions/flags/#protection-related");
                }
            } else if (foundFlag == Flags.PASSTHROUGH) {
                sender.print(passthroughFlagWarning);
                if (!sender.isPlayer()) {
                    sender.printRaw("https://worldguard.enginehub.org/en/latest/regions/flags/#overrides");
                }
            }
        }

        // Also make sure that we can use this flag
        // This permission is confusing and probably should be replaced, but
        // but not here -- in the model
        if (!permModel.maySetFlag(existing, foundFlag, value)) {
            throw new CommandPermissionsException();
        }

        // -g for group flag
        if (args.hasFlag('g')) {
            String group = args.getFlag('g');
            RegionGroupFlag groupFlag = foundFlag.getRegionGroupFlag();

            if (groupFlag == null) {
                throw new CommandException("区域规则 '" + foundFlag.getName()
                        + "' 没有对应的群组规则！");
            }

            // Parse the [-g group] separately so entire command can abort if parsing
            // the [value] part throws an error.
            try {
                groupValue = groupFlag.parseInput(FlagContext.create().setSender(sender).setInput(group).setObject("region", existing).build());
            } catch (InvalidFlagFormat e) {
                throw new CommandException(e.getMessage());
            }

        }

        // Set the flag value if a value was set
        if (value != null) {
            // Set the flag if [value] was given even if [-g group] was given as well
            try {
                value = setFlag(existing, foundFlag, sender, value).toString();
            } catch (InvalidFlagFormat e) {
                throw new CommandException(e.getMessage());
            }

            if (!args.hasFlag('h')) {
                sender.print("区域规则 " + foundFlag.getName() + " 已在区域 '" + regionId + "' 上设置为 '" + value + "'。");
            }

        // No value? Clear the flag, if -g isn't specified
        } else if (!args.hasFlag('g')) {
            // Clear the flag only if neither [value] nor [-g group] was given
            existing.setFlag(foundFlag, null);

            // Also clear the associated group flag if one exists
            RegionGroupFlag groupFlag = foundFlag.getRegionGroupFlag();
            if (groupFlag != null) {
                existing.setFlag(groupFlag, null);
            }

            if (!args.hasFlag('h')) {
                sender.print("区域规则 " + foundFlag.getName() + " 已从区域 '" + regionId + "' 中移除。（任何 -g(roups) 也已一并移除。）");
            }
        }

        // Now set the group
        if (groupValue != null) {
            RegionGroupFlag groupFlag = foundFlag.getRegionGroupFlag();

            // If group set to the default, then clear the group flag
            if (groupValue == groupFlag.getDefault()) {
                existing.setFlag(groupFlag, null);
                sender.print("区域群组规则 '" + foundFlag.getName() + "' 已重置为默认值。");
            } else {
                existing.setFlag(groupFlag, groupValue);
                sender.print("区域群组规则 '" + foundFlag.getName() + "' 已设置。");
            }
        }

        // Print region information
        if (args.hasFlag('h')) {
            int page = args.getFlagInteger('h');
            sendFlagHelper(sender, world, existing, permModel, page);
        } else {
            RegionPrintoutBuilder printout = new RegionPrintoutBuilder(world.getName(), existing, null, sender);
            printout.append(SubtleFormat.wrap("（当前规则： "));
            printout.appendFlagsList(false);
            printout.append(SubtleFormat.wrap("）"));
            printout.send(sender);
            checkSpawnOverlap(sender, world, existing);
        }
    }

    @Command(aliases = "flags",
             usage = "[-p <page>] [id]",
             flags = "p:w:",
             desc = "查看区域规则",
             min = 0, max = 2)
    public void flagHelper(CommandContext args, Actor sender) throws CommandException {
        World world = checkWorld(args, sender, 'w'); // Get the world

        // Lookup the existing region
        RegionManager manager = checkRegionManager(world);
        ProtectedRegion region;
        final RegionPermissionModel perms = getPermissionModel(sender);

        if (args.argsLength() == 0) { // Get region from where the player is
            if (!(sender instanceof LocalPlayer)) {
                throw new CommandException("请使用 /region flags -w 世界名称 区域名称 指定区域。");
            }

            region = checkRegionStandingIn(manager, (LocalPlayer) sender, true,
                    "/rg flags -w \"" + world.getName() + "\" %id%", perms::mayLookup);
        } else { // Get region from the ID
            region = checkExistingRegion(manager, args.getString(0), true);
        }

        if (!perms.mayLookup(region)) {
            throw new CommandPermissionsException();
        }
        int page = args.hasFlag('p') ? args.getFlagInteger('p') : 1;

        sendFlagHelper(sender, world, region, perms, page);
    }

    private static void sendFlagHelper(Actor sender, World world, ProtectedRegion region, RegionPermissionModel perms, int page) {
        final FlagHelperBox flagHelperBox = new FlagHelperBox(world, region, perms);
        flagHelperBox.setComponentsPerPage(18);
        if (!sender.isPlayer()) {
            flagHelperBox.tryMonoSpacing();
        }
        AsyncCommandBuilder.wrap(() -> {
                    if (checkSpawnOverlap(sender, world, region)) {
                        flagHelperBox.setComponentsPerPage(15);
                    }
                    return flagHelperBox.create(page);
                }, sender)
                .onSuccess((Component) null, sender::print)
                .onFailure("无法获取区域规则", WorldGuard.getInstance().getExceptionConverter())
                .buildAndExec(WorldGuard.getInstance().getExecutorService());
    }

    /**
     * Set the priority of a region.
     *
     * @param args the arguments
     * @param sender the sender
     * @throws CommandException any error
     */
    @Command(aliases = {"setpriority", "priority", "pri"},
             usage = "<id> <priority>",
             flags = "w:",
             desc = "设置区域的优先级",
             min = 2, max = 2)
    public void setPriority(CommandContext args, Actor sender) throws CommandException {
        warnAboutSaveFailures(sender);

        World world = checkWorld(args, sender, 'w'); // Get the world
        int priority = args.getInteger(1);

        // Lookup the existing region
        RegionManager manager = checkRegionManager(world);
        ProtectedRegion existing = checkExistingRegion(manager, args.getString(0), false);

        // Check permissions
        if (!getPermissionModel(sender).maySetPriority(existing)) {
            throw new CommandPermissionsException();
        }

        existing.setPriority(priority);

        sender.print("区域 '" + existing.getId() + "' 的优先级已设置为 " + priority + "（数值越大越优先）。");
        checkSpawnOverlap(sender, world, existing);
    }

    /**
     * Set the parent of a region.
     *
     * @param args the arguments
     * @param sender the sender
     * @throws CommandException any error
     */
    @Command(aliases = {"setparent", "parent", "par"},
             usage = "<id> [parent-id]",
             flags = "w:",
             desc = "设置区域的父区域",
             min = 1, max = 2)
    public void setParent(CommandContext args, Actor sender) throws CommandException {
        warnAboutSaveFailures(sender);

        World world = checkWorld(args, sender, 'w'); // Get the world
        ProtectedRegion parent;
        ProtectedRegion child;

        // Lookup the existing region
        RegionManager manager = checkRegionManager(world);

        // Get parent and child
        child = checkExistingRegion(manager, args.getString(0), false);
        if (args.argsLength() == 2) {
            parent = checkExistingRegion(manager, args.getString(1), false);
        } else {
            parent = null;
        }

        // Check permissions
        if (!getPermissionModel(sender).maySetParent(child, parent)) {
            throw new CommandPermissionsException();
        }

        try {
            child.setParent(parent);
        } catch (CircularInheritanceException e) {
            // Tell the user what's wrong
            RegionPrintoutBuilder printout = new RegionPrintoutBuilder(world.getName(), parent, null, sender);
            assert parent != null;
            printout.append(ErrorFormat.wrap("哎呀！将 '", parent.getId(), "' 设为 '", child.getId(),
                    "' 的父区域会导致循环继承。")).newline();
            printout.append(SubtleFormat.wrap("（'", parent.getId(), "' 的当前继承：")).newline();
            printout.appendParentTree(true);
            printout.append(SubtleFormat.wrap("）"));
            printout.send(sender);
            return;
        }

        // Tell the user the current inheritance
        RegionPrintoutBuilder printout = new RegionPrintoutBuilder(world.getName(), child, null, sender);
        printout.append(TextComponent.of("已为区域 '" + child.getId() + "' 设置继承。", TextColor.LIGHT_PURPLE));
        if (parent != null) {
            printout.newline();
            printout.append(SubtleFormat.wrap("（当前继承：")).newline();
            printout.appendParentTree(true);
            printout.append(SubtleFormat.wrap("）"));
        } else {
            printout.append(LabelFormat.wrap(" 该区域现为孤立区域。"));
        }
        printout.send(sender);
    }

    /**
     * Remove a region.
     *
     * @param args the arguments
     * @param sender the sender
     * @throws CommandException any error
     */
    @Command(aliases = {"remove", "delete", "del", "rem"},
             usage = "<id>",
             flags = "fuw:",
             desc = "移除一个区域",
             min = 1, max = 1)
    public void remove(CommandContext args, Actor sender) throws CommandException {
        warnAboutSaveFailures(sender);

        World world = checkWorld(args, sender, 'w'); // Get the world
        boolean removeChildren = args.hasFlag('f');
        boolean unsetParent = args.hasFlag('u');

        // Lookup the existing region
        RegionManager manager = checkRegionManager(world);
        ProtectedRegion existing = checkExistingRegion(manager, args.getString(0), true);

        // Check permissions
        if (!getPermissionModel(sender).mayDelete(existing)) {
            throw new CommandPermissionsException();
        }

        RegionRemover task = new RegionRemover(manager, existing);

        if (removeChildren && unsetParent) {
            throw new CommandException("你不能同时使用 -u（取消父区域）和 -f（移除子区域）。");
        } else if (removeChildren) {
            task.setRemovalStrategy(RemovalStrategy.REMOVE_CHILDREN);
        } else if (unsetParent) {
            task.setRemovalStrategy(RemovalStrategy.UNSET_PARENT_IN_CHILDREN);
        }

        final String description = String.format("正在移除世界 '%s' 中的区域 '%s'", world.getName(), existing.getId());
        AsyncCommandBuilder.wrap(task, sender)
                .registerWithSupervisor(WorldGuard.getInstance().getSupervisor(), description)
                .sendMessageAfterDelay("请稍候...正在移除区域。")
                .onSuccess((Component) null, removed -> sender.print(TextComponent.of(
                        "已成功移除 " + removed.stream().map(ProtectedRegion::getId).collect(Collectors.joining(", ")) + "。",
                        TextColor.LIGHT_PURPLE)))
                .onFailure("无法移除区域", WorldGuard.getInstance().getExceptionConverter())
                .buildAndExec(WorldGuard.getInstance().getExecutorService());
    }

    /**
     * Reload the region database.
     *
     * @param args the arguments
     * @param sender the sender
     * @throws CommandException any error
     */
    @Command(aliases = {"load", "reload"},
            usage = "[world]",
            desc = "从文件重新加载区域",
            flags = "w:")
    public void load(CommandContext args, final Actor sender) throws CommandException {
        warnAboutSaveFailures(sender);

        World world = null;
        try {
            world = checkWorld(args, sender, 'w'); // Get the world
        } catch (CommandException ignored) {
            // assume the user wants to reload all worlds
        }

        // Check permissions
        if (!getPermissionModel(sender).mayForceLoadRegions()) {
            throw new CommandPermissionsException();
        }

        if (world != null) {
            RegionManager manager = checkRegionManager(world);

            if (manager == null) {
                throw new CommandException("世界 '" + world.getName() + "' 不存在区域管理器。");
            }

            final String description = String.format("正在加载 '%s' 的区域数据。", world.getName());
            AsyncCommandBuilder.wrap(new RegionManagerLoader(manager), sender)
                    .registerWithSupervisor(worldGuard.getSupervisor(), description)
                    .sendMessageAfterDelay("请稍候... " + description)
                    .onSuccess(String.format("已加载 '%s' 的区域数据", world.getName()), null)
                    .onFailure(String.format("无法加载 '%s' 的区域数据", world.getName()), worldGuard.getExceptionConverter())
                    .buildAndExec(worldGuard.getExecutorService());
        } else {
            // Load regions for all worlds
            List<RegionManager> managers = new ArrayList<>();

            for (World w : WorldEdit.getInstance().getPlatformManager().queryCapability(Capability.GAME_HOOKS).getWorlds()) {
                RegionManager manager = WorldGuard.getInstance().getPlatform().getRegionContainer().get(w);
                if (manager != null) {
                    managers.add(manager);
                }
            }

            AsyncCommandBuilder.wrap(new RegionManagerLoader(managers), sender)
                    .registerWithSupervisor(worldGuard.getSupervisor(), "正在加载所有世界的区域")
                    .sendMessageAfterDelay("（请稍候...正在加载所有世界的区域数据...）")
                    .onSuccess("已成功加载所有世界的区域数据。", null)
                    .onFailure("无法加载所有世界的区域", worldGuard.getExceptionConverter())
                    .buildAndExec(worldGuard.getExecutorService());
        }
    }

    /**
     * Re-save the region database.
     *
     * @param args the arguments
     * @param sender the sender
     * @throws CommandException any error
     */
    @Command(aliases = {"save", "write"},
            usage = "[world]",
            desc = "重新将区域保存到文件",
            flags = "w:")
    public void save(CommandContext args, final Actor sender) throws CommandException {
        warnAboutSaveFailures(sender);

        World world = null;
        try {
            world = checkWorld(args, sender, 'w'); // Get the world
        } catch (CommandException ignored) {
            // assume user wants to save all worlds
        }

        // Check permissions
        if (!getPermissionModel(sender).mayForceSaveRegions()) {
            throw new CommandPermissionsException();
        }

        if (world != null) {
            RegionManager manager = checkRegionManager(world);

            if (manager == null) {
                throw new CommandException("世界 '" + world.getName() + "' 不存在区域管理器。");
            }

            final String description = String.format("正在保存 '%s' 的区域数据。", world.getName());
            AsyncCommandBuilder.wrap(new RegionManagerSaver(manager), sender)
                    .registerWithSupervisor(worldGuard.getSupervisor(), description)
                    .sendMessageAfterDelay("请稍候... " + description)
                    .onSuccess(String.format("正在保存 '%s' 的区域数据", world.getName()), null)
                    .onFailure(String.format("无法保存 '%s' 的区域数据", world.getName()), worldGuard.getExceptionConverter())
                    .buildAndExec(worldGuard.getExecutorService());
        } else {
            // Save for all worlds
            List<RegionManager> managers = new ArrayList<>();

            final RegionContainer regionContainer = worldGuard.getPlatform().getRegionContainer();
            for (World w : WorldEdit.getInstance().getPlatformManager().queryCapability(Capability.GAME_HOOKS).getWorlds()) {
                RegionManager manager = regionContainer.get(w);
                if (manager != null) {
                    managers.add(manager);
                }
            }

            AsyncCommandBuilder.wrap(new RegionManagerSaver(managers), sender)
                    .registerWithSupervisor(worldGuard.getSupervisor(), "正在保存所有世界的区域")
                    .sendMessageAfterDelay("（请稍候...正在保存所有世界的区域数据...）")
                    .onSuccess("已成功保存所有世界的区域数据。", null)
                    .onFailure("无法保存所有世界的区域", worldGuard.getExceptionConverter())
                    .buildAndExec(worldGuard.getExecutorService());
        }
    }

    /**
     * Migrate the region database.
     *
     * @param args the arguments
     * @param sender the sender
     * @throws CommandException any error
     */
    @Command(aliases = {"migratedb"}, usage = "<from> <to>",
             flags = "y",
             desc = "从一个保护数据库迁移到另一个。", min = 2, max = 2)
    public void migrateDB(CommandContext args, Actor sender) throws CommandException {
        // Check permissions
        if (!getPermissionModel(sender).mayMigrateRegionStore()) {
            throw new CommandPermissionsException();
        }

        DriverType from = Enums.findFuzzyByValue(DriverType.class, args.getString(0));
        DriverType to = Enums.findFuzzyByValue(DriverType.class, args.getString(1));

        if (from == null) {
            throw new CommandException("'from' 的值不是受支持的区域数据库类型。");
        }

        if (to == null) {
            throw new CommandException("'to' 的值不是受支持的区域数据库类型。");
        }

        if (from == to) {
            throw new CommandException("无法在相同类型的区域数据库之间进行迁移。");
        }

        if (!args.hasFlag('y')) {
            throw new CommandException("此命令具有潜在危险性。\n" +
                    "请确保已备份数据，然后在命令末尾加上 -y 重新输入以继续。");
        }

        ConfigurationManager config = WorldGuard.getInstance().getPlatform().getGlobalStateManager();
        RegionDriver fromDriver = config.regionStoreDriverMap.get(from);
        RegionDriver toDriver = config.regionStoreDriverMap.get(to);

        if (fromDriver == null) {
            throw new CommandException("你的 WorldGuard 版本似乎不支持 'from' 指定的驱动。");
        }

        if (toDriver == null) {
            throw new CommandException("你的 WorldGuard 版本似乎不支持 'to' 指定的驱动。");
        }

        DriverMigration migration = new DriverMigration(fromDriver, toDriver, WorldGuard.getInstance().getFlagRegistry());

        LoggerToChatHandler handler = null;
        Logger minecraftLogger = null;

        if (sender instanceof LocalPlayer) {
            handler = new LoggerToChatHandler(sender);
            handler.setLevel(Level.ALL);
            minecraftLogger = Logger.getLogger("com.sk89q.worldguard");
            minecraftLogger.addHandler(handler);
        }

        try {
            RegionContainer container = WorldGuard.getInstance().getPlatform().getRegionContainer();
            sender.print("正在执行迁移...这可能需要一段时间。");
            container.migrate(migration);
            sender.print(
                    "迁移完成！此次仅迁移了数据。如果你已将设置改为使用 " +
                    "目标驱动，那么 WorldGuard 现在正在使用新数据。如果没有，你需要调整 " +
                    "配置以使用新驱动，然后重启服务器。");
        } catch (MigrationException e) {
            log.log(Level.WARNING, "Failed to migrate", e);
            throw new CommandException("迁移过程中遇到错误： " + e.getMessage());
        } finally {
            if (minecraftLogger != null) {
                minecraftLogger.removeHandler(handler);
            }
        }
    }

    /**
     * Migrate the region databases to use UUIDs rather than name.
     *
     * @param args the arguments
     * @param sender the sender
     * @throws CommandException any error
     */
    @Command(aliases = {"migrateuuid"},
            desc = "迁移已加载的数据库以使用 UUID", max = 0)
    public void migrateUuid(CommandContext args, Actor sender) throws CommandException {
        // Check permissions
        if (!getPermissionModel(sender).mayMigrateRegionNames()) {
            throw new CommandPermissionsException();
        }

        LoggerToChatHandler handler = null;
        Logger minecraftLogger = null;

        if (sender instanceof LocalPlayer) {
            handler = new LoggerToChatHandler(sender);
            handler.setLevel(Level.ALL);
            minecraftLogger = Logger.getLogger("com.sk89q.worldguard");
            minecraftLogger.addHandler(handler);
        }

        try {
            ConfigurationManager config = WorldGuard.getInstance().getPlatform().getGlobalStateManager();
            RegionContainer container = WorldGuard.getInstance().getPlatform().getRegionContainer();
            RegionDriver driver = container.getDriver();
            UUIDMigration migration = new UUIDMigration(driver, WorldGuard.getInstance().getProfileService(), WorldGuard.getInstance().getFlagRegistry());
            migration.setKeepUnresolvedNames(config.keepUnresolvedNames);
            sender.print("正在执行迁移...这可能需要一段时间。");
            container.migrate(migration);
            sender.print("迁移完成！");
        } catch (MigrationException e) {
            log.log(Level.WARNING, "Failed to migrate", e);
            throw new CommandException("迁移过程中遇到错误： " + e.getMessage());
        } finally {
            if (minecraftLogger != null) {
                minecraftLogger.removeHandler(handler);
            }
        }
    }


    /**
     * Migrate regions that went from 0-255 to new world heights.
     *
     * @param args the arguments
     * @param sender the sender
     * @throws CommandException any error
     */
    @Command(aliases = {"migrateheights"},
            usage = "[world]", max = 1,
            flags = "yw:",
            desc = "将区域从旧高度限制迁移到新高度限制")
    public void migrateHeights(CommandContext args, Actor sender) throws CommandException {
        // Check permissions
        if (!getPermissionModel(sender).mayMigrateRegionHeights()) {
            throw new CommandPermissionsException();
        }

        if (!args.hasFlag('y')) {
            throw new CommandException("此命令具有潜在危险性。\n" +
                    "请确保已备份数据，然后在命令末尾加上 -y 重新输入以继续。");
        }

        World world = null;
        try {
            world = checkWorld(args, sender, 'w');
        } catch (CommandException ignored) {
        }

        LoggerToChatHandler handler = null;
        Logger minecraftLogger = null;

        if (sender instanceof LocalPlayer) {
            handler = new LoggerToChatHandler(sender);
            handler.setLevel(Level.ALL);
            minecraftLogger = Logger.getLogger("com.sk89q.worldguard");
            minecraftLogger.addHandler(handler);
        }

        try {
            RegionContainer container = WorldGuard.getInstance().getPlatform().getRegionContainer();
            RegionDriver driver = container.getDriver();
            WorldHeightMigration migration = new WorldHeightMigration(driver, WorldGuard.getInstance().getFlagRegistry(), world);
            container.migrate(migration);
            sender.print("迁移完成！");
        } catch (MigrationException e) {
            log.log(Level.WARNING, "Failed to migrate", e);
            throw new CommandException("迁移过程中遇到错误： " + e.getMessage());
        } finally {
            if (minecraftLogger != null) {
                minecraftLogger.removeHandler(handler);
            }
        }
    }


    /**
     * Teleport to a region
     *
     * @param args the arguments
     * @param sender the sender
     * @throws CommandException any error
     */
    @Command(aliases = {"teleport", "tp"},
             usage = "[-w world] [-c|s] <id>",
             flags = "csw:",
             desc = "将你传送到与该区域关联的位置。",
             min = 1, max = 1)
    public void teleport(CommandContext args, Actor sender) throws CommandException {
        LocalPlayer player = worldGuard.checkPlayer(sender);
        Location teleportLocation;

        // Lookup the existing region
        World world = checkWorld(args, player, 'w');
        RegionManager regionManager = checkRegionManager(world);
        ProtectedRegion existing = checkExistingRegion(regionManager, args.getString(0), true);

        // Check permissions
        if (!getPermissionModel(player).mayTeleportTo(existing)) {
            throw new CommandPermissionsException();
        }

        // -s for spawn location
        if (args.hasFlag('s')) {
            teleportLocation = FlagValueCalculator.getEffectiveFlagOf(existing, Flags.SPAWN_LOC, player);

            if (teleportLocation == null) {
                throw new CommandException(
                        "该区域没有关联的出生点。");
            }
        } else if (args.hasFlag('c')) {
            // Check permissions
            if (!getPermissionModel(player).mayTeleportToCenter(existing)) {
                throw new CommandPermissionsException();
            }
            Region region = WorldEditRegionConverter.convertToRegion(existing);
            if (region == null || region.getCenter() == null) {
                throw new CommandException("该区域没有中心点。");
            }
            if (player.getGameMode() == GameModes.SPECTATOR) {
                teleportLocation = new Location(world, region.getCenter(), 0, 0);
            } else {
                // TODO: Add some method to create a safe teleport location.
                // The method AbstractPlayerActor$findFreePoisition(Location loc) is no good way for this.
                // It doesn't return the found location and it can't be checked if the location is inside the region.
                throw new CommandException("中心传送仅在旁观者模式下可用。");
            }
        } else {
            teleportLocation = FlagValueCalculator.getEffectiveFlagOf(existing, Flags.TELE_LOC, player);

            if (teleportLocation == null) {
                throw new CommandException("该区域没有关联的传送点。");
            }
        }

        String message = FlagValueCalculator.getEffectiveFlagOf(existing, Flags.TELE_MESSAGE, player);

        // If the flag isn't set, use the default message
        // If message.isEmpty(), no message is sent by LocalPlayer#teleport(...)
        if (message == null) {
            message = Flags.TELE_MESSAGE.getDefault();
        }

        player.teleport(teleportLocation,
                message.replace("%id%", existing.getId()),
                "无法传送到区域 '" + existing.getId() + "'。");
    }

    @Command(aliases = {"toggle-bypass", "bypass"},
             usage = "[on|off]",
             desc = "切换区域绕过，实际忽略绕过权限。")
    public void toggleBypass(CommandContext args, Actor sender) throws CommandException {
        LocalPlayer player = worldGuard.checkPlayer(sender);
        if (!player.hasPermission("worldguard.region.toggle-bypass")) {
            throw new CommandPermissionsException();
        }
        Session session = WorldGuard.getInstance().getPlatform().getSessionManager().get(player);
        boolean shouldEnableBypass;
        if (args.argsLength() > 0) {
            String arg1 = args.getString(0);
            if (!arg1.equalsIgnoreCase("on") && !arg1.equalsIgnoreCase("off")) {
                throw new CommandException("允许的可选参数为：on、off");
            }
            shouldEnableBypass = arg1.equalsIgnoreCase("on");
        } else {
            shouldEnableBypass = session.hasBypassDisabled();
        }
        if (shouldEnableBypass) {
            session.setBypassDisabled(false);
            player.print("你现在正在绕过区域保护（只要你拥有权限）。");
        } else {
            session.setBypassDisabled(true);
            player.print("你已不再绕过区域保护。");
        }
    }

    private static class FlagListBuilder implements Callable<Component> {
        private final FlagRegistry flagRegistry;
        private final RegionPermissionModel permModel;
        private final ProtectedRegion existing;
        private final World world;
        private final String regionId;
        private final Actor sender;
        private final String flagName;

        FlagListBuilder(FlagRegistry flagRegistry, RegionPermissionModel permModel, ProtectedRegion existing,
                        World world, String regionId, Actor sender, String flagName) {
            this.flagRegistry = flagRegistry;
            this.permModel = permModel;
            this.existing = existing;
            this.world = world;
            this.regionId = regionId;
            this.sender = sender;
            this.flagName = flagName;
        }

        @Override
        public Component call() {
            ArrayList<String> flagList = new ArrayList<>();

            // Need to build a list
            for (Flag<?> flag : flagRegistry) {
                // Can the user set this flag?
                if (!permModel.maySetFlag(existing, flag)) {
                    continue;
                }

                flagList.add(flag.getName());
            }

            Collections.sort(flagList);

            final TextComponent.Builder builder = TextComponent.builder("可用规则： ");

            final HoverEvent clickToSet = HoverEvent.of(HoverEvent.Action.SHOW_TEXT, TextComponent.of("点击设置"));
            for (int i = 0; i < flagList.size(); i++) {
                String flag = flagList.get(i);

                builder.append(TextComponent.of(flag, i % 2 == 0 ? TextColor.GRAY : TextColor.WHITE)
                        .hoverEvent(clickToSet).clickEvent(ClickEvent.of(ClickEvent.Action.SUGGEST_COMMAND,
                                "/rg flag -w \"" + world.getName() + "\" " + regionId + " " + flag + " ")));
                if (i < flagList.size() + 1) {
                    builder.append(TextComponent.of(", "));
                }
            }

            Component ret = ErrorFormat.wrap("指定了未知的规则： " + flagName)
                    .append(TextComponent.newline())
                    .append(builder.build());
            if (sender.isPlayer()) {
                return ret.append(TextComponent.of("或使用命令 ", TextColor.LIGHT_PURPLE)
                                .append(TextComponent.of("/rg flags " + regionId, TextColor.AQUA)
                                    .clickEvent(ClickEvent.of(ClickEvent.Action.RUN_COMMAND,
                                        "/rg flags -w \"" + world.getName() + "\" " + regionId))));
            }
            return ret;
        }
    }
}
