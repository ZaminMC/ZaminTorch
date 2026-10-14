package net.zaminmc.torch.launcher;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/**
 * The dual-ledger ordering bridge of the torch-1.8.8-N era: release tags
 * ({@code torch-1.8.8-N}) against the running build's constant
 * ({@code 1.8.8-N}), with the legacy {@code vX.Y.Z-dev.N} tags still
 * readable so any torch build orders above every legacy one. Everything
 * else must degrade to a conservative "not newer".
 */
final class UpdateCheckerTest {

    @Test
    void theTagPrefixAloneIsNotANewerBuild() {
        assertFalse(UpdateChecker.isNewer("torch-1.8.8-26", "1.8.8-26"));
    }

    @Test
    void aHigherBuildNumberIsNewer() {
        assertTrue(UpdateChecker.isNewer("torch-1.8.8-27", "1.8.8-26"));
        assertTrue(UpdateChecker.isNewer("1.8.8-27", "1.8.8-26"));
    }

    @Test
    void aLowerBuildNumberIsNotNewer() {
        assertFalse(UpdateChecker.isNewer("torch-1.8.8-25", "1.8.8-26"));
    }

    @Test
    void buildNumbersCompareNumericallyNotLexicographically() {
        assertTrue(UpdateChecker.isNewer("torch-1.8.8-10", "1.8.8-9"));
        assertFalse(UpdateChecker.isNewer("torch-1.8.8-9", "1.8.8-10"));
    }

    @Test
    void torchBuildsOrderAboveTheWholeLegacyLedger() {
        assertTrue(UpdateChecker.isNewer("torch-1.8.8-1", "v0.2.0-dev.99"));
    }

    @Test
    void legacyTagsNeverNagATorchBuild() {
        assertFalse(UpdateChecker.isNewer("v0.2.0-dev.99", "1.8.8-1"));
        assertFalse(UpdateChecker.isNewer("v0.2.0-dev.26", "1.8.8-26"));
    }

    @Test
    void theLegacyLedgerStillOrdersInternally() {
        assertTrue(UpdateChecker.isNewer("v0.2.0-dev.25", "v0.2.0-dev.24"));
        assertFalse(UpdateChecker.isNewer("v0.2.0-dev.24", "v0.2.0-dev.25"));
        assertFalse(UpdateChecker.isNewer("v0.2.0-dev.25", "v0.2.0-dev.25"));
    }

    @Test
    void unrecognizedInputStaysSilent() {
        assertFalse(UpdateChecker.isNewer("latest", "1.8.8-26"));
        assertFalse(UpdateChecker.isNewer("", "1.8.8-26"));
        assertFalse(UpdateChecker.isNewer("torch-1.8.8-dev", "1.8.8-26"));
        assertFalse(UpdateChecker.isNewer("1.8.8", "1.8.8-26"));
        assertFalse(UpdateChecker.isNewer("v0.2.0", "1.8.8-26"));
    }

    @Test
    void nullAndBlankNeverNag() {
        assertFalse(UpdateChecker.isNewer(null, "1.8.8-26"));
        assertFalse(UpdateChecker.isNewer("torch-1.8.8-27", null));
        assertFalse(UpdateChecker.isNewer("   ", "1.8.8-26"));
        assertFalse(UpdateChecker.isNewer("torch-1.8.8-27", "  "));
    }
}
