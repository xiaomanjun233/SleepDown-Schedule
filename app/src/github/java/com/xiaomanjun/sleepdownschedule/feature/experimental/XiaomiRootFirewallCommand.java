package com.xiaomanjun.sleepdownschedule.feature.experimental;

import java.lang.reflect.Method;

/** Runs in a short-lived root app_process so Binder sees the privileged UID. */
public final class XiaomiRootFirewallCommand {
    private static final int OEM_DENY_CHAIN = 9;

    private XiaomiRootFirewallCommand() { }

    public static void main(String[] args) {
        try {
            if (args.length < 2) throw new IllegalArgumentException("Missing command or UID");
            final int uid = Integer.parseInt(args[1]);
            if (uid < 0) throw new IllegalArgumentException("Invalid UID");

            Class<?> serviceManager = Class.forName("android.os.ServiceManager");
            Object binder = serviceManager.getMethod("getService", String.class)
                    .invoke(null, "connectivity");
            if (binder == null) throw new IllegalStateException("Connectivity service unavailable");
            Object manager = Class.forName("android.net.IConnectivityManager$Stub")
                    .getMethod("asInterface", Class.forName("android.os.IBinder"))
                    .invoke(null, binder);
            if (manager == null) throw new IllegalStateException("Connectivity interface unavailable");

            Method setChain = manager.getClass().getMethod("setFirewallChainEnabled", int.class, boolean.class);
            Method setRule = manager.getClass().getMethod("setUidFirewallRule", int.class, int.class, int.class);
            switch (args[0]) {
                case "state":
                    boolean enabled = (Boolean) manager.getClass()
                            .getMethod("getFirewallChainEnabled", int.class)
                            .invoke(manager, OEM_DENY_CHAIN);
                    int rule = (Integer) manager.getClass()
                            .getMethod("getUidFirewallRule", int.class, int.class)
                            .invoke(manager, OEM_DENY_CHAIN, uid);
                    System.out.println("STATE " + (enabled ? 1 : 0) + " " + rule);
                    break;
                case "deny":
                    setChain.invoke(manager, OEM_DENY_CHAIN, true);
                    setRule.invoke(manager, OEM_DENY_CHAIN, uid, 2);
                    System.out.println("OK");
                    break;
                case "restore":
                    if (args.length != 4) throw new IllegalArgumentException("Missing saved firewall state");
                    boolean oldEnabled = "1".equals(args[2]);
                    int oldRule = Integer.parseInt(args[3]);
                    if (oldRule < 0 || oldRule > 2) throw new IllegalArgumentException("Invalid saved rule");
                    setRule.invoke(manager, OEM_DENY_CHAIN, uid, oldRule);
                    setChain.invoke(manager, OEM_DENY_CHAIN, oldEnabled);
                    System.out.println("OK");
                    break;
                default:
                    throw new IllegalArgumentException("Unknown command");
            }
        } catch (Throwable error) {
            System.err.println("ERROR " + error.getClass().getSimpleName());
            System.exit(1);
        }
    }
}
