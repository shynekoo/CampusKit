// SPDX-License-Identifier: MPL-2.0
package io.qzxy.lite.core;

/** A recent idle answer permits one start; every new query invalidates the old answer. */
public final class FieldStartGate {
    public static final long MAX_IDLE_AGE_MILLIS = 15000L;
    private long idleAt = -1L;
    private boolean consumed;

    public void invalidate() { idleAt = -1L; }
    public void observed(WaterOrder order, long now) {
        idleAt = order != null && order.status() == WaterOrder.Status.NONE ? now : -1L;
    }
    public boolean permits(long now) {
        return !consumed && idleAt >= 0 && now >= idleAt && now - idleAt < MAX_IDLE_AGE_MILLIS;
    }
    public void consume(long now) {
        if (!permits(now)) throw new IllegalStateException("请重新查询设备空闲状态");
        consumed = true; invalidate();
    }
}
