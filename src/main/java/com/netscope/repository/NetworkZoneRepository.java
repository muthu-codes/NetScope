package com.netscope.repository;

import com.netscope.entity.NetworkZoneEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface NetworkZoneRepository extends JpaRepository<NetworkZoneEntity, Long> {
    Optional<NetworkZoneEntity> findByCidr(String cidr);

    boolean existsByCidr(String cidr);

    List<NetworkZoneEntity> findByEnabledTrue();
}
