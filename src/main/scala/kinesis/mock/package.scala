package kinesis

package object mock {
  type Response[A] = Either[KinesisMockException, A]
}
