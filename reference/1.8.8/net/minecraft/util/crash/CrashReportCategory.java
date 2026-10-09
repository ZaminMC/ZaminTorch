package net.minecraft.util.crash;

import com.google.common.collect.Lists;
import java.util.List;
import java.util.concurrent.Callable;
import net.minecraft.block.Block;
import net.minecraft.block.state.BlockState;
import net.minecraft.util.math.BlockPos;

public class CrashReportCategory {
    private final CrashReport report;
    private final String title;
    private final List<CrashReportCategory.Entry> entries = Lists.newArrayList();
    private StackTraceElement[] stackTrace = new StackTraceElement[0];

    public CrashReportCategory(CrashReport report, String title) {
        this.report = report;
        this.title = title;
    }

    public static String formatPosition(double x, double y, double z) {
        return String.format("%.2f,%.2f,%.2f - %s", x, y, z, formatPosition(new BlockPos(x, y, z)));
    }

    public static String formatPosition(BlockPos pos) {
        int i = pos.getX();
        int j = pos.getY();
        int k = pos.getZ();
        StringBuilder stringbuilder = new StringBuilder();

        try {
            stringbuilder.append(String.format("World: (%d,%d,%d)", i, j, k));
        } catch (Throwable throwable2) {
            stringbuilder.append("(Error finding world loc)");
        }

        stringbuilder.append(", ");

        try {
            int l = i >> 4;
            int i1 = k >> 4;
            int j1 = i & 15;
            int k1 = j >> 4;
            int l1 = k & 15;
            int i2 = l << 4;
            int j2 = i1 << 4;
            int k2 = (l + 1 << 4) - 1;
            int l2 = (i1 + 1 << 4) - 1;
            stringbuilder.append(String.format("Chunk: (at %d,%d,%d in %d,%d; contains blocks %d,0,%d to %d,255,%d)", j1, k1, l1, l, i1, i2, j2, k2, l2));
        } catch (Throwable throwable1) {
            stringbuilder.append("(Error finding chunk loc)");
        }

        stringbuilder.append(", ");

        try {
            int j3 = i >> 9;
            int k3 = k >> 9;
            int l3 = j3 << 5;
            int i4 = k3 << 5;
            int j4 = (j3 + 1 << 5) - 1;
            int k4 = (k3 + 1 << 5) - 1;
            int l4 = j3 << 9;
            int i5 = k3 << 9;
            int j5 = (j3 + 1 << 9) - 1;
            int i3 = (k3 + 1 << 9) - 1;
            stringbuilder.append(
                String.format("Region: (%d,%d; contains chunks %d,%d to %d,%d, blocks %d,0,%d to %d,255,%d)", j3, k3, l3, i4, j4, k4, l4, i5, j5, i3)
            );
        } catch (Throwable throwable) {
            stringbuilder.append("(Error finding world loc)");
        }

        return stringbuilder.toString();
    }

    public void add(String key, Callable<String> value) {
        try {
            this.add(key, value.call());
        } catch (Throwable throwable) {
            this.add(key, throwable);
        }
    }

    public void add(String key, Object value) {
        this.entries.add(new CrashReportCategory.Entry(key, value));
    }

    public void add(String key, Throwable value) {
        this.add(key, (Object)value);
    }

    public int getStackTrace(int ignoredCallCount) {
        StackTraceElement[] astacktraceelement = Thread.currentThread().getStackTrace();
        if (astacktraceelement.length <= 0) {
            return 0;
        }

        this.stackTrace = new StackTraceElement[astacktraceelement.length - 3 - ignoredCallCount];
        System.arraycopy(astacktraceelement, 3 + ignoredCallCount, this.stackTrace, 0, this.stackTrace.length);
        return this.stackTrace.length;
    }

    public boolean validateStackTrace(StackTraceElement lastIncludedElement, StackTraceElement firstIgnoredElement) {
        if (this.stackTrace.length != 0 && lastIncludedElement != null) {
            StackTraceElement stacktraceelement = this.stackTrace[0];
            if (stacktraceelement.isNativeMethod() == lastIncludedElement.isNativeMethod()
                && stacktraceelement.getClassName().equals(lastIncludedElement.getClassName())
                && stacktraceelement.getFileName().equals(lastIncludedElement.getFileName())
                && stacktraceelement.getMethodName().equals(lastIncludedElement.getMethodName())) {
                if (firstIgnoredElement != null != this.stackTrace.length > 1) {
                    return false;
                }

                if (firstIgnoredElement != null && !this.stackTrace[1].equals(firstIgnoredElement)) {
                    return false;
                }

                this.stackTrace[0] = lastIncludedElement;
                return true;
            } else {
                return false;
            }
        } else {
            return false;
        }
    }

    public void trimStackTrace(int amount) {
        StackTraceElement[] astacktraceelement = new StackTraceElement[this.stackTrace.length - amount];
        System.arraycopy(this.stackTrace, 0, astacktraceelement, 0, astacktraceelement.length);
        this.stackTrace = astacktraceelement;
    }

    public void addDetails(StringBuilder sb) {
        sb.append("-- ").append(this.title).append(" --\n");
        sb.append("Details:");

        for (CrashReportCategory.Entry crashreportcategory$entry : this.entries) {
            sb.append("\n\t");
            sb.append(crashreportcategory$entry.getKey());
            sb.append(": ");
            sb.append(crashreportcategory$entry.getValue());
        }

        if (this.stackTrace != null && this.stackTrace.length > 0) {
            sb.append("\nStacktrace:");

            for (StackTraceElement stacktraceelement : this.stackTrace) {
                sb.append("\n\tat ");
                sb.append(stacktraceelement.toString());
            }
        }
    }

    public StackTraceElement[] getStackTrace() {
        return this.stackTrace;
    }

    public static void addBlockDetails(CrashReportCategory category, BlockPos pos, Block block, int metadata) {
        final int i = Block.getId(block);
        category.add("Block type", new Callable<String>() {
            public String call() throws Exception {
                try {
                    return String.format("ID #%d (%s // %s)", i, block.getTranslationKey(), block.getClass().getCanonicalName());
                } catch (Throwable throwable) {
                    return "ID #" + i;
                }
            }
        });
        category.add("Block data value", new Callable<String>() {
            public String call() throws Exception {
                if (metadata < 0) {
                    return "Unknown? (Got " + metadata + ")";
                }

                String s = String.format("%4s", Integer.toBinaryString(metadata)).replace(" ", "0");
                return String.format("%1$d / 0x%1$X / 0b%2$s", metadata, s);
            }
        });
        category.add("Block location", new Callable<String>() {
            public String call() throws Exception {
                return CrashReportCategory.formatPosition(pos);
            }
        });
    }

    public static void addBlockDetails(CrashReportCategory category, BlockPos pos, BlockState state) {
        category.add("Block", new Callable<String>() {
            public String call() throws Exception {
                return state.toString();
            }
        });
        category.add("Block location", new Callable<String>() {
            public String call() throws Exception {
                return CrashReportCategory.formatPosition(pos);
            }
        });
    }

    static class Entry {
        private final String key;
        private final String value;

        public Entry(String key, Object value) {
            this.key = key;
            if (value == null) {
                this.value = "~~NULL~~";
            } else if (value instanceof Throwable) {
                Throwable throwable = (Throwable)value;
                this.value = "~~ERROR~~ " + throwable.getClass().getSimpleName() + ": " + throwable.getMessage();
            } else {
                this.value = value.toString();
            }
        }

        public String getKey() {
            return this.key;
        }

        public String getValue() {
            return this.value;
        }
    }
}
