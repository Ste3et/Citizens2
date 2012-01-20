package net.citizensnpcs;

import java.io.File;

import net.citizensnpcs.api.CitizensAPI;
import net.citizensnpcs.api.DataKey;
import net.citizensnpcs.api.exception.NPCLoadException;
import net.citizensnpcs.api.npc.NPC;
import net.citizensnpcs.api.npc.trait.Character;
import net.citizensnpcs.api.npc.trait.Trait;
import net.citizensnpcs.api.npc.trait.trait.LocationTrait;
import net.citizensnpcs.npc.CitizensNPCManager;
import net.citizensnpcs.npc.trait.CitizensCharacterManager;
import net.citizensnpcs.npc.trait.CitizensTraitManager;
import net.citizensnpcs.storage.Storage;
import net.citizensnpcs.storage.flatfile.YamlStorage;
import net.citizensnpcs.util.Messaging;

import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

public class Citizens extends JavaPlugin {
    private CitizensNPCManager npcManager;
    private CitizensCharacterManager characterManager;
    private CitizensTraitManager traitManager;
    private Storage saves;

    @Override
    public void onDisable() {
        Messaging.log("v" + getDescription().getVersion() + " disabled.");
    }

    @Override
    public void onEnable() {
        // Register API managers
        npcManager = new CitizensNPCManager();
        characterManager = new CitizensCharacterManager();
        traitManager = new CitizensTraitManager();
        CitizensAPI.setNPCManager(npcManager);
        CitizensAPI.setCharacterManager(characterManager);
        CitizensAPI.setTraitManager(traitManager);

        // TODO database support
        saves = new YamlStorage(getDataFolder() + File.separator + "saves.yml");

        // Register events
        new EventListen(this);

        Messaging.log("v" + getDescription().getVersion() + " enabled.");

        // Setup NPCs after all plugins have been enabled (allows for multiworld
        // support and for NPCs to properly register external settings)
        if (Bukkit.getScheduler().scheduleSyncDelayedTask(this, new Runnable() {
            @Override
            public void run() {
                try {
                    setupNPCs();
                } catch (NPCLoadException ex) {
                    Messaging.log("Failed to create NPC: " + ex.getMessage());
                }
            }
        }) == -1) {
            Messaging.log("Issue enabling plugin. Disabling.");
            getServer().getPluginManager().disablePlugin(this);
        }
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String cmdName, String[] args) {
        if (args[0].equals("spawn")) {
            NPC npc = npcManager.createNPC("aPunch");
            npc.spawn(((Player) sender).getLocation());
        } else if (args[0].equals("despawn")) {
            for (NPC npc : npcManager.getNPCs()) {
                npc.despawn();
            }
        }
        return true;
    }

    private void setupNPCs() throws NPCLoadException {
        traitManager.registerTrait(LocationTrait.class);
        for (DataKey key : saves.getKey("npc").getIntegerSubKeys()) {
            int id = Integer.parseInt(key.name());
            if (!key.keyExists("name"))
                throw new NPCLoadException("Could not find a name for the NPC with ID '" + id + "'.");
            Character character = characterManager.getCharacter(key.getString("character"));
            NPC npc = npcManager.createNPC(key.getString("name"), character);

            // Load the character if it exists
            if (character != null) {
                character.load(key);
            }

            // Load traits
            for (DataKey traitKey : key.getSubKeys()) {
                for (Trait trait : traitManager.getRegisteredTraits()) {
                    if (trait.getName().equals(traitKey.name())) {
                        Messaging.debug("Found trait '" + trait.getName() + "' in the NPC with ID '" + id + "'.");
                        npc.addTrait(trait.getClass());
                    }
                }
            }
            for (Trait trait : npc.getTraits()) {
                trait.load(key);
            }
            // Spawn the NPC
            npc.spawn(npc.getTrait(LocationTrait.class).getLocation());
        }
        Messaging.log("Loaded " + npcManager.getNPCs().size() + " NPCs.");
    }
}