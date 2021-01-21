package akka.http.scaladsl.coding

import akka.http.scaladsl.model.HttpMessage

class WSDeflate (compressionLevel: Int) extends Deflate(compressionLevel, (_ : HttpMessage) => false) {

  import com.sun.xml.internal.ws.server.sei.MessageFiller

  override def newCompressor: DeflateCompressor = new WSDeflateCompressor(compressionLevel);
}
