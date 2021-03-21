package kinesis.mock.models

final case class ShardId(shardId: String, index: Int)

object ShardId {
  def create(index: Int): ShardId =
    ShardId("shardId-" + s"00000000000$index".takeRight(12), index)
  implicit val shardIdOrdering: Ordering[ShardId] = new Ordering[ShardId] {
    override def compare(x: ShardId, y: ShardId): Int = x.index.compare(y.index)
  }
}
