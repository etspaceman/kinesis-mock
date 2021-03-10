package kinesis.mock.models

final case class Shard(
    id: String,
    startingHashKey: BigInt,
    endingHashKey: BigInt
)
