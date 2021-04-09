package io.github.mrsperry.commandframework;

import com.google.common.collect.Lists;
import io.github.mrsperry.commandframework.annotations.Command;
import io.github.mrsperry.commandframework.annotations.Completion;
import io.github.mrsperry.commandframework.annotations.StaticCompletion;
import io.github.mrsperry.commandframework.context.CommandContext;
import io.github.mrsperry.commandframework.context.CompletionContext;
import io.github.mrsperry.commandframework.exceptions.*;
import org.bukkit.Server;
import org.bukkit.command.*;
import org.bukkit.entity.Player;
import org.bukkit.permissions.PermissionAttachmentInfo;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.util.StringUtil;

import java.io.*;
import java.lang.reflect.*;
import java.util.*;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

public final class CommandManager {
    /** A map of executable commands and their respective methods */
    private final Map<WrappedCommand, Method> commands;
    /** The plugin instance for this manager */
    private final JavaPlugin plugin;

    /**
     * Creates a new command manager instance; used to register commands using annotations
     * @param plugin The owning plugin
     */
    public CommandManager(final JavaPlugin plugin) {
        this.commands = new HashMap<>();
        this.plugin = plugin;
    }

    /**
     * Registers all classes in the plugin's package
     *
     * For recursive registering use {@code registerPackage(File jar, String packageName, boolean recursive}
     * @param jar The file of the plugin
     * @return The instance of the command manager, allowing for chaining register calls
     */
    public final CommandManager registerPackage(final File jar) {
        return this.registerPackage(jar, null);
    }

    /**
     * Registers all classes in the given sub-package
     *
     * This will not register any classes in the main package and it is not recursive
     * @param jar The file of the plugin
     * @param packageName The name of the sub-package (period separated, ex: "your.sub.package")
     * @return The instance of the command manager, allowing for chaining register calls
     */
    public final CommandManager registerPackage(final File jar, final String packageName) {
        return this.registerPackage(jar, packageName, false);
    }

    /**
     * Registers all classes in the given sub-package
     *
     * This will not register any classes in the main package but will recursively register all sub-packages
     * @param jar The file of the plugin
     * @param packageName The name of the sub-package (period separated, ex: "your.sub.package")
     * @param recursive If sub-packages of the given sub-package should be registered
     * @return The instance of the command manager, allowing for chaining register calls
     */
    public final CommandManager registerPackage(final File jar, String packageName, final boolean recursive) {
        // Set the main plugin package as the package name
        final String packageCopy = packageName;
        packageName = this.plugin.getClass().getPackageName();

        // Add a subpackage if available
        if (packageCopy != null) {
            packageName += "." + packageCopy;
        }
        packageName = packageName.replace(".", "/");

        final List<String> classNames = new ArrayList<>();
        try {
            // Get a stream of all files in the plugin jar
            final InputStream input = new FileInputStream(jar.getAbsolutePath());
            final ZipInputStream zip = new ZipInputStream(input);

            for (ZipEntry entry = zip.getNextEntry(); entry != null; entry = zip.getNextEntry()) {
                if (entry.isDirectory()) {
                    continue;
                }

                String name = entry.getName();
                if (!name.endsWith(".class")) {
                    continue;
                }
                name = name.substring(0, name.length() - ".class".length());

                // Get the package name of the current class
                final String[] location = name.split("/");
                final StringBuilder currentPackage = new StringBuilder();
                for (int index = 0; index < location.length - 1; index++) {
                    currentPackage.append(location[index]).append("/");
                }
                // Remove trailing slash
                currentPackage.deleteCharAt(currentPackage.length() - 1);

                // Check if this class is in the given package or a sub-package if recursive
                if (recursive) {
                    if (!currentPackage.toString().startsWith(packageName)) {
                        continue;
                    }
                } else {
                    if (!packageName.equals(currentPackage.toString())) {
                        continue;
                    }
                }

                classNames.add(name.replace("/", "."));
            }
        } catch (final Exception ex) {
            this.plugin.getLogger().severe("Could not read package entries for: " + packageName.replace("/", "."));
            ex.printStackTrace();
            return this;
        }

        // Register each class
        for (final String name : classNames) {
            try {
                this.registerClass(Class.forName(name));
            } catch (final Exception ex) {
                this.plugin.getLogger().severe("Could not get class to register command: " + name);
                ex.printStackTrace();
                return this;
            }
        }

        return this;
    }

    /**
     * Registers all methods in a class that use the {@link Command} annotation
     *
     * If a command method has a non-unique identifier (name and aliases) a warning will be logged and the command will not be registered
     * @param clazz The class who's methods will be registered
     * @return The instance of the command manager, allowing for chaining register calls
     */
    public final CommandManager registerClass(final Class<?> clazz) {
        final Method[] methods = clazz.getMethods();

        for (final Method method : methods) {
            // Skip methods not marked with the command annotation
            if (method.isAnnotationPresent(Command.class)) {
                this.registerExecutor(method);
            }
        }

        // Run completion setup after creating all wrapped commands
        for (final Method method : methods) {
            // Skip methods not marked with the completion annotations
            if (method.isAnnotationPresent(Completion.class)) {
                this.registerCompletion(method, false);
            } else if (method.isAnnotationPresent(StaticCompletion.class)) {
                this.registerCompletion(method, true);
            }
        }

        return this;
    }

    /**
     * Registers a command executor method
     * @param method The method to register
     */
    private void registerExecutor(final Method method) {
        // Create the new command
        final WrappedCommand command = new WrappedCommand(method.getAnnotation(Command.class));
        final Set<String> identifiers = command.getIdentifiers();

        // Check if command context should be sent when the command is executed
        final Type[] params = method.getGenericParameterTypes();
        if (params.length == 1) {
            if (params[0].equals(CommandContext.class)) {
                command.sendContext();
            } else {
                this.plugin.getLogger().severe("The only argument in command methods must be of type CommandContext: ");
                return;
            }
        } else if (params.length != 0) {
            this.plugin.getLogger().severe("Command methods may only contain zero or one arguments");
            return;
        }

        // Check for duplicate identifiers in this command
        boolean register = true;
        for (final WrappedCommand current : this.commands.keySet()) {
            String duplicateID = null;

            for (final String id : current.getIdentifiers()) {
                if (identifiers.contains(id)) {
                    duplicateID = id;
                    register = false;
                    break;
                }
            }

            if (duplicateID != null) {
                this.plugin.getLogger().severe("A duplicate command identifier was found and will not be registered: " + duplicateID);
                break;
            }
        }

        // Register the command
        if (register) {
            this.commands.put(command, method);
        }
    }

    /**
     * Registers a command tab completion method
     * @param method The method to register
     * @param isStatic If the completion is static or dynamic
     */
    private void registerCompletion(final Method method, final boolean isStatic) {
        final String name;
        final String[] completions;

        if (isStatic) {
            name = method.getAnnotation(Command.class).name();
            completions = method.getAnnotation(StaticCompletion.class).value();
        } else {
            name = method.getAnnotation(Completion.class).value();
            completions = new String[0];

            // Ensure that the dynamic completion method can be run
            try {
                final ParameterizedType type = (ParameterizedType) method.getGenericReturnType();
                if (!type.getRawType().equals(List.class)) {
                    throw new Exception();
                }

                if (!type.getActualTypeArguments()[0].equals(String.class)) {
                    throw new Exception();
                }
            } catch (final Exception ex) {
                this.plugin.getLogger().severe("Completion methods must return a List<String>: " + this.formatMethodLocation(method));
                return;
            }

            final Type[] params = method.getGenericParameterTypes();
            if (params.length != 1 || !params[0].equals(CompletionContext.class)) {
                this.plugin.getLogger().severe("Completion methods must only contain a single argument of type CompletionContext: " + this.formatMethodLocation(method));
                return;
            }
        }

        // Get the command this completion applies to
        WrappedCommand command = null;
        for (final WrappedCommand current : this.commands.keySet()) {
            if (current.identify(name)) {
                command = current;
                break;
            }
        }

        if (command == null) {
            this.plugin.getLogger().severe("Could not find command for tab completion (is the method private?): " + this.formatMethodLocation(method));
            return;
        }

        // Set the completions
        if (isStatic) {
            final Map<Integer, List<String>> completionMap = new HashMap<>();
            // Split static completions on the pipe symbol, allowing multiple completions per index
            // ex: { "one", "two|three" } -> { 1: "one", 2: { "two", "three" } }
            for (int index = 0; index < completions.length; index++) {
                completionMap.put(index, Lists.newArrayList(completions[index].split("\\|")));
            }

            command.setStaticCompletions(completionMap);
        } else {
            command.setCompletionMethod(method);
        }
    }

    /** Puts all registered commands into the Bukkit command map so that they can be accessed in-game */
    public final void buildCommands() {
        try {
            // Allow access to the global sever command map
            final Server server = this.plugin.getServer();
            final Field field = server.getClass().getDeclaredField("commandMap");
            field.setAccessible(true);

            // Allow plugin commands to be instantiated
            final Constructor<PluginCommand> pluginCommand = PluginCommand.class.getDeclaredConstructor(String.class, Plugin.class);
            pluginCommand.setAccessible(true);

            final CommandMap commandMap = (CommandMap) field.get(server);

            // Add each command to the command map
            for (final WrappedCommand wrapped : this.commands.keySet()) {
                // Register each identifier (name and all aliases) for this command
                commandMap.register(this.plugin.getName().toLowerCase(), pluginCommand.newInstance(wrapped.getName(), this.plugin)
                        .setUsage(wrapped.getUsage())
                        .setDescription(wrapped.getDescription())
                        .setAliases(new ArrayList<>(wrapped.getAliases())));
            }
        } catch (final Exception ex) {
            this.plugin.getLogger().severe("An error occurred while registering commands!");
            ex.printStackTrace();
        }
    }

    /**
     * Attempts to execute a registered command
     * @param sender The sender of the command
     * @param command The name of the command
     * @param originalArgs The arguments of the command
     */
    public final void execute(final CommandSender sender, final String command, final String[] originalArgs) throws SenderException, PermissionException, FlagException, TooFewArgumentsException, TooManyArgumentsException, IllegalArgumentException {
        for (final WrappedCommand cmd : this.commands.keySet()) {
            // Only execute if the command name is an identifier of the current wrapped command
            if (!cmd.identify(command.toLowerCase())) {
                continue;
            }

            // Check if the sender must be a player
            if (cmd.isPlayerOnly() && !(sender instanceof Player)) {
                throw new SenderException("You must be a player to use this command");
            }

            // Check if the sender has permission
            boolean hasPermission = false;
            if (sender.isOp()
                    || sender instanceof ConsoleCommandSender
                    || sender instanceof RemoteConsoleCommandSender
                    || sender instanceof BlockCommandSender) {
                hasPermission = true;
            } else {
                for (final PermissionAttachmentInfo permission : sender.getEffectivePermissions()) {
                    if (cmd.hasPermission(permission.getPermission())) {
                        hasPermission = true;
                        break;
                    }
                }
            }

            if (!hasPermission) {
                throw new PermissionException();
            }

            // Split flags from actual arguments
            final List<String> argList = new ArrayList<>();
            final Map<String, String> flags = new HashMap<>();

            for (int index = 0; index < originalArgs.length; index++) {
                final String arg = originalArgs[index];

                // Check if this argument is a flag
                if (!arg.startsWith("-") || arg.equals("-")) {
                    argList.add(arg);
                    continue;
                }

                // Check if this flag is supported by the command
                final String strippedArg = arg.substring(1);
                if (!cmd.supportsFlag(strippedArg)) {
                    throw new FlagException("Flag '" + arg + "' is not supported on this command");
                }

                // Check if this flag requires a value trailing it
                if (cmd.flagRequiresValue(strippedArg)) {
                    if (originalArgs.length <= index + 1) {
                        throw new FlagException("Flag '" + arg + "' requires a value proceeding it");
                    }

                    flags.put(strippedArg, originalArgs[index + 1]);
                    ++index;
                } else {
                    flags.put(strippedArg, null);
                }
            }

            // Convert the list of arguments into an array for easier access
            final String[] args = new String[argList.size()];
            for (int index = 0; index < argList.size(); index++) {
                args[index] = argList.get(index);
            }

            // Check if there are enough arguments
            if (cmd.getMinArgs() > args.length) {
                throw new TooFewArgumentsException();
            }

            // Check if there are too many arguments
            final int maxArgs = cmd.getMaxArgs();
            if (maxArgs != -1 && maxArgs < args.length) {
                throw new TooManyArgumentsException();
            }

            // Try to execute the method assigned to this command
            final Method method = this.commands.get(cmd);
            try {
                method.setAccessible(true);

                if (cmd.shouldSendContext()) {
                    method.invoke(this.plugin, new CommandContext(sender, args, flags));
                } else {
                    method.invoke(this.plugin);
                }
            } catch (final IllegalAccessException ex) {
                this.plugin.getLogger().severe("Could not access method to invoke command: " + this.formatMethodLocation(method));
                ex.printStackTrace();
            } catch (final IllegalArgumentException ex) {
                this.plugin.getLogger().severe("Illegal argument passed to command method: " + this.formatMethodLocation(method));
                ex.printStackTrace();
            } catch (final InvocationTargetException ex) {
                this.plugin.getLogger().severe("Could not invoke method for command: " + this.formatMethodLocation(method));
                ex.printStackTrace();
            }
        }
    }

    /**
     * Attempts to tab complete a registered command
     * @param sender The command sender
     * @param command The command being typed
     * @param args The command's arguments
     * @return A list of completions to display
     */
    @SuppressWarnings("unchecked")
    public final List<String> completion(final CommandSender sender, final String command, final String[] args) {
        for (final WrappedCommand cmd : this.commands.keySet()) {
            if (!cmd.identify(command.toLowerCase())) {
                continue;
            }

            // Get the initial static completions
            final List<String> completions = new ArrayList<>();
            final int index = args.length - 1;
            StringUtil.copyPartialMatches(args[index], cmd.getStaticCompletions().getOrDefault(index, new ArrayList<>()), completions);

            // Check if dynamic completions should be run
            final Method method = cmd.getCompletionMethod();
            if (method == null) {
                return completions;
            }

            // Run the dynamic completion
            try {
                method.setAccessible(true);
                completions.addAll((List<String>) method.invoke(this.plugin, new CompletionContext(sender, args)));
            } catch (final IllegalAccessException ex) {
                this.plugin.getLogger().severe("Could not access method to invoke command: " + this.formatMethodLocation(method));
                ex.printStackTrace();
            } catch (final IllegalArgumentException ex) {
                this.plugin.getLogger().severe("Illegal argument passed to command method: " + this.formatMethodLocation(method));
                ex.printStackTrace();
            } catch (final InvocationTargetException ex) {
                this.plugin.getLogger().severe("Could not invoke method for command: " + this.formatMethodLocation(method));
                ex.printStackTrace();
            }

            return completions;
        }

        return new ArrayList<>();
    }

    /**
     * Formats a method signature
     * @param method The method to format
     * @return The method signature in the style: "[method name]() in [class name]"
     */
    private String formatMethodLocation(final Method method) {
        return method.getName() + "() in " + method.getDeclaringClass().getSimpleName();
    }
}
