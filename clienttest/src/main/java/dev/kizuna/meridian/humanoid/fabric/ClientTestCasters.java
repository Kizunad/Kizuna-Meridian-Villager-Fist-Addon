package dev.kizuna.meridian.humanoid.fabric;

import net.minecraft.world.entity.Mob;

/** 连拍替生人起式时要的身体身份：先把它的功法宿主备好（和大脑自己起式前做的一样），再读身体 ID。 */
public final class ClientTestCasters {
    private ClientTestCasters() {
        // 纯静态入口。
    }

    public static String bodyId(Mob mob) {
        return HumanoidTechniqueHost.prepare(mob).body().snapshot().getId();
    }
}
