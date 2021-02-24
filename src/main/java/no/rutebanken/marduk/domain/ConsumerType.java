package no.rutebanken.marduk.domain;

import com.amazonaws.services.kafka.model.S3;

public enum ConsumerType {
    FTP,
    SFTP,
    REST,
    RESTCONCERTO,
    S3;
}
