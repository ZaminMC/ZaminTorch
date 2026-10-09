package net.minecraft.client.network;

import java.net.IDN;
import java.util.Hashtable;
import javax.naming.directory.Attributes;
import javax.naming.directory.DirContext;
import javax.naming.directory.InitialDirContext;

public class ServerAddress {
    private final String address;
    private final int port;

    private ServerAddress(String address, int port) {
        this.address = address;
        this.port = port;
    }

    public String getAddress() {
        return IDN.toASCII(this.address);
    }

    public int getPort() {
        return this.port;
    }

    public static ServerAddress parse(String address) {
        if (address == null) {
            return null;
        }

        String[] astring = address.split(":");
        if (address.startsWith("[")) {
            int i = address.indexOf("]");
            if (i > 0) {
                String s = address.substring(1, i);
                String s1 = address.substring(i + 1).trim();
                if (s1.startsWith(":") && s1.length() > 0) {
                    s1 = s1.substring(1);
                    astring = new String[]{s, s1};
                } else {
                    astring = new String[]{s};
                }
            }
        }

        if (astring.length > 2) {
            astring = new String[]{address};
        }

        String s2 = astring[0];
        int j = astring.length > 1 ? getPortOrDefault(astring[1], 25565) : 25565;
        if (j == 25565) {
            String[] astring1 = parseAddress(s2);
            s2 = astring1[0];
            j = getPortOrDefault(astring1[1], 25565);
        }

        return new ServerAddress(s2, j);
    }

    private static String[] parseAddress(String address) {
        try {
            String s = "com.sun.jndi.dns.DnsContextFactory";
            Class.forName("com.sun.jndi.dns.DnsContextFactory");
            Hashtable hashtable = new Hashtable();
            hashtable.put("java.naming.factory.initial", "com.sun.jndi.dns.DnsContextFactory");
            hashtable.put("java.naming.provider.url", "dns:");
            hashtable.put("com.sun.jndi.dns.timeout.retries", "1");
            DirContext dircontext = new InitialDirContext(hashtable);
            Attributes attributes = dircontext.getAttributes("_minecraft._tcp." + address, new String[]{"SRV"});
            String[] astring = attributes.get("srv").get().toString().split(" ", 4);
            return new String[]{astring[3], astring[2]};
        } catch (Throwable throwable) {
            return new String[]{address, Integer.toString(25565)};
        }
    }

    private static int getPortOrDefault(String port, int def) {
        try {
            return Integer.parseInt(port.trim());
        } catch (Exception exception) {
            return def;
        }
    }
}
