package net.minecraft.util;

import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.ListeningExecutorService;
import com.google.common.util.concurrent.MoreExecutors;
import com.google.common.util.concurrent.ThreadFactoryBuilder;
import java.io.BufferedReader;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.UnsupportedEncodingException;
import java.net.HttpURLConnection;
import java.net.Proxy;
import java.net.ServerSocket;
import java.net.URL;
import java.net.URLEncoder;
import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import net.minecraft.server.MinecraftServer;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class HttpUtil {
    public static final ListeningExecutorService DOWNLOAD_THREAD_FACTORY = MoreExecutors.listeningDecorator(
        Executors.newCachedThreadPool(new ThreadFactoryBuilder().setDaemon(true).setNameFormat("Downloader %d").build())
    );
    private static final AtomicInteger downloadThreadCounter = new AtomicInteger(0);
    private static final Logger LOGGER = LogManager.getLogger();

    public static String buildString(Map<String, Object> data) {
        StringBuilder stringbuilder = new StringBuilder();

        for (Entry<String, Object> entry : data.entrySet()) {
            if (stringbuilder.length() > 0) {
                stringbuilder.append('&');
            }

            try {
                stringbuilder.append(URLEncoder.encode(entry.getKey(), "UTF-8"));
            } catch (UnsupportedEncodingException unsupportedencodingexception1) {
                unsupportedencodingexception1.printStackTrace();
            }

            if (entry.getValue() != null) {
                stringbuilder.append('=');

                try {
                    stringbuilder.append(URLEncoder.encode(entry.getValue().toString(), "UTF-8"));
                } catch (UnsupportedEncodingException unsupportedencodingexception) {
                    unsupportedencodingexception.printStackTrace();
                }
            }
        }

        return stringbuilder.toString();
    }

    public static String post(URL url, Map<String, Object> data, boolean silent) {
        return post(url, buildString(data), silent);
    }

    private static String post(URL url, String data, boolean silent) {
        try {
            Proxy proxy = MinecraftServer.getInstance() == null ? null : MinecraftServer.getInstance().getProxy();
            if (proxy == null) {
                proxy = Proxy.NO_PROXY;
            }

            HttpURLConnection httpurlconnection = (HttpURLConnection)url.openConnection(proxy);
            httpurlconnection.setRequestMethod("POST");
            httpurlconnection.setRequestProperty("Content-Type", "application/x-www-form-urlencoded");
            httpurlconnection.setRequestProperty("Content-Length", "" + data.getBytes().length);
            httpurlconnection.setRequestProperty("Content-Language", "en-US");
            httpurlconnection.setUseCaches(false);
            httpurlconnection.setDoInput(true);
            httpurlconnection.setDoOutput(true);
            DataOutputStream dataoutputstream = new DataOutputStream(httpurlconnection.getOutputStream());
            dataoutputstream.writeBytes(data);
            dataoutputstream.flush();
            dataoutputstream.close();
            BufferedReader bufferedreader = new BufferedReader(new InputStreamReader(httpurlconnection.getInputStream()));
            StringBuffer stringbuffer = new StringBuffer();

            String s;
            while ((s = bufferedreader.readLine()) != null) {
                stringbuffer.append(s);
                stringbuffer.append('\r');
            }

            bufferedreader.close();
            return stringbuffer.toString();
        } catch (Exception exception) {
            if (!silent) {
                LOGGER.error("Could not post to " + url, exception);
            }

            return "";
        }
    }

    public static ListenableFuture<Object> downloadServerResourcePack(
        File file, String url, Map<String, String> data, int maxFileSize, ProgressListener listener, Proxy proxy
    ) {
        return (ListenableFuture<Object>)DOWNLOAD_THREAD_FACTORY.submit(new Runnable() {
            @Override
            public void run() {
                HttpURLConnection httpurlconnection = null;
                InputStream inputstream = null;
                OutputStream outputstream = null;
                if (listener != null) {
                    listener.updateTitle("Downloading Resource Pack");
                    listener.progressStage("Making Request...");
                }

                try {
                    byte[] abyte = new byte[4096];
                    URL urlx = new URL(url);
                    httpurlconnection = (HttpURLConnection)urlx.openConnection(proxy);
                    float f = 0.0F;
                    float f1 = data.entrySet().size();

                    for (Entry<String, String> entry : data.entrySet()) {
                        httpurlconnection.setRequestProperty(entry.getKey(), entry.getValue());
                        if (listener != null) {
                            listener.progressStagePercentage((int)(++f / f1 * 100.0F));
                        }
                    }

                    inputstream = httpurlconnection.getInputStream();
                    f1 = httpurlconnection.getContentLength();
                    int i = httpurlconnection.getContentLength();
                    if (listener != null) {
                        listener.progressStage(String.format("Downloading file (%.2f MB)...", f1 / 1000.0F / 1000.0F));
                    }

                    if (file.exists()) {
                        long j = file.length();
                        if (j == i) {
                            if (listener != null) {
                                listener.setDone();
                            }

                            return;
                        }

                        HttpUtil.LOGGER.warn("Deleting " + file + " as it does not match what we currently have (" + i + " vs our " + j + ").");
                        FileUtils.deleteQuietly(file);
                    } else if (file.getParentFile() != null) {
                        file.getParentFile().mkdirs();
                    }

                    outputstream = new DataOutputStream(new FileOutputStream(file));
                    if (maxFileSize > 0 && f1 > maxFileSize) {
                        if (listener != null) {
                            listener.setDone();
                        }

                        throw new IOException("Filesize is bigger than maximum allowed (file is " + f + ", limit is " + maxFileSize + ")");
                    }

                    int k = 0;

                    while (true) {
                        if ((k = inputstream.read(abyte)) < 0) {
                            if (listener != null) {
                                listener.setDone();
                            }

                            return;
                        }

                        f += k;
                        if (listener != null) {
                            listener.progressStagePercentage((int)(f / f1 * 100.0F));
                        }

                        if (maxFileSize > 0 && f > maxFileSize) {
                            if (listener != null) {
                                listener.setDone();
                            }

                            throw new IOException("Filesize was bigger than maximum allowed (got >= " + f + ", limit was " + maxFileSize + ")");
                        }

                        if (Thread.interrupted()) {
                            HttpUtil.LOGGER.error("INTERRUPTED");
                            if (listener != null) {
                                listener.setDone();
                            }
                            break;
                        }

                        outputstream.write(abyte, 0, k);
                    }
                } catch (Throwable throwable) {
                    throwable.printStackTrace();
                    if (httpurlconnection != null) {
                        InputStream inputstream1 = httpurlconnection.getErrorStream();

                        try {
                            HttpUtil.LOGGER.error(IOUtils.toString(inputstream1));
                        } catch (IOException ioexception) {
                            ioexception.printStackTrace();
                        }
                    }

                    if (listener != null) {
                        listener.setDone();
                    }

                    return;
                } finally {
                    IOUtils.closeQuietly(inputstream);
                    IOUtils.closeQuietly(outputstream);
                }
            }
        });
    }

    public static int getLocalPort() throws IOException {
        ServerSocket serversocket = null;
        int i = -1;

        try {
            serversocket = new ServerSocket(0);
            i = serversocket.getLocalPort();
        } finally {
            try {
                if (serversocket != null) {
                    serversocket.close();
                }
            } catch (IOException ioexception) {
            }
        }

        return i;
    }

    public static String getUrlContents(URL url) throws IOException {
        HttpURLConnection httpurlconnection = (HttpURLConnection)url.openConnection();
        httpurlconnection.setRequestMethod("GET");
        BufferedReader bufferedreader = new BufferedReader(new InputStreamReader(httpurlconnection.getInputStream()));
        StringBuilder stringbuilder = new StringBuilder();

        String s;
        while ((s = bufferedreader.readLine()) != null) {
            stringbuilder.append(s);
            stringbuilder.append('\r');
        }

        bufferedreader.close();
        return stringbuilder.toString();
    }
}
