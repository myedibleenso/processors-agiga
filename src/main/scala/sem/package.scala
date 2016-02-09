import java.io.{FileInputStream, FileOutputStream, File}

import org.apache.commons.compress.archivers.ArchiveStreamFactory
import org.apache.commons.compress.archivers.zip.ZipArchiveEntry
import org.apache.commons.compress.utils.IOUtils

package object sem {

  def compress(outFile: File):Unit = {

    val outName = outFile.getName

    /// archive container
    val container = new FileOutputStream(new File(s"${outFile.getAbsolutePath}.zip"))

    // create archive stream for attaching files
    val archive = new ArchiveStreamFactory().createArchiveOutputStream(ArchiveStreamFactory.ZIP, container)

    // write header info
    archive.putArchiveEntry(new ZipArchiveEntry(outName))
    // Copy input file
    IOUtils.copy(new FileInputStream(outFile), archive)
    // close archive
    archive.closeArchiveEntry()
    archive.finish()
    // close container
    container.close()
  }
}
