package com.netscope.service;

import com.netscope.model.PingResult;
import com.netscope.model.PingStats;
import com.netscope.util.CommandExecutor;
import com.netscope.util.IpAddressUtils;
import com.netscope.util.NetworkUtils;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * ICMP echo ("ping") using the operating system's own ping command.
 * Why not Java's InetAddress.isReachable()? Without administrator rights on Windows it silently falls back to a
 * TCP probe on port 7, which almost nothing answers - so it reports live devices as dead.
 */
@Service
public class PingService {

    private static final Pattern TTL = Pattern.compile("(?i)\\bttl[=:]\\s*(\\d+)");
    private static final Pattern TIME = Pattern.compile("([=<])\\s*(\\d+(?:[.,]\\d+)?)\\s*ms");

    public record Reply(double latencyMs, int ttl) {
    }

    private final CommandExecutor exec;

    public PingService(CommandExecutor exec) {
        this.exec = exec;
    }

    /** One echo request. */
    public PingResult ping(String ip, int timeoutMs) {
        requireIpv4(ip);
        CommandExecutor.CommandResult r = exec.run(timeoutMs + 4000L, command(ip, 1, timeoutMs));
        List<Reply> replies = parseReplies(r.output(), ip);
        if (replies.isEmpty()) return PingResult.failed(ip);
        Reply first = replies.get(0);
        return new PingResult(ip, true, first.latencyMs(), first.ttl());
    }

    /** Several echo requests in one process (used for packet loss / jitter measurements). */
    public PingStats pingStats(String ip, int count, int timeoutMs) {
        requireIpv4(ip);
        int n = Math.max(1, Math.min(count, 30));
        long total = (long) n * (timeoutMs + 1000L) + 4000L;
        CommandExecutor.CommandResult r = exec.run(total, command(ip, n, timeoutMs));
        return statsOf(ip, n, parseReplies(r.output(), ip));
    }

    private static void requireIpv4(String ip) {
        if (!IpAddressUtils.isValidIpv4(ip)) throw new IllegalArgumentException("'" + ip + "' is not a valid IPv4 address.");
    }

    private static List<String> command(String ip, int count, int timeoutMs) {
        List<String> c = new ArrayList<>();
        switch (NetworkUtils.os()) {
            case WINDOWS -> c.addAll(List.of("ping", "-n", String.valueOf(count), "-w", String.valueOf(timeoutMs)));
            case MAC -> c.addAll(List.of("ping", "-c", String.valueOf(count), "-W", String.valueOf(timeoutMs)));
            default -> {
                c.addAll(List.of("ping", "-c", String.valueOf(count), "-W", String.valueOf(Math.max(1, (timeoutMs + 999) / 1000))));
                if (count > 1) c.addAll(List.of("-i", "0.2"));
            }
        }
        c.add(ip);
        return c;
    }

    /**
     * Reads real echo replies out of ping's text output. A line only counts when it comes from the target IP AND
     * contains TTL=... (this ignores "Destination host unreachable" replies that Windows prints with exit code 0).
     */
    public static List<Reply> parseReplies(String output, String ip) {
        List<Reply> replies = new ArrayList<>();
        if (output == null || output.isEmpty()) return replies;
        Pattern from = Pattern.compile("(?<![\\d.])" + Pattern.quote(ip) + "(?!\\d)");
        for (String line : output.split("\\R")) {
            if (!from.matcher(line).find()) continue;
            Matcher ttl = TTL.matcher(line);
            if (!ttl.find()) continue;
            Matcher time = TIME.matcher(line);
            if (!time.find()) continue;
            double v = Double.parseDouble(time.group(2).replace(',', '.'));
            if (time.group(1).equals("<")) v = v / 2.0;      // Windows prints "time<1ms"
            replies.add(new Reply(v, Integer.parseInt(ttl.group(1))));
        }
        return replies;
    }

    public static PingStats statsOf(String ip, int sent, List<Reply> replies) {
        List<Double> samples = new ArrayList<>();
        for (Reply r : replies) if (samples.size() < sent) samples.add(r.latencyMs());
        int received = samples.size();
        double loss = sent == 0 ? 0 : (sent - received) * 100.0 / sent;
        if (samples.isEmpty()) return new PingStats(ip, sent, 0, 100.0, null, null, null, null, samples);
        double min = Double.MAX_VALUE, max = 0, sum = 0;
        for (double s : samples) {
            min = Math.min(min, s);
            max = Math.max(max, s);
            sum += s;
        }
        double jitter = 0;
        for (int i = 1; i < samples.size(); i++) jitter += Math.abs(samples.get(i) - samples.get(i - 1));
        jitter = samples.size() > 1 ? jitter / (samples.size() - 1) : 0;
        return new PingStats(ip, sent, received, round1(loss), round1(min), round1(sum / received), round1(max), round1(jitter), samples);
    }

    private static double round1(double v) {
        return Math.round(v * 10.0) / 10.0;
    }
}
