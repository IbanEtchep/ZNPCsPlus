package lol.pyr.znpcsplus.entity;

import com.github.retrooper.packetevents.PacketEvents;
import com.github.retrooper.packetevents.manager.server.ServerVersion;
import com.github.retrooper.packetevents.protocol.entity.type.EntityType;
import com.github.retrooper.packetevents.protocol.entity.type.EntityTypes;
import lol.pyr.znpcsplus.api.entity.EntityProperty;
import lol.pyr.znpcsplus.api.entity.PropertyHolder;
import lol.pyr.znpcsplus.packets.PacketFactory;
import lol.pyr.znpcsplus.reflection.Reflections;
import lol.pyr.znpcsplus.util.FutureUtil;
import lol.pyr.znpcsplus.util.NpcLocation;
import lol.pyr.znpcsplus.util.Viewable;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;

public class PacketEntity implements PropertyHolder {
    private final PacketFactory packetFactory;

    private final PropertyHolder properties;
    private final Viewable viewable;
    private final int entityId;
    private final UUID uuid;

    private final EntityType type;
    private NpcLocation location;

    private PacketEntity vehicle;
    private Integer vehicleId;
    private List<Integer> passengers;

    public PacketEntity(PacketFactory packetFactory, PropertyHolder properties, Viewable viewable, EntityType type, NpcLocation location) {
        this.packetFactory = packetFactory;
        this.properties = properties;
        this.viewable = viewable;
        this.entityId = reserveEntityID();
        this.uuid = UUID.randomUUID();
        this.type = type;
        this.location = location;
    }

    public int getEntityId() {
        return entityId;
    }

    public NpcLocation getLocation() {
        return location;
    }

    public UUID getUuid() {
        return uuid;
    }

    public EntityType getType() {
        return type;
    }

    public void setLocation(NpcLocation location) {
        this.location = location;
        if (vehicle != null) {
            vehicle.setLocation(location.withY(location.getY() - 0.9));
            return;
        }
        for (Player viewer : viewable.getViewers()) packetFactory.teleportEntity(viewer, this);
    }

    public CompletableFuture<Void> spawn(Player player) {
        return FutureUtil.exceptionPrintingRunAsync(() -> {
            if (type == EntityTypes.PLAYER) packetFactory.spawnPlayer(player, this, properties).join();
            else packetFactory.spawnEntity(player, this, properties);
            if (vehicle != null) {
                setVehicle(vehicle);
            }
            if (vehicleId != null) {
                packetFactory.setPassengers(player, vehicleId, this.getEntityId());
            }
            if (passengers != null) {
                packetFactory.setPassengers(player, this.getEntityId(), passengers.stream().mapToInt(Integer::intValue).toArray());
            }
        });
    }

    public void setHeadRotation(Player player, float yaw, float pitch) {
        packetFactory.sendHeadRotation(player, this, yaw, pitch);
    }

    public PacketEntity getVehicle() {
        return vehicle;
    }

    public Viewable getViewable() {
        return viewable;
    }

    public void setVehicleId(Integer vehicleId) {
        if (this.vehicle != null) {
            for (Player player : viewable.getViewers()) {
                packetFactory.setPassengers(player, this.vehicle.getEntityId());
                this.vehicle.despawn(player);
                packetFactory.teleportEntity(player, this);
            }
        } else if (this.vehicleId != null) {
            for (Player player : viewable.getViewers()) {
                packetFactory.setPassengers(player, this.vehicleId);
            }
        }
        this.vehicleId = vehicleId;
        if (vehicleId == null) return;

        for (Player player : viewable.getViewers()) {
            packetFactory.setPassengers(player, this.getEntityId(), vehicleId);
        }
    }

    public void setVehicle(PacketEntity vehicle) {
        // remove old vehicle
        if (this.vehicle != null) {
            for (Player player : viewable.getViewers()) {
                packetFactory.setPassengers(player, this.vehicle.getEntityId());
                this.vehicle.despawn(player);
                packetFactory.teleportEntity(player, this);
            }
        } else if (this.vehicleId != null) {
            for (Player player : viewable.getViewers()) {
                packetFactory.setPassengers(player, this.vehicleId);
            }
        }

        this.vehicle = vehicle;
        if (this.vehicle == null) return;

        vehicle.setLocation(location.withY(location.getY() - 0.9));
        for (Player player : viewable.getViewers()) {
            vehicle.spawn(player).thenRun(() -> {
                packetFactory.setPassengers(player, vehicle.getEntityId(), this.getEntityId());
            });
        }
    }

    public Integer getVehicleId() {
        return vehicleId;
    }

    public List<Integer> getPassengers() {
        return passengers == null ? Collections.emptyList() : passengers;
    }

    public void addPassenger(int entityId) {
        if (passengers == null) {
            passengers = new ArrayList<>();
        }
        passengers.add(entityId);
        for (Player player : viewable.getViewers()) {
            packetFactory.setPassengers(player, this.getEntityId(), passengers.stream().mapToInt(Integer::intValue).toArray());
        }
    }

    public void removePassenger(int entityId) {
        if (passengers == null) return;
        passengers.remove(entityId);
        for (Player player : viewable.getViewers()) {
            packetFactory.setPassengers(player, this.getEntityId(), passengers.stream().mapToInt(Integer::intValue).toArray());
        }
        if (passengers.isEmpty()) {
            passengers = null;
        }
    }

    public void despawn(Player player) {
        packetFactory.destroyEntity(player, this, properties);
        if (vehicle != null) vehicle.despawn(player);
    }

    public void refreshMeta(Player player) {
        packetFactory.sendAllMetadata(player, this, properties);
    }

    public void swingHand(Player player, boolean offhand) {
        packetFactory.sendHandSwing(player, this, offhand);
    }

    /*
     * On 26.2+ the vanilla entity id counter was moved out of Entity and its exact
     * location can no longer be reliably found by reflection. Guessing at a field via
     * reflection here is dangerous: if the lookup silently matches the wrong field, NPCs
     * get handed ids that eventually collide with real entities, which destroys the NPC
     * on the client without the plugin ever knowing (it still renders fine server-side,
     * hence /npc list and teleport still working while the NPC is invisible in-world).
     * Instead we hand out ids from the top of the int range, far above anything vanilla's
     * own counter will reach in a server's uptime, so collisions can't happen regardless
     * of how the server internals are laid out on any given version.
     */
    private static final AtomicInteger SAFE_ENTITY_ID_COUNTER = new AtomicInteger(Integer.MAX_VALUE / 2);

    private static int reserveEntityID() {
        if (PacketEvents.getAPI().getServerManager().getVersion().isNewerThanOrEquals(ServerVersion.V_26_2)) {
            return SAFE_ENTITY_ID_COUNTER.incrementAndGet();
        } else if (PacketEvents.getAPI().getServerManager().getVersion().isNewerThanOrEquals(ServerVersion.V_1_14)) {
            return Reflections.ATOMIC_ENTITY_ID_FIELD.get().incrementAndGet();
        } else {
            int id = Reflections.ENTITY_ID_MODIFIER.get();
            Reflections.ENTITY_ID_MODIFIER.set(id + 1);
            return id;
        }
    }

    @Override
    public <T> T getProperty(EntityProperty<T> key) {
        return properties.getProperty(key);
    }

    @Override
    public boolean hasProperty(EntityProperty<?> key) {
        return properties.hasProperty(key);
    }

    @Override
    public <T> void setProperty(EntityProperty<T> key, T value) {
        properties.setProperty(key, value);
    }

    @Override
    public void setItemProperty(EntityProperty<?> key, ItemStack value) {
        properties.setItemProperty(key, value);
    }

    @Override
    public ItemStack getItemProperty(EntityProperty<?> key) {
        return properties.getItemProperty(key);
    }

    public PropertyHolder getProperties() {
        return properties;
    }

    @Override
    public Set<EntityProperty<?>> getAppliedProperties() {
        return properties.getAppliedProperties();
    }
}
