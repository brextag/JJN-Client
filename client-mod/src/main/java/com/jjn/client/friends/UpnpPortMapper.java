package com.jjn.client.friends;

import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Collections;
import java.util.Enumeration;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Pure-Java UPnP IGD port mapper for automatic router port forwarding
 * when hosting a singleplayer world to friends.
 */
public final class UpnpPortMapper {

    public record MappingResult(
            boolean success,
            String externalIp,
            int port,
            String localIp,
            String controlUrl,
            String serviceType,
            String message
    ) {}

    private static final HttpClient HTTP = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(3))
            .build();

    private UpnpPortMapper() {}

    public static String findOutboundLanIp() {
        try (DatagramSocket socket = new DatagramSocket()) {
            socket.connect(InetAddress.getByName("8.8.8.8"), 10002);
            InetAddress local = socket.getLocalAddress();
            if (local instanceof Inet4Address && !local.isLoopbackAddress()) {
                return local.getHostAddress();
            }
        } catch (Exception ignored) {
        }
        try {
            Enumeration<NetworkInterface> ifaces = NetworkInterface.getNetworkInterfaces();
            for (NetworkInterface iface : Collections.list(ifaces)) {
                if (!iface.isUp() || iface.isLoopback()) continue;
                for (InetAddress addr : Collections.list(iface.getInetAddresses())) {
                    if (addr instanceof Inet4Address && !addr.isLoopbackAddress()) {
                        return addr.getHostAddress();
                    }
                }
            }
        } catch (Exception ignored) {
        }
        return "127.0.0.1";
    }

    public static MappingResult mapTcpPort(int port) {
        String localIp = findOutboundLanIp();
        try {
            String location = discoverGatewayLocation();
            if (location == null || location.isBlank()) {
                return new MappingResult(false, "", port, localIp, "", "", "No UPnP router responded (use LAN, VPN, or Relay)");
            }

            HttpRequest descReq = HttpRequest.newBuilder()
                    .uri(URI.create(location))
                    .timeout(Duration.ofSeconds(3))
                    .GET()
                    .build();
            HttpResponse<String> descResp = HTTP.send(descReq, HttpResponse.BodyHandlers.ofString());
            if (descResp.statusCode() / 100 != 2) {
                return new MappingResult(false, "", port, localIp, "", "", "UPnP gateway XML unavailable");
            }

            String xml = descResp.body();
            String serviceType = findServiceType(xml);
            String controlPath = findControlUrl(xml, serviceType);
            if (serviceType == null || controlPath == null) {
                return new MappingResult(false, "", port, localIp, "", "", "Router does not expose WANIPConnection");
            }

            String controlUrl = URI.create(location).resolve(controlPath).toString();
            String externalIp = fetchExternalIp(controlUrl, serviceType);

            boolean added = addPortMapping(controlUrl, serviceType, port, localIp);
            if (!added) {
                return new MappingResult(false, externalIp, port, localIp, controlUrl, serviceType, "Router rejected UPnP mapping (use Relay or Tailscale)");
            }

            String msg = (externalIp != null && !externalIp.isBlank())
                    ? "UPnP open: " + externalIp + ":" + port
                    : "UPnP mapped port " + port;
            return new MappingResult(true, externalIp == null ? "" : externalIp, port, localIp, controlUrl, serviceType, msg);
        } catch (Exception ex) {
            return new MappingResult(false, "", port, localIp, "", "", "UPnP unavailable: " + ex.getMessage());
        }
    }

    public static void unmap(MappingResult mapping) {
        if (mapping == null || !mapping.success() || mapping.controlUrl().isBlank()) return;
        try {
            String body = "<?xml version=\"1.0\"?>"
                    + "<s:Envelope xmlns:s=\"http://schemas.xmlsoap.org/soap/envelope/\" "
                    + "s:encodingStyle=\"http://schemas.xmlsoap.org/soap/encoding/\">"
                    + "<s:Body><u:DeletePortMapping xmlns:u=\"" + mapping.serviceType() + "\">"
                    + "<NewRemoteHost></NewRemoteHost>"
                    + "<NewExternalPort>" + mapping.port() + "</NewExternalPort>"
                    + "<NewProtocol>TCP</NewProtocol>"
                    + "</u:DeletePortMapping></s:Body></s:Envelope>";
            sendSoap(mapping.controlUrl(), mapping.serviceType(), "DeletePortMapping", body);
        } catch (Exception ignored) {
        }
    }

    private static String discoverGatewayLocation() {
        String[] targets = {
                "urn:schemas-upnp-org:device:InternetGatewayDevice:1",
                "urn:schemas-upnp-org:service:WANIPConnection:1",
                "urn:schemas-upnp-org:service:WANPPPConnection:1"
        };
        try (DatagramSocket socket = new DatagramSocket()) {
            socket.setSoTimeout(1200);
            InetAddress group = InetAddress.getByName("239.255.255.250");
            for (String st : targets) {
                String msg = "M-SEARCH * HTTP/1.1\r\n"
                        + "HOST: 239.255.255.250:1900\r\n"
                        + "MAN: \"ssdp:discover\"\r\n"
                        + "MX: 2\r\n"
                        + "ST: " + st + "\r\n\r\n";
                byte[] bytes = msg.getBytes(StandardCharsets.UTF_8);
                socket.send(new DatagramPacket(bytes, bytes.length, group, 1900));
            }
            byte[] buf = new byte[4096];
            DatagramPacket resp = new DatagramPacket(buf, buf.length);
            socket.receive(resp);
            String reply = new String(resp.getData(), resp.getOffset(), resp.getLength(), StandardCharsets.UTF_8);
            Matcher m = Pattern.compile("(?im)^LOCATION:\\s*(.+)$").matcher(reply);
            if (m.find()) {
                return m.group(1).trim();
            }
        } catch (Exception ignored) {
        }
        return null;
    }

    private static String findServiceType(String xml) {
        if (xml.contains("urn:schemas-upnp-org:service:WANIPConnection:1")) {
            return "urn:schemas-upnp-org:service:WANIPConnection:1";
        }
        if (xml.contains("urn:schemas-upnp-org:service:WANIPConnection:2")) {
            return "urn:schemas-upnp-org:service:WANIPConnection:2";
        }
        if (xml.contains("urn:schemas-upnp-org:service:WANPPPConnection:1")) {
            return "urn:schemas-upnp-org:service:WANPPPConnection:1";
        }
        return null;
    }

    private static String findControlUrl(String xml, String serviceType) {
        if (serviceType == null) return null;
        int idx = xml.indexOf(serviceType);
        if (idx < 0) return null;
        String tail = xml.substring(idx, Math.min(xml.length(), idx + 1500));
        Matcher m = Pattern.compile("<controlURL>([^<]+)</controlURL>", Pattern.CASE_INSENSITIVE).matcher(tail);
        if (m.find()) {
            return m.group(1).trim();
        }
        return null;
    }

    private static String fetchExternalIp(String controlUrl, String serviceType) {
        try {
            String body = "<?xml version=\"1.0\"?>"
                    + "<s:Envelope xmlns:s=\"http://schemas.xmlsoap.org/soap/envelope/\" "
                    + "s:encodingStyle=\"http://schemas.xmlsoap.org/soap/encoding/\">"
                    + "<s:Body><u:GetExternalIPAddress xmlns:u=\"" + serviceType + "\">"
                    + "</u:GetExternalIPAddress></s:Body></s:Envelope>";
            String resp = sendSoap(controlUrl, serviceType, "GetExternalIPAddress", body);
            Matcher m = Pattern.compile("<NewExternalIPAddress>([^<]+)</NewExternalIPAddress>", Pattern.CASE_INSENSITIVE).matcher(resp);
            if (m.find()) {
                return m.group(1).trim();
            }
        } catch (Exception ignored) {
        }
        return "";
    }

    private static boolean addPortMapping(String controlUrl, String serviceType, int port, String localIp) {
        try {
            String body = "<?xml version=\"1.0\"?>"
                    + "<s:Envelope xmlns:s=\"http://schemas.xmlsoap.org/soap/envelope/\" "
                    + "s:encodingStyle=\"http://schemas.xmlsoap.org/soap/encoding/\">"
                    + "<s:Body><u:AddPortMapping xmlns:u=\"" + serviceType + "\">"
                    + "<NewRemoteHost></NewRemoteHost>"
                    + "<NewExternalPort>" + port + "</NewExternalPort>"
                    + "<NewProtocol>TCP</NewProtocol>"
                    + "<NewInternalPort>" + port + "</NewInternalPort>"
                    + "<NewInternalClient>" + localIp + "</NewInternalClient>"
                    + "<NewEnabled>1</NewEnabled>"
                    + "<NewPortMappingDescription>JJN Client World</NewPortMappingDescription>"
                    + "<NewLeaseDuration>0</NewLeaseDuration>"
                    + "</u:AddPortMapping></s:Body></s:Envelope>";
            String resp = sendSoap(controlUrl, serviceType, "AddPortMapping", body);
            return !resp.contains("UPnPError");
        } catch (Exception ignored) {
            return false;
        }
    }

    private static String sendSoap(String url, String serviceType, String action, String body) throws Exception {
        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(Duration.ofSeconds(3))
                .header("Content-Type", "text/xml; charset=\"utf-8\"")
                .header("SOAPAction", "\"" + serviceType + "#" + action + "\"")
                .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8))
                .build();
        HttpResponse<String> resp = HTTP.send(req, HttpResponse.BodyHandlers.ofString());
        return resp.body();
    }
}
