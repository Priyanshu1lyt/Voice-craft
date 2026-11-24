package com.nebrionverse.voicecraft;

import com.nebrionverse.voicecraft.CommandInterpreter.Plan;
import java.util.Set;

public final class ConfirmationManager {
    private static volatile Plan pending;
    private static final Set<String> YES = Set.of("yes", "yeah", "yep", "confirm", "execute", "ok", "okay", "sure", "haan");
    private static final Set<String> NO = Set.of("no", "nope", "cancel", "stop", "abort", "nevermind", "nahi");
    public static void set(Plan p) { pending = p; }
    public static boolean has() { return pending != null; }
    public static Plan take() { Plan p = pending; pending = null; return p; }
    public static void clear() { pending = null; }
    public static boolean isYes(String n) { return YES.contains(n) || n.equals("do it") || n.equals("go ahead"); }
    public static boolean isNo(String n) { return NO.contains(n) || n.equals("never mind"); }
}
