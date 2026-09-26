package com.netscope.entity;

import jakarta.persistence.*;

import java.time.Instant;

/** Snapshot of a topology node taken after a full scan (lets you compare the network over time). */
@Entity
@Table(name = "topology_node", indexes = @Index(name = "idx_tn_captured", columnList = "captured_at"))
public class TopologyNodeEntity {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(name = "session_id") private Long sessionId;
    @Column(name = "captured_at") private Instant capturedAt;
    @Column(name = "node_key", length = 80) private String nodeKey;
    @Column(length = 255) private String label;
    @Column(name = "node_type", length = 30) private String nodeType;
    @Column(length = 45) private String ip;
    @Column(length = 17) private String mac;
    @Column(length = 20) private String state;

    public Long getId() { return id; }
    public Long getSessionId() { return sessionId; }
    public void setSessionId(Long v) { sessionId = v; }
    public Instant getCapturedAt() { return capturedAt; }
    public void setCapturedAt(Instant v) { capturedAt = v; }
    public String getNodeKey() { return nodeKey; }
    public void setNodeKey(String v) { nodeKey = v; }
    public String getLabel() { return label; }
    public void setLabel(String v) { label = v; }
    public String getNodeType() { return nodeType; }
    public void setNodeType(String v) { nodeType = v; }
    public String getIp() { return ip; }
    public void setIp(String v) { ip = v; }
    public String getMac() { return mac; }
    public void setMac(String v) { mac = v; }
    public String getState() { return state; }
    public void setState(String v) { state = v; }
}
