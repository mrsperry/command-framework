package io.github.mrsperry.commandframework.exceptions;

public final class UsageException extends CommandException {
    public UsageException(final String usage) {
        super(usage);
    }
}
