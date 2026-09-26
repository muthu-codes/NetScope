package com.netscope.repository;

import com.netscope.entity.TopologyEdgeEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;

public interface TopologyEdgeRepository extends JpaRepository<TopologyEdgeEntity, Long> {
    long deleteByCapturedAtBefore(Instant cutoff);
}
