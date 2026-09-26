package com.netscope.zone;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Reads nmap's XML output (-oX -). Pure text parsing - no process handling here. */
public final class NmapParser {

    public record OpenPort(int port, String service) {
    }

    public record NmapHost(String ip, String hostname, Double latencyMs, List<OpenPort> openPorts) {
    }

    private static final Pattern HOST = Pattern.compile("<host[ >].*?</host>", Pattern.DOTALL);
    private static final Pattern STATUS = Pattern.compile("<status state=\"(\\w+)\"");
    private static final Pattern ADDR = Pattern.compile("<address addr=\"([0-9.]+)\" addrtype=\"ipv4\"");
    private static final Pattern NAME = Pattern.compile("<hostname name=\"([^\"]+)\"");
    private static final Pattern SRTT = Pattern.compile("<times srtt=\"(\\d+)\"");
    private static final Pattern PORT = Pattern.compile("<port protocol=\"tcp\" portid=\"(\\d+)\">(.*?)</port>", Pattern.DOTALL);
    private static final Pattern SERVICE = Pattern.compile("<service name=\"([^\"]*)\"");

    private NmapParser() {
    }

    /** Hosts that nmap reports as "up". */
    public static List<NmapHost> parse(String xml) {
        List<NmapHost> out = new ArrayList<>();
        if (xml == null) return out;
        Matcher hm = HOST.matcher(xml);
        while (hm.find()) {
            String block = hm.group();
            Matcher st = STATUS.matcher(block);
            if (!st.find() || !st.group(1).equals("up")) continue;
            Matcher a = ADDR.matcher(block);
            if (!a.find()) continue;
            Matcher n = NAME.matcher(block);
            Matcher t = SRTT.matcher(block);
            Double ms = t.find() ? Math.round(Long.parseLong(t.group(1)) / 100.0) / 10.0 : null;   // microseconds -> ms (1 decimal)
            List<OpenPort> ports = new ArrayList<>();
            Matcher pm = PORT.matcher(block);
            while (pm.find()) {
                if (!pm.group(2).contains("state=\"open\"")) continue;
                Matcher s = SERVICE.matcher(pm.group(2));
                ports.add(new OpenPort(Integer.parseInt(pm.group(1)), s.find() ? s.group(1) : ""));
            }
            out.add(new NmapHost(a.group(1), n.find() ? n.group(1) : null, ms, ports));
        }
        return out;
    }
}
