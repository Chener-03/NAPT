CREATE TABLE client_item (
     id INT AUTO_INCREMENT PRIMARY KEY,
     clientUid VARCHAR(255),
     clientAddr VARCHAR(255),
     serverPort INT,
     flow BIGINT,
     maxFlowLimit BIGINT,
     speedLimit BIGINT,
     createTime DATETIME
);
