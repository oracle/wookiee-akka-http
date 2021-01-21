package akka.http.scaladsl.coding

object WSCoders {
  def Deflate(compressionLevel: Int = DeflateCompressor.DefaultCompressionLevel): Coder = new WSDeflate(compressionLevel)
}
