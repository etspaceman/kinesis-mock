package kinesis.mock.models

final case class Shard(
    id: String,
    hashKeyRange: HashKeyRange,
    sequenceNumberRange: SequenceNumberRange
)
