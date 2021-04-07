package io.github.mrsperry.commandframework.context;

import org.bukkit.command.CommandSender;

import java.util.Map;

/** Utility class used to store information on commands that are run */
public final class CommandContext {
    private final CommandSender sender;
    private final String[] args;
    private final Map<String, String> flags;

    public CommandContext(final CommandSender sender, final String[] args, final Map<String, String> flags) {
        this.sender = sender;
        this.args = args;
        this.flags = flags;
    }

    public final CommandSender getSender() {
        return this.sender;
    }

    public final String[] getArgs() {
        return this.args;
    }

    public final Map<String, String> getFlags() {
        return this.flags;
    }
}
