package com.netscope.entity;

import jakarta.persistence.*;

import java.time.Instant;

@Entity
@Table(name = "topology_edge", indexes = @Index(name = "idx_te_captured", columnList = "captured_at"))
public class TopologyEdgeEntity {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(name = "session_id") private Long sessionId;
    @Column(name = "captured_at") private Instant capturedAt;
    @Column(name = "source_key", length = 80) private String sourceKey;
    @Column(name = "target_key", length = 80) private String targetKey;
    @Column(length = 40) private String relation;
    @Column(length = 30) private String evidence;
    @Column(length = 12) private String confidence;

    public Long getId() { return id; }
    public Long getSessionId() { return sessionId; }
    public void setSessionId(Long v) { sessionId = v; }
    public Instant getCapturedAt() { return capturedAt; }
    public void setCapturedAt(Instant v) { capturedAt = v; }
    public String getSourceKey() { return sourceKey; }
    public void setSourceKey(String v) { sourceKey = v; }
    public String getTargetKey() { return targetKey; }
    public void setTargetKey(String v) { targetKey = v; }
    public String getRelation() { return relation; }
    public void setRelation(String v) { relation = v; }
    public String getEvidence() { return evidence; }
    public void setEvidence(String v) { evidence = v; }
    public String getConfidence() { return confidence; }
    public void setConfidence(String v) { confidence = v; }
}
