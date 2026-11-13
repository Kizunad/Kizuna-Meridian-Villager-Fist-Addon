package dev.kizuna.meridian.villager.fabric;

import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import dev.kizuna.meridian.fabric.MeridianCommands;
import dev.kizuna.meridian.technique.fabric.TechniqueFabric;
import dev.kizuna.meridian.technique.protocol.pb.TechniqueMessages.BlockWork;
import dev.kizuna.meridian.technique.protocol.pb.TechniqueMessages.TechniqueEffect;
import java.util.Optional;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.LivingEntity;

/** /meridian debug villager_fist target：看执行者现在对着哪根桩，和打木桩起式时的认法完全一样。 */
final class VillagerFistCommands {
    private static final String NODE = "villager_fist";
    private static final String DRILL_ID = "meridian_villager:dummy_drill";

    private VillagerFistCommands() {
        // 指令登记只在模组初始化时做一次。
    }

    static void register() {
        // 调试指令要 2 级权限；字面量都能 Tab 补全。
        MeridianCommands.register(MeridianCommands.Branch.DEBUG, NODE, MeridianCommands.GAMEMASTER,
                (node, context) -> node.then(Commands.literal("target").executes(VillagerFistCommands::target)));
    }

    private static int target(CommandContext<CommandSourceStack> command) throws CommandSyntaxException {
        // 距离和方块标签取正式目录里打木桩的声明，不另写一份。
        CommandSourceStack source = command.getSource();
        if (!(source.getEntityOrException() instanceof LivingEntity caster)) {
            source.sendFailure(Component.translatable("commands.meridian_villager.target.not_living"));
            return 0;
        }
        BlockWork work = drillWork();
        Optional<DrillTarget> target = DrillTargets.find(caster, work.getMaximumDistance().getValue(),
                DrillTargets.blockTags(work.getBlockTagsList()));
        if (target.isEmpty()) {
            source.sendFailure(Component.translatable("commands.meridian_villager.target.none"));
            return 0;
        }
        source.sendSuccess(() -> target.get().describe(), false /* broadcastToOps */);
        return 1;
    }

    private static BlockWork drillWork() {
        // 打木桩恰好声明一个木桩效果，目录校验保证它存在。
        var definition = TechniqueFabric.service().registry().findLatest(DRILL_ID).orElseThrow();
        return definition.getEffectsList().stream()
                .filter(TechniqueEffect::hasBlockWork)
                .map(TechniqueEffect::getBlockWork)
                .findFirst()
                .orElseThrow();
    }
}
