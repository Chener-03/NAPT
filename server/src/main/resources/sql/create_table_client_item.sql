CREATE TABLE client_item (
     id INT AUTO_INCREMENT PRIMARY KEY,
     client_uid VARCHAR(255),
     client_addr VARCHAR(255),
     server_port INT,
     flow BIGINT,
     max_flow_limit BIGINT,
     speed_limit BIGINT,
     create_time DATETIME,
     remark VARCHAR(255)
);
