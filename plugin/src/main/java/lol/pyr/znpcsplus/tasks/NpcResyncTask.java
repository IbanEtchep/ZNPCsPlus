package lol.pyr.znpcsplus.tasks;

import lol.pyr.znpcsplus.npc.NpcEntryImpl;
import lol.pyr.znpcsplus.npc.NpcImpl;
import lol.pyr.znpcsplus.npc.NpcRegistryImpl;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;

/*
 * Viewable only tracks whether we think a player should see an npc, not whether their
 * client actually still renders it. If a client silently loses track of the npc's entity
 * (dropped packet, entity id collision, a rendering glitch, etc) the plugin has no way to
 * notice, so the npc stays invisible to that player forever, even though it's still
 * spawned and interactable as far as the rest of the plugin is concerned. The only thing
 * that fixes it today is a full plugin reload, since that rebuilds every npc's viewer
 * state from scratch and forces a fresh spawn packet to go out.
 *
 * This periodically does the same thing on a much smaller scale: silently re-send the
 * npc to everyone already viewing it, so any such desync heals itself without needing a
 * manual reload.
 */
public class NpcResyncTask extends BukkitRunnable {
    private final NpcRegistryImpl npcRegistry;

    public NpcResyncTask(NpcRegistryImpl npcRegistry) {
        this.npcRegistry = npcRegistry;
    }

    @Override
    public void run() {
        for (NpcEntryImpl entry : npcRegistry.getProcessable()) {
            NpcImpl npc = entry.getNpc();
            if (!npc.isEnabled()) continue;
            for (Player viewer : npc.getViewers()) npc.respawn(viewer);
        }
    }
}
