package akka.http.scaladsl.coding

import java.util.zip.Deflater

class WSDeflateCompressor (compressionLevel: Int) extends DeflateCompressor(compressionLevel) {
  override protected lazy val deflater = new Deflater(compressionLevel, true)
}
