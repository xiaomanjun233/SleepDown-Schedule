package com.xiaomanjun.sleepdownschedule.feature.experimental;

/** Used only by the root command and Shizuku's shell/root process. */
final class XiaomiFirewall {
    private static final int OEM_DENY_CHAIN = 9;
    private final Object connection;
    private final Class<?> api;

    XiaomiFirewall() throws Exception {
        Object binder = Class.forName("android.os.ServiceManager").getMethod("getService", String.class)
                .invoke(null, "connectivity");
        if (binder == null) throw new IllegalStateException("Connectivity service unavailable");
        api = Class.forName("android.net.IConnectivityManager");
        connection = Class.forName("android.net.IConnectivityManager$Stub")
                .getMethod("asInterface", Class.forName("android.os.IBinder")).invoke(null, binder);
        if (connection == null) throw new IllegalStateException("Connectivity interface unavailable");
    }

    int[] readState(int uid) throws Exception {
        validateUid(uid);
        boolean enabled = (Boolean) api.getMethod("getFirewallChainEnabled", int.class)
                .invoke(connection, OEM_DENY_CHAIN);
        int rule = (Integer) api.getMethod("getUidFirewallRule", int.class, int.class)
                .invoke(connection, OEM_DENY_CHAIN, uid);
        if (rule < 0 || rule > 2) throw new IllegalStateException("Unknown firewall rule");
        return new int[]{enabled ? 1 : 0, rule};
    }

    void deny(int uid) throws Exception {
        validateUid(uid);
        setChainEnabled(true);
        setRule(uid, 2);
    }

    void restore(int uid, int rule, boolean enabled) throws Exception {
        validateUid(uid);
        if (rule < 0 || rule > 2) throw new IllegalArgumentException("Invalid saved rule");
        setRule(uid, rule);
        setChainEnabled(enabled);
    }

    private void setRule(int uid, int rule) throws Exception {
        api.getMethod("setUidFirewallRule", int.class, int.class, int.class)
                .invoke(connection, OEM_DENY_CHAIN, uid, rule);
    }

    private void setChainEnabled(boolean enabled) throws Exception {
        api.getMethod("setFirewallChainEnabled", int.class, boolean.class)
                .invoke(connection, OEM_DENY_CHAIN, enabled);
    }

    private static void validateUid(int uid) {
        if (uid < 0) throw new IllegalArgumentException("Invalid UID");
    }
}
