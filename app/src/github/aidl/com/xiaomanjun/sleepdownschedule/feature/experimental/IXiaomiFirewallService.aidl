package com.xiaomanjun.sleepdownschedule.feature.experimental;

interface IXiaomiFirewallService {
    void destroy() = 16777114;
    int[] readState(int uid) = 1;
    void deny(int uid) = 2;
    void restore(int uid, int rule, boolean chainEnabled) = 3;
}
