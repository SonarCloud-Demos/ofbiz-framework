CREATE USER platform_owner PASSWORD 'local-platform-only';
CREATE DATABASE platform OWNER platform_owner;
CREATE USER isolation_probe PASSWORD 'local-isolation-only';
CREATE DATABASE isolation_probe OWNER isolation_probe;
REVOKE CONNECT ON DATABASE platform FROM PUBLIC;
REVOKE CONNECT ON DATABASE isolation_probe FROM PUBLIC;
GRANT CONNECT ON DATABASE platform TO platform_owner;
GRANT CONNECT ON DATABASE isolation_probe TO isolation_probe;
