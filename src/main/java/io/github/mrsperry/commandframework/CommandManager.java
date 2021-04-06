package io.github.mrsperry.commandframework;

import org.bukkit.Server;
import org.bukkit.command.*;
import org.bukkit.entity.Player;
import org.bukkit.permissions.PermissionAttachmentInfo;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;

import java.lang.reflect.*;
import java.util.*;

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
     * Registers all methods in a class that use the {@link Command} annotation
     *
     * If a command method has a non-unique identifier (name and aliases) a warning will be logged and the command will not be registered
     * @param clazz The class who's methods will be registered
     * @return The instance of the command manager, allowing for chaining register calls
     */
    public final CommandManager register(final Class<?> clazz) {
        for (final Method method : clazz.getMethods()) {
            // Skip methods not marked with the command annotation
            if (!method.isAnnotationPresent(Command.class)) {
                continue;
            }

            // Create the new command
            final WrappedCommand command = new WrappedCommand(method.getAnnotation(Command.class));
            final Set<String> identifiers = command.getIdentifiers();

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

        return this;
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
            final String pluginName = this.plugin.getName();

            // Add each command to the command map
            for (final WrappedCommand wrapped : this.commands.keySet()) {
                final Type[] params = this.commands.get(wrapped).getGenericParameterTypes();

                // Check if command context should be sent when the command is executed
                if (params.length == 1) {
                    if (params[0].equals(CommandContext.class)) {
                        wrapped.sendContext();
                    } else {
                        throw new IllegalArgumentException("The only argument in command methods must be of type CommandContext");
                    }
                } else if (params.length != 0) {
                    throw new IllegalArgumentException("Command methods may only contain zero or one arguments");
                }

                // Register each identifier (name and all aliases) for this command
                for (final String id : wrapped.getIdentifiers()) {
                    final PluginCommand command = pluginCommand.newInstance(id, this.plugin);
                    commandMap.register(pluginName, command);
                }
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
    public final void execute(final CommandSender sender, final String command, final String[] originalArgs) {
        for (final WrappedCommand cmd : this.commands.keySet()) {
            // Only execute if the command name is an identifier of the current wrapped command
            if (!cmd.identify(command.toLowerCase())) {
                continue;
            }

            // Check if the sender must be a player
            if (cmd.isPlayerOnly() && !(sender instanceof Player)) {
                cmd.playerOnly(sender);
                return;
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
                cmd.noPermission(sender);
                return;
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
                    cmd.noSuchFlag(sender, arg);
                    return;
                }

                // Check if this flag requires a value trailing it
                if (cmd.flagRequiresValue(strippedArg)) {
                    if (originalArgs.length <= index + 1) {
                        cmd.flagRequiresValue(sender, arg);
                        return;
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
                cmd.tooFewArguments(sender);
                return;
            }

            // Check if there are too many arguments
            final int maxArgs = cmd.getMaxArgs();
            if (maxArgs != -1 && maxArgs < args.length) {
                cmd.tooManyArguments(sender);
                return;
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
                this.plugin.getLogger().severe("Could not access method to invoke command: " + cmd);
                ex.printStackTrace();
            } catch (final IllegalArgumentException ex) {
                this.plugin.getLogger().severe("Illegal argument passed to command method: " + cmd);
                ex.printStackTrace();
            } catch (final InvocationTargetException ex) {
                this.plugin.getLogger().severe("Could not invoke method for command: " + cmd);
                ex.printStackTrace();
            }
        }
    }
}
