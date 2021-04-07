package io.github.mrsperry.commandframework.context;

import org.bukkit.command.CommandSender;

/** Utility class used to store information on a command's tab completion */
public final class CompletionContext {
    private final CommandSender sender;
    private final String[] args;

    public CompletionContext(final CommandSender sender, final String[] args) {
        this.sender = sender;
        this.args = args;
    }

    public final CommandSender getSender() {
        return this.sender;
    }

    public final String[] getArgs() {
        return this.args;
    }
}
