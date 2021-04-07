package io.github.mrsperry.commandframework.annotations;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface StaticCompletion {
    /**
     * An array of string that will be displayed as completion information
     *
     * To add multiple completions for a single index, separate them with a pipe (|) ex: { "one", "two|three" }
     */
    String[] value();
}
