package com.netscope.repository;

import com.netscope.entity.ZoneDeviceEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ZoneDeviceRepository extends JpaRepository<ZoneDeviceEntity, Long> {
    List<ZoneDeviceEntity> findByZoneId(Long zoneId);

    Optional<ZoneDeviceEntity> findByZoneIdAndIp(Long zoneId, String ip);

    void deleteByZoneId(Long zoneId);

    long countByZoneId(Long zoneId);
}
