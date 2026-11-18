package dev.kizuna.meridian.humanoid.fabric;

import com.google.gson.JsonObject;
import dev.kizuna.meridian.humanoid.server.MeleeGuardRules;
import dev.kizuna.meridian.technique.protocol.pb.TechniqueMessages.TechniqueDefinition;
import dev.kizuna.meridian.technique.server.TechniqueSelector;
import net.minecraft.world.entity.Mob;

/** 在服务端线程上记录当前场地拳师这一轮真实决策中经过的门槛。 */
public final class FistDecisionTrace {
    private static JsonObject row;
    private static JsonObject candidates;
    private static boolean reading;

    public enum Gate { PASSED, REJECTED }

    private FistDecisionTrace() { }

    public static void begin(Mob mob, String source) {
        row = FistXbowMeasureGameTest.decisionRow(mob);
        if (row != null) {
            row.addProperty("trigger", source);
            candidates = new JsonObject();
            row.add("candidates", candidates);
            reading = true;
        }
    }

    public static String scheduled(Mob mob, Object state) {
        HumanoidMind mind = (HumanoidMind) state;
        if (mind.contactPending()) {
            return "contact";
        }
        int interval = HumanoidBrain.config().schedule().decisionIntervalTicks();
        return Math.floorMod((long) mob.tickCount + mob.getId(), interval) == 0 ? "interval" : "stagger_end";
    }

    public static void payable(TechniqueDefinition definition, Gate gate) {
        if (reading) {
            candidate(definition.getId()).addProperty("payable", gate == Gate.PASSED);
        }
    }

    public static void castable(TechniqueSelector.Context context, TechniqueDefinition definition, Gate gate) {
        if (reading) {
            var result = candidate(definition.getId());
            result.addProperty("castable", gate == Gate.PASSED);
            result.addProperty("cooldown_left", Math.max(0, context.availability().cooldownEnds()
                    .getOrDefault(definition.getId(), 0L) - context.situation().nowMillis()));
        }
    }

    public static void prediction(String id, MeleeGuardRules.Motion motion, MeleeGuardRules.Strike strike,
                                  Gate gate) {
        if (reading) {
            var result = candidate(id);
            result.addProperty("prediction", gate == Gate.PASSED);
            result.addProperty("motion", motion.toString());
            result.addProperty("strike", strike.toString());
        }
    }

    public static void readout(Object value) {
        if (!reading) {
            return;
        }
        var readout = (HumanoidRepertoires.Readout) value;
        double distance = readout.context().situation().targetDistanceMeters();
        row.addProperty("decision_distance", distance);
        for (var known : readout.known()) {
            candidate(known.getTechnique().getId());
        }
        for (var strike : readout.repertoire().strikes()) {
            var result = candidate(strike.technique());
            result.addProperty("start_reach", strike.reach());
            result.addProperty("within_start", distance <= strike.reach());
        }
        reading = false;
    }

    private static JsonObject candidate(String id) {
        if (!candidates.has(id)) {
            candidates.add(id, new JsonObject());
        }
        return candidates.getAsJsonObject(id);
    }

    public static void end(Mob mob) {
        if (row != null) {
            FistXbowMeasureGameTest.finishDecision(mob, row);
        }
        row = null;
        candidates = null;
        reading = false;
    }
}
