-- Reference schema (MySQL 8). Spring Data JPA creates/updates these tables automatically
-- (spring.jpa.hibernate.ddl-auto=update), so running this file is OPTIONAL.
CREATE DATABASE IF NOT EXISTS netscope CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
USE netscope;

CREATE TABLE IF NOT EXISTS device (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  ip VARCHAR(45) NOT NULL,
  mac VARCHAR(17),
  hostname VARCHAR(255),
  vendor VARCHAR(120),
  device_type VARCHAR(30),
  state VARCHAR(20),
  subnet VARCHAR(24),
  first_seen DATETIME(6),
  last_seen DATETIME(6),
  last_latency_ms DOUBLE,
  UNIQUE KEY idx_device_ip (ip)
);

CREATE TABLE IF NOT EXISTS scan_session (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  scan_mode VARCHAR(12),
  started_at DATETIME(6),
  finished_at DATETIME(6),
  status VARCHAR(12),
  targets_probed INT,
  devices_reachable INT,
  devices_total INT,
  scopes VARCHAR(500),
  error VARCHAR(500)
);

CREATE TABLE IF NOT EXISTS observation (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  device_id BIGINT NOT NULL,
  session_id BIGINT,
  observed_at DATETIME(6) NOT NULL,
  reachable BIT NOT NULL,
  latency_ms DOUBLE,
  ttl INT,
  KEY idx_obs_device_time (device_id, observed_at),
  KEY idx_obs_time (observed_at)
);

CREATE TABLE IF NOT EXISTS network_event (
  id BIGINT PRIMARY KEY,
  occurred_at DATETIME(6) NOT NULL,
  event_type VARCHAR(40) NOT NULL,
  severity VARCHAR(12),
  ip VARCHAR(45),
  mac VARCHAR(17),
  hostname VARCHAR(255),
  message VARCHAR(700),
  KEY idx_event_time (occurred_at)
);

CREATE TABLE IF NOT EXISTS network_interface (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  name VARCHAR(200) NOT NULL UNIQUE,
  display_name VARCHAR(255),
  interface_type VARCHAR(20),
  mac VARCHAR(17),
  ipv4 VARCHAR(100),
  is_up BIT,
  last_seen DATETIME(6)
);

CREATE TABLE IF NOT EXISTS topology_node (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  session_id BIGINT,
  captured_at DATETIME(6),
  node_key VARCHAR(80),
  label VARCHAR(255),
  node_type VARCHAR(30),
  ip VARCHAR(45),
  mac VARCHAR(17),
  state VARCHAR(20),
  KEY idx_tn_captured (captured_at)
);

CREATE TABLE IF NOT EXISTS topology_edge (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  session_id BIGINT,
  captured_at DATETIME(6),
  source_key VARCHAR(80),
  target_key VARCHAR(80),
  relation VARCHAR(40),
  evidence VARCHAR(30),
  confidence VARCHAR(12),
  KEY idx_te_captured (captured_at)
);

-- ---------------------------------------------------------------------------
-- Multi-subnet campus network zones (added on top of the original single-subnet schema above).
-- ---------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS network_zone (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  name VARCHAR(80) NOT NULL,
  cidr VARCHAR(24) NOT NULL,
  description VARCHAR(300),
  zone_type VARCHAR(16) NOT NULL,          -- LOCAL, ROUTED, SERVER, MANAGEMENT, CUSTOM
  enabled BIT NOT NULL DEFAULT 1,
  authorized BIT NOT NULL DEFAULT 0,
  authorized_by VARCHAR(120),
  gateway VARCHAR(45),
  methods VARCHAR(20) DEFAULT 'ICMP,TCP',
  tcp_ports VARCHAR(60) DEFAULT '80,443,22',
  use_nmap BIT NOT NULL DEFAULT 1,
  max_concurrency INT NOT NULL DEFAULT 20,
  timeout_ms INT NOT NULL DEFAULT 1000,
  max_devices INT NOT NULL DEFAULT 512,
  interval_seconds INT NOT NULL DEFAULT 60,
  failure_threshold INT NOT NULL DEFAULT 3,
  status VARCHAR(16) NOT NULL DEFAULT 'IDLE',   -- IDLE, DISCOVERING, MONITORING, DEGRADED, UNREACHABLE, ERROR
  last_scan_at DATETIME(6),
  created_at DATETIME(6) NOT NULL,
  updated_at DATETIME(6) NOT NULL,
  UNIQUE KEY idx_zone_cidr (cidr)
);

CREATE TABLE IF NOT EXISTS zone_device (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  zone_id BIGINT NOT NULL,
  ip VARCHAR(45) NOT NULL,
  mac VARCHAR(17),                          -- usually NULL for routed zones - MAC does not cross a router
  hostname VARCHAR(255),
  state VARCHAR(12) NOT NULL DEFAULT 'UNKNOWN',   -- UP, DEGRADED, DOWN, UNKNOWN
  discovery_method VARCHAR(12),             -- ICMP, TCP
  latency_ms DOUBLE,
  packet_loss_percent DOUBLE,
  consecutive_failures INT NOT NULL DEFAULT 0,
  open_ports VARCHAR(200),
  first_seen DATETIME(6),
  last_seen DATETIME(6),
  KEY idx_zone_device_zone (zone_id),
  KEY idx_zone_device_ip (ip)
);
