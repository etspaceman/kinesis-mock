package kinesis.mock.models

final case class Stream(
    name: String,
    data: Map[Shard, List[KinesisRecord]],
    tags: Map[String, String],
    status: StreamStatus
)
