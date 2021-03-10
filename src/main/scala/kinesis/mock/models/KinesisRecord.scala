package kinesis.mock.models

import java.time.Instant

final case class KinesisRecord(
    sequenceNumber: SequenceNumber,
    approximateArrivalTimestamp: Instant,
    encryptionType: EncryptionType,
    partitionKey: String,
    data: Array[Byte]
)