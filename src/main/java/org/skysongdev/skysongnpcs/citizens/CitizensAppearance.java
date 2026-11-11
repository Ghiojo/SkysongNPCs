package org.skysongdev.skysongnpcs.citizens;

import net.citizensnpcs.api.CitizensAPI;
import net.citizensnpcs.api.npc.NPC;
import net.citizensnpcs.api.trait.trait.MobType;
import net.citizensnpcs.trait.ScoreboardTrait;
import net.citizensnpcs.trait.SitTrait;
import net.citizensnpcs.trait.SkinTrait;
import net.citizensnpcs.trait.SleepTrait;
import net.citizensnpcs.trait.SneakTrait;
import net.citizensnpcs.util.MojangSkinGenerator;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.EntityType;
import org.bukkit.plugin.Plugin;
import org.skysongdev.skysongnpcs.api.appearance.Nameplate;
import org.skysongdev.skysongnpcs.api.appearance.NpcPose;
import org.skysongdev.skysongnpcs.appearance.AppearanceDriver;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/** Changes NPCs' looks through Citizens' own traits, the same ones its {@code /npc} commands use. */
public final class CitizensAppearance implements AppearanceDriver {
    private static final String HOVER = "hover";

    private final Plugin plugin;

    public CitizensAppearance(Plugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public String rawName(UUID npc) {
        return npc(npc).getRawName();
    }

    @Override
    public void rename(UUID npc, String name) {
        npc(npc).setName(name);
    }

    @Override
    public boolean hasSkin(UUID npc) {
        return npc(npc).getOrAddTrait(MobType.class).getType() == EntityType.PLAYER;
    }

    @Override
    public Optional<String> skin(UUID npc) {
        SkinTrait skin = npc(npc).getTraitNullable(SkinTrait.class);
        return skin == null ? Optional.empty() : Optional.ofNullable(skin.getSkinName()).filter(name -> !name.isBlank());
    }

    @Override
    public void skinFromPlayer(UUID npc, String player) {
        npc(npc).getOrAddTrait(SkinTrait.class).setSkinName(player, true);
    }

    @Override
    public CompletableFuture<Void> skinFromUrl(UUID npc, String url, boolean slim) {
        CompletableFuture<Void> done = new CompletableFuture<>();
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                Map<?, ?> skin = generateFromUrl(url, slim);
                Map<?, ?> texture = (Map<?, ?>) skin.get("texture");
                String uuid = String.valueOf(skin.get("uuid"));
                String signature = String.valueOf(texture.get("signature"));
                String value = String.valueOf(texture.get("value"));
                Bukkit.getScheduler().runTask(plugin, () -> {
                    NPC found = CitizensAPI.getNPCRegistry().getByUniqueId(npc);
                    if (found == null) {
                        done.completeExceptionally(new IllegalStateException("The NPC is gone"));
                        return;
                    }
                    found.getOrAddTrait(SkinTrait.class).setSkinPersistent(uuid, signature, value);
                    done.complete(null);
                });
            } catch (Exception | LinkageError failed) {
                Bukkit.getScheduler().runTask(plugin, () -> done.completeExceptionally(failed));
            }
        });
        return done;
    }

    /**
     * Citizens' own MineSkin upload, as {@code /npc skin --url} does it. Called by reflection
     * because it returns json-simple's JSONObject (a Map), which is not on our compile classpath.
     */
    private static Map<?, ?> generateFromUrl(String url, boolean slim) throws Exception {
        Method generate = MojangSkinGenerator.class.getMethod("generateFromURL", String.class, boolean.class);
        try {
            Object result = generate.invoke(null, url, slim);
            if (!(result instanceof Map<?, ?> skin) || !(skin.get("texture") instanceof Map<?, ?>)) {
                throw new IllegalStateException("MineSkin gave no skin for that link");
            }
            return skin;
        } catch (InvocationTargetException failed) {
            throw failed.getCause() instanceof Exception cause ? cause : failed;
        }
    }

    @Override
    public Nameplate nameplate(UUID npc) {
        String value = String.valueOf(npc(npc).data().get(NPC.Metadata.NAMEPLATE_VISIBLE, true));
        if (value.equalsIgnoreCase(HOVER)) {
            return Nameplate.HOVER;
        }
        return Boolean.parseBoolean(value) ? Nameplate.SHOWN : Nameplate.HIDDEN;
    }

    @Override
    public void nameplate(UUID npc, Nameplate nameplate) {
        NPC found = npc(npc);
        Object value = switch (nameplate) {
            case SHOWN -> true;
            case HIDDEN -> false;
            case HOVER -> HOVER;
        };
        found.data().setPersistent(NPC.Metadata.NAMEPLATE_VISIBLE, value);
        found.scheduleUpdate(NPC.NPCUpdate.PACKET);
    }

    @Override
    public NpcPose pose(UUID npc) {
        NPC found = npc(npc);
        SleepTrait sleep = found.getTraitNullable(SleepTrait.class);
        if (sleep != null && sleep.isSleeping()) {
            return NpcPose.LYING;
        }
        SneakTrait sneak = found.getTraitNullable(SneakTrait.class);
        return sneak != null && sneak.isSneaking() ? NpcPose.CROUCHING : NpcPose.STANDING;
    }

    @Override
    public void pose(UUID npc, NpcPose pose) {
        NPC found = npc(npc);
        SneakTrait sneak = found.getTraitNullable(SneakTrait.class);
        if (pose == NpcPose.CROUCHING || sneak != null) {
            found.getOrAddTrait(SneakTrait.class).setSneaking(pose == NpcPose.CROUCHING);
        }
        if (pose == NpcPose.LYING) {
            found.getOrAddTrait(SleepTrait.class).setSleeping(found.getStoredLocation());
        } else if (found.hasTrait(SleepTrait.class)) {
            found.getOrAddTrait(SleepTrait.class).setSleeping(null);
        }
    }

    @Override
    public boolean sitting(UUID npc) {
        SitTrait sit = npc(npc).getTraitNullable(SitTrait.class);
        return sit != null && sit.isSitting();
    }

    @Override
    public void sitting(UUID npc, boolean sitting) {
        NPC found = npc(npc);
        if (sitting) {
            found.getOrAddTrait(SitTrait.class).setSitting(found.getStoredLocation());
        } else if (found.hasTrait(SitTrait.class)) {
            found.getOrAddTrait(SitTrait.class).setSitting(null);
        }
    }

    @Override
    public Optional<NamedTextColor> glow(UUID npc) {
        NPC found = npc(npc);
        if (!Boolean.TRUE.equals(found.data().get(NPC.Metadata.GLOWING, false))) {
            return Optional.empty();
        }
        ScoreboardTrait scoreboard = found.getTraitNullable(ScoreboardTrait.class);
        ChatColor colour = scoreboard == null ? null : scoreboard.getColor();
        NamedTextColor named = colour == null ? null : NamedTextColor.NAMES.value(colour.name().toLowerCase(Locale.ROOT));
        return Optional.of(named == null ? NamedTextColor.WHITE : named);
    }

    @Override
    @SuppressWarnings("deprecation") // ScoreboardTrait still takes Bukkit's ChatColor.
    public void glow(UUID npc, NamedTextColor colour) {
        NPC found = npc(npc);
        if (colour != null) {
            String name = NamedTextColor.NAMES.key(colour);
            found.getOrAddTrait(ScoreboardTrait.class).setColor(ChatColor.valueOf(name.toUpperCase(Locale.ROOT)));
        }
        found.data().setPersistent(NPC.Metadata.GLOWING, colour != null);
    }

    @Override
    public Optional<UUID> commandTarget(CommandSender sender, String[] args) {
        for (int i = 0; i < args.length; i++) {
            String id = null;
            if (args[i].equalsIgnoreCase("--id") && i + 1 < args.length) {
                id = args[i + 1];
            } else if (args[i].toLowerCase(Locale.ROOT).startsWith("--id=")) {
                id = args[i].substring("--id=".length());
            }
            if (id != null) {
                try {
                    NPC byId = CitizensAPI.getNPCRegistry().getById(Integer.parseInt(id));
                    return byId == null ? Optional.empty() : Optional.of(byId.getUniqueId());
                } catch (NumberFormatException notANumber) {
                    break;
                }
            }
        }
        NPC selected = CitizensAPI.getDefaultNPCSelector().getSelected(sender);
        return selected == null ? Optional.empty() : Optional.of(selected.getUniqueId());
    }

    private static NPC npc(UUID npc) {
        NPC found = CitizensAPI.getNPCRegistry().getByUniqueId(npc);
        if (found == null) {
            throw new IllegalArgumentException("Citizens has no NPC " + npc);
        }
        return found;
    }
}
