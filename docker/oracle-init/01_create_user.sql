-- ============================================================
-- Oracle Database Initialization Script
-- Run automatically by Oracle Docker entrypoint on first start
-- Creates the application user and grants necessary privileges
-- ============================================================

-- Connect to the pluggable database
ALTER SESSION SET CONTAINER = FREEPDB1;

-- Create the application user
CREATE USER caching_user IDENTIFIED BY Caching123
    DEFAULT TABLESPACE users
    TEMPORARY TABLESPACE temp
    QUOTA UNLIMITED ON users;

-- Grant required privileges
GRANT CONNECT, RESOURCE TO caching_user;
GRANT CREATE SESSION TO caching_user;
GRANT CREATE TABLE TO caching_user;
GRANT CREATE SEQUENCE TO caching_user;
GRANT CREATE INDEX TO caching_user;

-- Grant Flyway schema history table permissions
GRANT CREATE TABLE TO caching_user;
GRANT SELECT ON dba_tables TO caching_user;

COMMIT;
