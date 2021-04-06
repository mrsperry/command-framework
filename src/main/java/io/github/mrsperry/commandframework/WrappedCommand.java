package io.github.mrsperry.commandframework;

import com.google.common.collect.Sets;
import org.bukkit.ChatColor;
import org.bukkit.command.CommandSender;

import java.util.*;

/** A command wrapper containing all relevant information a command would need to execute. See {@link Command} for detailed descriptions of each variable */
final class WrappedCommand {
    private final String name;
    private final Set<String> aliases;
    private final Set<String> identifiers;
    private final String usage;
    private final String description;
    private final boolean playerOnly;
    private final int minArgs;
    private final int maxArgs;
    private final Map<String, Boolean> flags;
    private final Set<String> permissions;

    /** If context should be sent when this command is executed */
    private boolean sendContext;

    protected WrappedCommand(final Command command) {
        this.name = command.name();
        this.aliases = Sets.newHashSet(command.aliases());

        this.identifiers = new HashSet<>();
        this.identifiers.add(this.name);
        this.identifiers.addAll(this.aliases);

        final String usage = command.usage();
        if (usage.equals("")) {
            this.usage = "No usage provided for '" + this.name + "'";
        } else {
            this.usage = "/" + this.name + " " + command.usage();
        }

        final String description = command.description();
        if (description.equals("")) {
            this.description = "No description provided for '" + this.name + "'";
        } else {
            this.description = description;
        }

        this.playerOnly = command.playerOnly();
        this.minArgs = command.minArgs();
        this.maxArgs = command.maxArgs();

        this.flags = new HashMap<>();
        for (final String flag : command.flags()) {
            if (flag.endsWith(":")) {
                this.flags.put(flag.substring(0, flag.length() - 1), true);
            } else {
                this.flags.put(flag, false);
            }
        }

        this.permissions = new HashSet<>(Arrays.asList(command.permissions()));

        this.sendContext = false;
    }

    /** Used when a sender other than a player runs a player-only command */
    protected final void playerOnly(final CommandSender sender) {
        sender.sendMessage(ChatColor.RED + "You must be a player to use this command.");
    }

    /** Used when a sender does not have permission to run this command */
    protected final void noPermission(final CommandSender sender) {
        sender.sendMessage(ChatColor.RED + "You do not have permission to use this command.");
    }

    /** Used when there are too few arguments to run this command */
    protected final void tooFewArguments(final CommandSender sender) {
        sender.sendMessage(ChatColor.RED + "Too few arguments.");
        this.usage(sender);
    }

    /** Used when there are too many arguments to run this command */
    protected final void tooManyArguments(final CommandSender sender) {
        sender.sendMessage(ChatColor.RED + "Too many arguments.");
        this.usage(sender);
    }

    /** Used when a flag is present that is not supported by this command */
    protected final void noSuchFlag(final CommandSender sender, final String flag) {
        sender.sendMessage(ChatColor.RED + "Flag '" + flag + "' is not supported on this command.");
        this.usage(sender);
    }

    /** Used when a flag is supported by this command but requires a value after it and none was found */
    protected final void flagRequiresValue(final CommandSender sender, final String flag) {
        sender.sendMessage(ChatColor.RED + "Flag '" + flag + "' requires a value after it.");
        this.usage(sender);
    }

    /** Used when the usage of this command should be displayed */
    protected final void usage(final CommandSender sender) {
        sender.sendMessage(ChatColor.RED + "Usage: " + this.getUsage());
    }

    /**
     * @param name The name of the command
     * @return If this command contains the given name
     */
    protected final boolean identify(final String name) {
        return this.identifiers.contains(name);
    }

    protected final String getName() {
        return this.name;
    }

    protected final Set<String> getAliases() {
        return this.aliases;
    }

    protected final Set<String> getIdentifiers() {
        return this.identifiers;
    }

    protected final String getUsage() {
        return this.usage;
    }

    protected final String getDescription() {
        return this.description;
    }

    protected final boolean isPlayerOnly() {
        return this.playerOnly;
    }

    protected final int getMinArgs() {
        return this.minArgs;
    }

    protected final int getMaxArgs() {
        return this.maxArgs;
    }

    protected final boolean supportsFlag(final String flag) {
        return this.flags.containsKey(flag);
    }

    protected final boolean flagRequiresValue(final String flag) {
        return this.flags.getOrDefault(flag, null);
    }

    protected final boolean hasPermission(final String permission) {
        return this.permissions.isEmpty() || this.permissions.contains(permission);
    }

    protected final void sendContext() {
        this.sendContext = true;
    }

    protected final boolean shouldSendContext() {
        return this.sendContext;
    }
}
