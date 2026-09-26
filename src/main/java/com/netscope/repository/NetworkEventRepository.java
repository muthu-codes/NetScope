package com.netscope.repository;

import com.netscope.entity.NetworkEventEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.List;

public interface NetworkEventRepository extends JpaRepository<NetworkEventEntity, Long> {
    List<NetworkEventEntity> findTop500ByOrderByOccurredAtDesc();

    long deleteByOccurredAtBefore(Instant cutoff);
}
