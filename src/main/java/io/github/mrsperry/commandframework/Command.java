package io.github.mrsperry.commandframework;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface Command {
    /** The unique name of this command */
    String name();

    /** An array of unique supplementary names of this command */
    String[] aliases() default {};

    /** The short hand usage of this command */
    String usage() default "";

    /** A user friendly description of what this command does */
    String description() default "";

    /** If this command may only be run by players */
    boolean playerOnly() default false;

    /** The minimum number of arguments required to run this command */
    int minArgs() default 0;

    /** The maximum number of arguments allowed to run this command (-1 means no limit) */
    int maxArgs() default -1;

    /**
     * An array of accepted flag identifiers that will not be included in the argument array
     *
     * Flags are characters that are prefixed by a dash (-)
     * Flags may optionally have a postfix of a color (:) to denote that a value must follow the flag
     */
    String[] flags() default {};

    /** An array of permission strings (only one must match for the sender to have permission) */
    String[] permissions() default {};
}
