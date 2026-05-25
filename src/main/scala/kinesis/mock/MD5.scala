package kinesis.mock

/** MessageDigest is incredibly heavy-weight and has no linkages in ScalaJS. We
  * could use the FS2 MD5 hashing, but that spins up MessageDigest with each
  * pass, which leads to really bad performance. So I created this to deal with
  * MD5 hashing in a performant way.
  *
  * See https://rosettacode.org/wiki/MD5/Implementation#Java
  */
@SuppressWarnings(Array("scalafix:DisableSyntax.var"))
object MD5:
  private val initA: Int = 0x67452301
  private val initB: Int = 0xefcdab89L.toInt
  private val initC: Int = 0x98badcfeL.toInt
  private val initD: Int = 0x10325476
  private val shiftAmts: Array[Int] =
    Array(7, 12, 17, 22, 5, 9, 14, 20, 4, 11, 16, 23, 6, 10, 15, 21)

  private val tableT: Array[Int] = Array.ofDim(64)
  for i <- 0 until 64 do
    tableT(i) = ((1L << 32) * Math.abs(Math.sin((i + 1).toDouble))).toLong.toInt

  def compute(message: Array[Byte]): Array[Byte] =
    val messageLenBytes = message.length
    val numBlocks = ((messageLenBytes + 8) >>> 6) + 1
    val totalLen = numBlocks << 6
    val paddingBytes: Array[Byte] = Array.ofDim(totalLen - messageLenBytes)
    paddingBytes(0) = 0x80.toByte

    var messageLenBits = messageLenBytes.toLong << 3
    for i <- 0 until 8 do
      paddingBytes(paddingBytes.length - 8 + i) = messageLenBits.toByte
      messageLenBits = messageLenBits >>> 8
    var a = initA
    var b = initB
    var c = initC
    var d = initD
    val buffer: Array[Int] = Array.ofDim(16)

    for i <- 0 until numBlocks do
      var index = i << 6

      for j <- 0 until 64 do
        buffer(j >>> 2) = ((if index < messageLenBytes then message(index)
                            else
                              paddingBytes(
                                index - messageLenBytes
                              )
        ) << 24).toInt | (buffer(j >>> 2) >>> 8)
        index = index + 1
      val originalA = a
      val originalB = b
      val originalC = c
      val originalD = d

      for j <- 0 until 64 do
        val div16 = j >>> 4
        var f = 0
        var bufferIndex = j
        div16 match
          case 0 =>
            f = (b & c) | (~b & d)
          case 1 =>
            f = (b & d) | (c & ~d)
            bufferIndex = (bufferIndex * 5 + 1) & 0x0f
          case 2 =>
            f = b ^ c ^ d
            bufferIndex = (bufferIndex * 3 + 5) & 0x0f
          case 3 =>
            f = c ^ (b | ~d)
            bufferIndex = (bufferIndex * 7) & 0x0f
          case _ => ()
        val temp = b + Integer.rotateLeft(
          a + f + buffer(bufferIndex) + tableT(j),
          shiftAmts((div16 << 2) | (j & 3))
        )
        a = d
        d = c
        c = b
        b = temp

      a = a + originalA
      b = b + originalB
      c = c + originalC
      d = d + originalD

    val md5: Array[Byte] = Array.ofDim(16)
    var count = 0

    for i <- 0 until 4 do
      var n = i match
        case 0 => a
        case 1 => b
        case 2 => c
        case _ => d

      for _ <- 0 until 4 do
        md5(count) = n.toByte
        count = count + 1
        n = n >>> 8

    md5
