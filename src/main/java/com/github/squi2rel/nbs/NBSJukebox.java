package com.github.squi2rel.nbs;

import com.github.squi2rel.nbs.nbs.NBSReader;
import com.github.squi2rel.nbs.nbs.NBSSong;
import com.github.squi2rel.nbs.nms.NBSJukeboxBlockEntity;
import com.github.squi2rel.nbs.util.NBSUtil;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.JukeboxBlockEntity;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import org.bukkit.ChatColor;
import org.bukkit.NamespacedKey;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.command.PluginCommand;
import org.bukkit.craftbukkit.CraftChunk;
import org.bukkit.craftbukkit.CraftWorld;
import org.bukkit.craftbukkit.block.CraftBlock;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.world.ChunkLoadEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.components.JukeboxPlayableComponent;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.logging.Level;
import java.util.stream.Stream;

@SuppressWarnings("UnstableApiUsage")
public final class NBSJukebox extends JavaPlugin implements Listener {

    public static Map<String, NBSSong> registry = new HashMap<>();

    public static final String ID = "nbsjukebox:song";
    public static final String PLAYTIME = "nbsjukebox:playtime";
    public static final NamespacedKey ID_KEY = Objects.requireNonNull(NamespacedKey.fromString(ID));
    public static final NamespacedKey PLAYTIME_KEY = Objects.requireNonNull(NamespacedKey.fromString(PLAYTIME));

    public static Map<String, Sound> soundsMap = new HashMap<>();

    @SuppressWarnings("deprecation")
    @Override
    public void onEnable() {
        // Plugin startup logic
        getServer().getPluginManager().registerEvents(this, this);

        PluginCommand cmd = Objects.requireNonNull(getCommand("nbswrite"));
        cmd.setExecutor((s, c, l, args) -> {
            if (args.length == 0) return false;
            String key = String.join(" ", args);
            if (!registry.containsKey(key)) return false;
            if (!(s instanceof Player p)) return true;
            ItemStack item = p.getInventory().getItemInMainHand();
            if (item.isEmpty()) return true;
            ItemMeta meta = item.getItemMeta();
            if (!meta.hasJukeboxPlayable()) {
                JukeboxPlayableComponent playable = meta.getJukeboxPlayable();
                playable.setShowInTooltip(false);
                meta.setJukeboxPlayable(playable);
            }
            meta.setLore(List.of(ChatColor.GRAY + key));
            PersistentDataContainer container = meta.getPersistentDataContainer();
            container.set(ID_KEY, PersistentDataType.STRING, key);
            item.setItemMeta(meta);
            return true;
        });
        cmd.setTabCompleter((s, c, l, args) -> {
            String full = String.join(" ", args);
            int len = full.length();
            return registry.keySet().stream().filter(n -> n.startsWith(full)).map(n -> n.substring(Math.min(n.length(), len))).toList();
        });

        Objects.requireNonNull(getCommand("nbsreload")).setExecutor((s, c, l, args) -> {
            loadSongs();
            return true;
        });

        loadSongs();

        try (InputStream in = getResource("sounds.json")) {
            soundsMap = NBSUtil.buildReverseMap(in);
        } catch (IOException e) {
            getLogger().log(Level.WARNING, "Failed to load sounds.json", e);
            soundsMap = new HashMap<>();
        }

        for (World world : getServer().getWorlds()) {
            ServerLevel level = ((CraftWorld) world).getHandle();
            List<JukeboxBlockEntity> list = new ArrayList<>();
            for (BlockEntity entity : level.capturedTileEntities.values()) {
                if (entity instanceof JukeboxBlockEntity j) list.add(j);
            }
            for (JukeboxBlockEntity j : list) level.setBlockEntity(new NBSJukeboxBlockEntity(j));
        }
    }

    public void loadSongs() {
        registry.clear();
        File folder = getDataFolder();
        if (!folder.exists() && !folder.mkdirs()) throw new RuntimeException("Could not create folder!");
        try (Stream<Path> s = Files.list(folder.toPath())) {
            s.filter(p -> p.getFileName().toString().endsWith(".nbs")).filter(Files::isRegularFile).forEach(path -> {
                String name = path.getFileName().toString();
                try {
                    registry.put(name.substring(0, name.length() - 4), NBSReader.parse(path.toFile()));
                } catch (IOException e) {
                    getLogger().log(Level.WARNING, "Failed to load NBS song " + name, e);
                }
            });
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        getLogger().info("Loaded " + registry.size() + " NBS songs");
    }

    @Override
    public void onDisable() {
        // Plugin shutdown logic
        for (World world : getServer().getWorlds()) {
            ServerLevel level = ((CraftWorld) world).getHandle();
            List<JukeboxBlockEntity> list = new ArrayList<>();
            for (BlockEntity entity : level.capturedTileEntities.values()) {
                if (entity instanceof NBSJukeboxBlockEntity nbs) list.add(nbs.getOriginal());
            }
            for (JukeboxBlockEntity entity : list) level.setBlockEntity(entity);
        }
    }

    @EventHandler
    public void onChunkLoad(ChunkLoadEvent e) {
        LevelChunk chunk = (LevelChunk) ((CraftChunk) e.getChunk()).getHandle(ChunkStatus.FULL);
        for (BlockPos pos : chunk.getBlockEntitiesPos()) {
            BlockEntity entity = chunk.getBlockEntity(pos);
            if (entity instanceof JukeboxBlockEntity j) {
                NBSJukeboxBlockEntity jukebox = new NBSJukeboxBlockEntity(j);
                chunk.addAndRegisterBlockEntity(jukebox);
                jukebox.onLoad();
            }
        }
    }

    @EventHandler
    public void onBlockPlace(BlockPlaceEvent e) {
        CraftBlock block = (CraftBlock) e.getBlock();
        LevelAccessor world = block.getHandle();
        ChunkAccess chunk = world.getChunk(block.getPosition());
        BlockEntity old = chunk.getBlockEntity(block.getPosition());
        if (!(old instanceof JukeboxBlockEntity j)) return;
        NBSJukeboxBlockEntity jukebox = new NBSJukeboxBlockEntity(j);
        chunk.setBlockEntity(jukebox);
        jukebox.onLoad();
    }
}
