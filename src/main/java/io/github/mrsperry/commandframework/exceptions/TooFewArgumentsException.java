package io.github.mrsperry.commandframework.exceptions;

public final class TooFewArgumentsException extends CommandException {
    public TooFewArgumentsException() {
        super("Too few arguments");
    }
}
