package net.minecraft.snooper;

import com.google.common.collect.Maps;
import java.lang.management.ManagementFactory;
import java.lang.management.RuntimeMXBean;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.List;
import java.util.Map;
import java.util.Timer;
import java.util.TimerTask;
import java.util.UUID;
import java.util.Map.Entry;
import net.minecraft.util.HttpUtil;

public class Snooper {
    private final Map<String, Object> fixedData = Maps.newHashMap();
    private final Map<String, Object> dynamicData = Maps.newHashMap();
    private final String token = UUID.randomUUID().toString();
    private final URL url;
    private final SnooperPopulator populator;
    private final Timer timer = new Timer("Snooper Timer", true);
    private final Object lock = new Object();
    private final long initTime;
    private boolean initialized;
    private int count;

    public Snooper(String side, SnooperPopulator populator, long time) {
        try {
            this.url = new URL("http://snoop.minecraft.net/" + side + "?version=" + 2);
        } catch (MalformedURLException malformedurlexception) {
            throw new IllegalArgumentException();
        }

        this.populator = populator;
        this.initTime = time;
    }

    public void init() {
        if (!this.initialized) {
            this.initialized = true;
            this.doInit();
            this.timer.schedule(new TimerTask() {
                @Override
                public void run() {
                    if (Snooper.this.populator.isSnooperEnabled()) {
                        Map<String, Object> map;
                        synchronized (Snooper.this.lock) {
                            map = Maps.newHashMap(Snooper.this.dynamicData);
                            if (Snooper.this.count == 0) {
                                map.putAll(Snooper.this.fixedData);
                            }

                            map.put("snooper_count", Snooper.this.count++);
                            map.put("snooper_token", Snooper.this.token);
                        }

                        HttpUtil.post(Snooper.this.url, map, true);
                    }
                }
            }, 0L, 900000L);
        }
    }

    private void doInit() {
        this.putJvmArgs();
        this.putDynamic("snooper_token", this.token);
        this.putFixed("snooper_token", this.token);
        this.putFixed("os_name", System.getProperty("os.name"));
        this.putFixed("os_version", System.getProperty("os.version"));
        this.putFixed("os_architecture", System.getProperty("os.arch"));
        this.putFixed("java_version", System.getProperty("java.version"));
        this.putDynamic("version", "1.8.8");
        this.populator.initSnooper(this);
    }

    private void putJvmArgs() {
        RuntimeMXBean runtimemxbean = ManagementFactory.getRuntimeMXBean();
        List<String> list = runtimemxbean.getInputArguments();
        int i = 0;

        for (String s : list) {
            if (s.startsWith("-X")) {
                this.putDynamic("jvm_arg[" + i++ + "]", s);
            }
        }

        this.putDynamic("jvm_args", i);
    }

    public void populate() {
        this.putFixed("memory_total", Runtime.getRuntime().totalMemory());
        this.putFixed("memory_max", Runtime.getRuntime().maxMemory());
        this.putFixed("memory_free", Runtime.getRuntime().freeMemory());
        this.putFixed("cpu_cores", Runtime.getRuntime().availableProcessors());
        this.populator.populateSnooper(this);
    }

    public void putDynamic(String key, Object value) {
        synchronized (this.lock) {
            this.dynamicData.put(key, value);
        }
    }

    public void putFixed(String key, Object value) {
        synchronized (this.lock) {
            this.fixedData.put(key, value);
        }
    }

    public Map<String, String> getAll() {
        Map<String, String> map = Maps.newLinkedHashMap();
        synchronized (this.lock) {
            this.populate();

            for (Entry<String, Object> entry : this.fixedData.entrySet()) {
                map.put(entry.getKey(), entry.getValue().toString());
            }

            for (Entry<String, Object> entry1 : this.dynamicData.entrySet()) {
                map.put(entry1.getKey(), entry1.getValue().toString());
            }

            return map;
        }
    }

    public boolean isInitialized() {
        return this.initialized;
    }

    public void interrupt() {
        this.timer.cancel();
    }

    public String getToken() {
        return this.token;
    }

    public long getInitTime() {
        return this.initTime;
    }
}
