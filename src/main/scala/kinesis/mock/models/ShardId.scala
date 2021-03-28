package kinesis.mock.models

final case class ShardId(shardId: String, index: Int)

object ShardId {
  def create(index: Int): ShardId =
    ShardId("shardId-" + s"00000000000$index".takeRight(12), index)
  implicit val shardIdOrdering: Ordering[ShardId] = (x: ShardId, y: ShardId) =>
    x.index.compare(y.index)
}
