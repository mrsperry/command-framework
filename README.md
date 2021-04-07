# About
Based on sk89q's command framework. Includes tab completion support.

# Usage
## Registration
To use the framework you will need to register classes with the command manager.

```java
public class Example extends JavaPlugin {
    @Override
    public void onEnable() {
        CommandManager manager = new CommandManager(this);
        manager.register(new Commands()).buildCommands();
    }
}
```

You may chain registration calls to register all of your classes. Once your classes have been registered, you must call the `buildCommands()` method in order to complete the registration.

## Execution methods
Command methods may be anywhere in the class however they must have the `@Command` annotation. They may include a single argument of type `CommandContext` to access sender, arguments, ect.

```java
public class Commands {
    @Command(name = "circle")
    public void circle() {
        Bukkit.getLogger().info("Circle command");
    }
}
```

The name of the method is irrelevant and can be named whatever you like. The name in the annotation will be the command that you run.

To view the entire list of arguments the `@Command` annotation takes, see [the @Command file](/src/main/java/io/github/mrsperry/commandframework/annotations/Command.java).

## Completion methods
Tab completion can be dynamic or static. They work seamlessly together so feel free to use both for any command.

---

Dynamic completion allows you to change what completion terms are displayed based on external conditions such as previous arguments or the command sender.

Dynamic completions should be in their own method, separate from the execution method.

```java
public class Commands {
    @Command(name = "circle")
    public void circle() {
        Bukkit.getLogger().info("Circle command");
    }

    @Completion("circle")
    public List<String> circleCompletion(CompletionContext context) {
        return Lists.newArrayList("always");
    }
}
```

In this example, "always" will be displayed for every argument.

---

Static completion will always be displayed meaning it won't change based on previous arguments.

When using static completion, multiple completions for a single argument index are separated using the pipe `|` symbol.

```java
public class Commands {
    @Command(name = "circle")
    @StaticCompletion({ "one", "two|three" })
    public void circle(CommandContext context) {
        Bukkit.getLogger().info("Circle command");
    }
}
```

In this example, "one" will be displayed when the first argument is typed. The strings "two" and "three" will be displayed on the second argument. No tab completion will be displayed for arguments beyond that.