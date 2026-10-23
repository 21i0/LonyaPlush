package com.customdoll.lonya_plush.story;

import net.neoforged.neoforge.common.ModConfigSpec;
import java.util.Locale;

/** Chapter-entry cooldown and independent casual-chat affection reward interval. */
public final class LonyaStoryConfig {
    public static final ModConfigSpec SPEC;
    public static final ModConfigSpec.BooleanValue COOLDOWN_ENABLED;
    public static final ModConfigSpec.IntValue COOLDOWN_SECONDS;
    static {
        var builder = new ModConfigSpec.Builder();
        builder.comment("Lonya 章节拜访冷却（由服务端执行；日常闲聊不受此配置限制）").push("chat");
        COOLDOWN_ENABLED = builder.comment("是否启用章节拜访冷却。关闭后可立即再次拜访或重玩；不影响闲聊的一分钟好感度奖励间隔。")
                .define("cooldownEnabled", true);
        COOLDOWN_SECONDS = builder.comment("冷却时长，单位：秒。默认 1200 秒（20 分钟）；0 表示不等待。离线时间也计入冷却。")
                .defineInRange("cooldownSeconds", 1200, 0, 31_536_000);
        builder.pop(); SPEC = builder.build();
    }
    public static long remainingMillis(LonyaProgress.Progress progress, long nowMillis) {
        return remainingMillis(progress.lastChapterCompletedAtMillis, nowMillis);
    }
    public static long dailyRemainingMillis(LonyaProgress.Progress progress, long nowMillis) {
        long last=progress.lastDailyChatAtMillis;
        return last<=0?0:Math.max(0,60_000-Math.max(0,nowMillis-last));
    }
    private static long remainingMillis(long completedAt, long nowMillis) {
        if (!COOLDOWN_ENABLED.get() || completedAt <= 0) return 0;
        long duration = COOLDOWN_SECONDS.get().longValue() * 1000;
        // A clock correction must never produce a wait longer than the configured duration.
        long elapsed = nowMillis >= completedAt ? nowMillis - completedAt : 0;
        return Math.max(0, duration - elapsed);
    }
    public static String countdown(long millis) {
        long seconds = Math.max(0, (millis + 999) / 1000);
        return seconds >= 3600 ? String.format(Locale.ROOT, "%d:%02d:%02d", seconds / 3600, seconds / 60 % 60, seconds % 60)
                : String.format(Locale.ROOT, "%02d:%02d", seconds / 60, seconds % 60);
    }
    private LonyaStoryConfig() { }
}
