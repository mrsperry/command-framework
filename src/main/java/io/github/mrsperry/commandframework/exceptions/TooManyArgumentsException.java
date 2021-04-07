package io.github.mrsperry.commandframework.exceptions;

public final class TooManyArgumentsException extends CommandException {
    public TooManyArgumentsException() {
        super("Too many arguments");
    }
}
