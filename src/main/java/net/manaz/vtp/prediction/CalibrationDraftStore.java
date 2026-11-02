package net.manaz.vtp.prediction;

import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

/** An unfinished form belongs to one session, villager and tier, and expires after a minute away. */
public final class CalibrationDraftStore {
    public record Draft(String enchantment, int level, String price) {}
    static final long LIFETIME_NANOS = TimeUnit.SECONDS.toNanos(60);
    private static Draft draft;
    private static String binding;
    private static long savedAt;

    private CalibrationDraftStore() {}
    public static void clear() { draft = null; binding = null; }
    public static void save(String scope, Draft value) { save(scope, value, System.nanoTime()); }
    public static Optional<Draft> get(String scope) { return get(scope, System.nanoTime()); }

    static void save(String scope, Draft value, long now) {
        binding = scope; draft = value; savedAt = now;
    }
    static Optional<Draft> get(String scope, long now) {
        if (!Objects.equals(scope, binding) || now - savedAt >= LIFETIME_NANOS) clear();
        return Optional.ofNullable(draft);
    }
}
