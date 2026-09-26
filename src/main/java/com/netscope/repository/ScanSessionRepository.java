package com.netscope.repository;

import com.netscope.entity.ScanSessionEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ScanSessionRepository extends JpaRepository<ScanSessionEntity, Long> {
}
