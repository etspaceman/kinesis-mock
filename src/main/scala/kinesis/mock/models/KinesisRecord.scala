package kinesis.mock.models

import java.time.Instant

final case class KinesisRecord(
    sequenceNumber: String,
    approximateArrivalTimestamp: Instant,
    encryptionType: EncryptionType,
    partitionKey: String,
    data: Array[Byte]
)
