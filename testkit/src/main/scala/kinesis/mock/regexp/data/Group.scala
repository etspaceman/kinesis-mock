package kinesis.mock.regexp.data

sealed abstract class Group[A]:

  def compliment: Group[A]

  def intersect(that: Group[A]): Group[A]

  def ++(that: Group[A]): Group[A]

  def --(that: Group[A]): Group[A]

object Group:

  final case class Inclusion[A](values: Set[A]) extends Group[A]:

    override lazy val compliment: Group[A] = Exclusion(values)

    override def intersect(that: Group[A]): Group[A] =
      that match
        case Inclusion(other) => Inclusion(values.intersect(other))
        case Exclusion(other) => Inclusion(values -- other)

    override def ++(that: Group[A]): Group[A] =
      that match
        case Inclusion(other) => Inclusion(values ++ other)
        case Exclusion(other) => Exclusion(other -- values)

    override def --(that: Group[A]): Group[A] =
      that match
        case Inclusion(other) => Inclusion(values -- other)
        case Exclusion(other) => Inclusion(values.intersect(other))

  final case class Exclusion[A](values: Set[A]) extends Group[A]:

    override lazy val compliment: Group[A] = Inclusion(values)

    override def intersect(that: Group[A]): Group[A] =
      that match
        case Inclusion(other) => Inclusion(values -- other)
        case Exclusion(other) => Exclusion(values ++ other)

    override def ++(that: Group[A]): Group[A] =
      that match
        case Inclusion(other) => Exclusion(values -- other)
        case Exclusion(other) => Exclusion(values.intersect(other))

    override def --(that: Group[A]): Group[A] =
      that match
        case Exclusion(other) => Exclusion(other -- values)
        case Inclusion(other) => Exclusion(values ++ other)
