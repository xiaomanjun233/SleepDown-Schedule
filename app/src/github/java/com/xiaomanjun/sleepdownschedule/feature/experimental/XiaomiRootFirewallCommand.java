package com.xiaomanjun.sleepdownschedule.feature.experimental;

/** Runs in a short-lived root app_process so Binder sees the privileged UID. */
public final class XiaomiRootFirewallCommand {
    private XiaomiRootFirewallCommand() { }

    public static void main(String[] args) {
        try {
            if (args.length < 2) throw new IllegalArgumentException("Missing command or UID");
            final int uid = Integer.parseInt(args[1]);
            if (uid < 0) throw new IllegalArgumentException("Invalid UID");

            XiaomiFirewall firewall = new XiaomiFirewall();
            switch (args[0]) {
                case "state":
                    int[] state = firewall.readState(uid);
                    System.out.println("STATE " + state[0] + " " + state[1]);
                    break;
                case "deny":
                    firewall.deny(uid);
                    System.out.println("OK");
                    break;
                case "restore":
                    if (args.length != 4) throw new IllegalArgumentException("Missing saved firewall state");
                    boolean oldEnabled = "1".equals(args[2]);
                    int oldRule = Integer.parseInt(args[3]);
                    if (oldRule < 0 || oldRule > 2) throw new IllegalArgumentException("Invalid saved rule");
                    firewall.restore(uid, oldRule, oldEnabled);
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
