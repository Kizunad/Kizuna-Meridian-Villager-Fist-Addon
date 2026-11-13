package dev.kizuna.meridian.villager.fabric;

import java.util.UUID;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;

/** 按 UUID 在当前服务器的所有维度里找活体；处理器只拿到施放者 UUID，要靠这里取回实体。 */
final class ServerEntities {
    private static MinecraftServer server;

    private ServerEntities() {
        // 只记当前服务器。
    }

    static void register() {
        // 服务器起停时更新引用，停服后一律找不到。
        ServerLifecycleEvents.SERVER_STARTED.register(started -> server = started);
        ServerLifecycleEvents.SERVER_STOPPED.register(stopped -> server = null);
    }

    static LivingEntity living(UUID entityUuid) {
        // 施放者、被擒的都可能是玩家或任何维度里的生物；找不到返回 null。
        if (server == null) {
            return null;
        }
        for (ServerLevel level : server.getAllLevels()) {
            Entity entity = level.getEntity(entityUuid);
            if (entity instanceof LivingEntity living) {
                return living;
            }
        }
        return null;
    }
}
