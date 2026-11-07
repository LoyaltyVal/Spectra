package com.spectra.config;

import com.spectra.Spectra;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/** Loads messages.yml (falling back to the bundled copy) and renders MiniMessage. */
public final class Messages {

    private static final MiniMessage MM = MiniMessage.miniMessage();

    private final Spectra plugin;
    private YamlConfiguration yaml = new YamlConfiguration();
    private TagResolver prefix = TagResolver.empty();

    public Messages(Spectra plugin) {
        this.plugin = plugin;
    }

    public void load() {
        File file = new File(plugin.getDataFolder(), "messages.yml");
        YamlConfiguration loaded = YamlConfiguration.loadConfiguration(file);
        InputStream bundled = plugin.getResource("messages.yml");
        if (bundled != null) {
            loaded.setDefaults(YamlConfiguration.loadConfiguration(new InputStreamReader(bundled, StandardCharsets.UTF_8)));
        }
        this.yaml = loaded;
        this.prefix = Placeholder.parsed("prefix", loaded.getString("prefix", ""));
    }

    public String raw(String key) {
        return yaml.getString(key, key);
    }

    public Component get(String key, TagResolver... resolvers) {
        return MM.deserialize(raw(key), TagResolver.resolver(TagResolver.resolver(resolvers), prefix));
    }

    public List<Component> list(String key, TagResolver... resolvers) {
        TagResolver all = TagResolver.resolver(TagResolver.resolver(resolvers), prefix);
        List<String> lines = yaml.getStringList(key);
        if (lines.isEmpty() && yaml.isString(key)) {
            lines = List.of(raw(key));
        }
        List<Component> out = new ArrayList<>(lines.size());
        for (String line : lines) {
            out.add(MM.deserialize(line, all));
        }
        return out;
    }

    public void send(CommandSender sender, String key, TagResolver... resolvers) {
        sender.sendMessage(get(key, resolvers));
    }

    public void sendList(CommandSender sender, String key, TagResolver... resolvers) {
        for (Component line : list(key, resolvers)) {
            sender.sendMessage(line);
        }
    }
}
