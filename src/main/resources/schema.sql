DROP TABLE IF exists CAMEL_MESSAGEPROCESSED;
CREATE TABLE IF NOT EXISTS CAMEL_FILEPROCESSED  (processorName VARCHAR(255), digest VARCHAR(255), fileName varchar(255),createdAt TIMESTAMP);