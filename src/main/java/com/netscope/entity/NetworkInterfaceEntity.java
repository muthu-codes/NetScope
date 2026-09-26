package com.netscope.entity;

import jakarta.persistence.*;

import java.time.Instant;

@Entity
@Table(name = "network_interface")
public class NetworkInterfaceEntity {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(nullable = false, unique = true, length = 200) private String name;
    @Column(name = "display_name", length = 255) private String displayName;
    @Column(name = "interface_type", length = 20) private String interfaceType;
    @Column(length = 17) private String mac;
    @Column(length = 100) private String ipv4;
    @Column(name = "is_up") private boolean up;
    @Column(name = "last_seen") private Instant lastSeen;

    public Long getId() { return id; }
    public String getName() { return name; }
    public void setName(String v) { name = v; }
    public String getDisplayName() { return displayName; }
    public void setDisplayName(String v) { displayName = v; }
    public String getInterfaceType() { return interfaceType; }
    public void setInterfaceType(String v) { interfaceType = v; }
    public String getMac() { return mac; }
    public void setMac(String v) { mac = v; }
    public String getIpv4() { return ipv4; }
    public void setIpv4(String v) { ipv4 = v; }
    public boolean isUp() { return up; }
    public void setUp(boolean v) { up = v; }
    public Instant getLastSeen() { return lastSeen; }
    public void setLastSeen(Instant v) { lastSeen = v; }
}
