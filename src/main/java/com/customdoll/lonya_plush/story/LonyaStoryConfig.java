package com.customdoll.lonya_plush.story;

import net.neoforged.neoforge.common.ModConfigSpec;
import java.util.Locale;

/** Server-authoritative chapter cooldown. COMMON keeps the file in the instance config folder. */
public final class LonyaStoryConfig {
    public static final ModConfigSpec SPEC;
    public static final ModConfigSpec.BooleanValue COOLDOWN_ENABLED;
    public static final ModConfigSpec.IntValue COOLDOWN_SECONDS;
    static {
        var builder = new ModConfigSpec.Builder();
        builder.comment("Lonya 章节聊天冷却（由服务端执行；修改后可重启游戏/服务器生效）").push("chat");
        COOLDOWN_ENABLED = builder.comment("是否启用每章结束后的聊天冷却。关闭后可立即开始下一章或重玩。")
                .define("cooldownEnabled", true);
        COOLDOWN_SECONDS = builder.comment("冷却时长，单位：秒。默认 1200 秒（20 分钟）；0 表示不等待。离线时间也计入冷却。")
                .defineInRange("cooldownSeconds", 1200, 0, 31_536_000);
        builder.pop(); SPEC = builder.build();
    }
    public static long remainingMillis(LonyaProgress.Progress progress, long nowMillis) {
        if (!COOLDOWN_ENABLED.get() || progress.lastChapterCompletedAtMillis <= 0) return 0;
        long duration = COOLDOWN_SECONDS.get().longValue() * 1000;
        // A clock correction must never produce a wait longer than the configured duration.
        long elapsed = nowMillis >= progress.lastChapterCompletedAtMillis
                ? nowMillis - progress.lastChapterCompletedAtMillis : 0;
        return Math.max(0, duration - elapsed);
    }
    public static String countdown(long millis) {
        long seconds = Math.max(0, (millis + 999) / 1000);
        return seconds >= 3600 ? String.format(Locale.ROOT, "%d:%02d:%02d", seconds / 3600, seconds / 60 % 60, seconds % 60)
                : String.format(Locale.ROOT, "%02d:%02d", seconds / 60, seconds % 60);
    }
    private LonyaStoryConfig() { }
}
