package dev.kizuna.meridian.villager.fabric;

import dev.kizuna.meridian.technique.fabric.TechniqueFabric;
import dev.kizuna.meridian.technique.protocol.pb.TechniqueMessages.EffectIntent;
import dev.kizuna.meridian.technique.server.TechniqueService.StrikeOutcome;
import dev.kizuna.meridian.technique.server.TechniqueService.StrikeReport;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.entity.LivingEntity;

/**
 * 擒摔里的一下打击（落地、一靠）：书级空手伤害 × 本击倍率 × 双手 1.2，走原版受伤，
 * 部位由伤害类型的 {@code aim/torso} 标签定；真正受伤时向功法库回报打中。
 */
record GrappleStrike(EffectIntent intent, String damageTypeId, double dose) {
    /** 擒和摔都是双手，和拳掌执行器的双手倍数一致。 */
    static final double BOTH_ARMS_FACTOR = 1.2;

    static GrappleStrike of(EffectIntent intent, String damageTypeId, double singleHandDose) {
        // 剂量在排程时算好，落地时不再读书级规则。
        return new GrappleStrike(intent, damageTypeId, singleHandDose * BOTH_ARMS_FACTOR);
    }

    void hit(LivingEntity caster, LivingEntity target) {
        // 打一下；只有真正受伤才报打中，供武馆练功计数。
        boolean damaged = target.hurtServer((ServerLevel) target.level(), source(caster), (float) dose);
        if (damaged && intent.hasTechnique()) {
            TechniqueFabric.service().reportStrike(new StrikeReport(intent.getCastId(), intent.getTechnique(),
                    caster.getUUID(), target.getUUID(), StrikeOutcome.HIT));
        }
    }

    void push(LivingEntity caster, LivingEntity target, double strength) {
        // 沿施放者朝向推开，带上这一下的伤害来源；击退抗性照原版生效。
        float yaw = caster.getYRot() * Mth.DEG_TO_RAD;
        target.knockback(strength, Mth.sin(yaw), -Mth.cos(yaw), source(caster), (float) dose);
        target.hurtMarked = true;
    }

    private DamageSource source(LivingEntity caster) {
        // 目录给完整伤害类型 ID，直接查注册表。
        ResourceKey<DamageType> key = ResourceKey.create(Registries.DAMAGE_TYPE, Identifier.parse(damageTypeId));
        Holder<DamageType> type = caster.level().registryAccess().lookupOrThrow(Registries.DAMAGE_TYPE)
                .getOrThrow(key);
        return new DamageSource(type, caster);
    }
}
