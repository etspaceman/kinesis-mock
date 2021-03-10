package kinesis.mock.models

final case class SequenceNumber private (
    sequenceNumber: String,
    subSequenceNumber: Long
)

object SequenceNumber {
    val LATEST = SequenceNumber("LATEST", 0L)
    val SHARD_END = SequenceNumber("SHARD_END", 0L)
    val TRIM_HORIZON = SequenceNumber("TRIM_HORIZON", 0L)
    val AT_TIMESTAMP = SequenceNumber("AT_TIMESTAMP", 0L)
}
