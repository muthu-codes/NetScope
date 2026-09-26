package com.netscope.repository;

import com.netscope.entity.TopologyNodeEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;

public interface TopologyNodeRepository extends JpaRepository<TopologyNodeEntity, Long> {
    long deleteByCapturedAtBefore(Instant cutoff);
}
