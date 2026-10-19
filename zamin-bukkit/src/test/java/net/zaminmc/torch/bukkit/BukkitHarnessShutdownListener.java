package net.zaminmc.torch.bukkit;

import org.junit.platform.launcher.TestExecutionListener;
import org.junit.platform.launcher.TestPlan;

/**
 * The end-of-run walk: the shared harness's engine shutdown after the LAST
 * test in the JVM (the listener runs once; the hook is the belt-and-braces
 * twin — both idempotent).
 */
public final class BukkitHarnessShutdownListener implements TestExecutionListener {

    @Override
    public void testPlanExecutionFinished(TestPlan testPlan) {
        BukkitTestHarness.shutdownNow();
    }
}
