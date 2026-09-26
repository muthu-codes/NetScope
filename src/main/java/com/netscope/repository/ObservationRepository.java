package com.netscope.repository;

import com.netscope.entity.ObservationEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.List;

public interface ObservationRepository extends JpaRepository<ObservationEntity, Long> {
    List<ObservationEntity> findTop200ByDeviceIdOrderByObservedAtDesc(Long deviceId);

    long deleteByObservedAtBefore(Instant cutoff);
}
