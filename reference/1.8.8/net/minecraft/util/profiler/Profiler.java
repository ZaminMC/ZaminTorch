package net.minecraft.util.profiler;

import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class Profiler {
    private static final Logger LOGGER = LogManager.getLogger();
    private final List<String> locations = Lists.newArrayList();
    private final List<Long> times = Lists.newArrayList();
    public boolean profiling;
    private String currentLocation = "";
    private final Map<String, Long> timesByLocation = Maps.newHashMap();

    public void reset() {
        this.timesByLocation.clear();
        this.currentLocation = "";
        this.locations.clear();
    }

    public void push(String location) {
        if (this.profiling) {
            if (this.currentLocation.length() > 0) {
                this.currentLocation = this.currentLocation + ".";
            }

            this.currentLocation = this.currentLocation + location;
            this.locations.add(this.currentLocation);
            this.times.add(System.nanoTime());
        }
    }

    public void pop() {
        if (this.profiling) {
            long i = System.nanoTime();
            long j = this.times.remove(this.times.size() - 1);
            this.locations.remove(this.locations.size() - 1);
            long k = i - j;
            if (this.timesByLocation.containsKey(this.currentLocation)) {
                this.timesByLocation.put(this.currentLocation, this.timesByLocation.get(this.currentLocation) + k);
            } else {
                this.timesByLocation.put(this.currentLocation, k);
            }

            if (k > 100000000L) {
                LOGGER.warn("Something's taking too long! '" + this.currentLocation + "' took aprox " + k / 1000000.0 + " ms");
            }

            this.currentLocation = !this.locations.isEmpty() ? this.locations.get(this.locations.size() - 1) : "";
        }
    }

    public List<Profiler.Result> getResults(String location) {
        if (!this.profiling) {
            return null;
        }

        String s = location;
        long i = this.timesByLocation.containsKey("root") ? this.timesByLocation.get("root") : 0L;
        long j = this.timesByLocation.containsKey(location) ? this.timesByLocation.get(location) : -1L;
        List<Profiler.Result> list = Lists.newArrayList();
        if (location.length() > 0) {
            location = location + ".";
        }

        long k = 0L;

        for (String s1 : this.timesByLocation.keySet()) {
            if (s1.length() > location.length() && s1.startsWith(location) && s1.indexOf(".", location.length() + 1) < 0) {
                k += this.timesByLocation.get(s1);
            }
        }

        float f = (float)k;
        if (k < j) {
            k = j;
        }

        if (i < k) {
            i = k;
        }

        for (String s2 : this.timesByLocation.keySet()) {
            if (s2.length() > location.length() && s2.startsWith(location) && s2.indexOf(".", location.length() + 1) < 0) {
                long l = this.timesByLocation.get(s2);
                double d0 = l * 100.0 / k;
                double d1 = l * 100.0 / i;
                String s3 = s2.substring(location.length());
                list.add(new Profiler.Result(s3, d0, d1));
            }
        }

        for (String s4 : this.timesByLocation.keySet()) {
            this.timesByLocation.put(s4, this.timesByLocation.get(s4) * 999L / 1000L);
        }

        if ((float)k > f) {
            list.add(new Profiler.Result("unspecified", ((float)k - f) * 100.0 / k, ((float)k - f) * 100.0 / i));
        }

        Collections.sort(list);
        list.add(0, new Profiler.Result(s, 100.0, k * 100.0 / i));
        return list;
    }

    public void swap(String location) {
        this.pop();
        this.push(location);
    }

    public String getCurrentLocation() {
        return this.locations.size() == 0 ? "[UNKNOWN]" : this.locations.get(this.locations.size() - 1);
    }

    public static final class Result implements Comparable<Profiler.Result> {
        public double percentageOfParent;
        public double percentageOfTotal;
        public String location;

        public Result(String location, double percentageOfParent, double percentageOfTotal) {
            this.location = location;
            this.percentageOfParent = percentageOfParent;
            this.percentageOfTotal = percentageOfTotal;
        }

        public int compareTo(Profiler.Result result) {
            if (result.percentageOfParent < this.percentageOfParent) {
                return -1;
            } else {
                return result.percentageOfParent > this.percentageOfParent ? 1 : result.location.compareTo(this.location);
            }
        }

        public int getColor() {
            return (this.location.hashCode() & 11184810) + 4473924;
        }
    }
}
