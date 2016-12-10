package org.clulab

import java.io.{File, FileInputStream, FileOutputStream}
import edu.jhu.agiga.{AgigaPrefs, AgigaSentence, StreamingDocumentReader}
import org.apache.commons.compress.archivers.ArchiveStreamFactory
import org.apache.commons.compress.archivers.zip.ZipArchiveEntry
import org.apache.commons.compress.utils.IOUtils
import org.clulab.processors.{Document, Sentence}
import org.clulab.struct.{Edge, DirectedGraph, GraphMap}
import scala.collection.JavaConverters._


package object agiga {

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

  /**
    * Extract Stanford collapsed dependencies from an Agiga sentence
    */
  def mkDependencies(s: AgigaSentence): DirectedGraph[String] = {
    // collapsed dependencies...
    val collapsedDeps = s.getColCcprocDeps.asScala

    val graphComponents: List[Any] =
      for {
        c <- collapsedDeps.toList
        // component indices for edge construction
        depIndex = c.getDepIdx
        headIndex = c.getGovIdx
        // relation
        rel = c.getType
      } yield {
        headIndex match {
          case -1 => depIndex
          case _ => Edge[String](source = headIndex, destination = depIndex, relation = rel)
        }
      }

    val edges: List[Edge[String]] = graphComponents
      .collect { case e: Edge[_] => Edge[String]( source = e.source, destination = e.destination, relation = e.relation.toString) }

    val roots: Set[Int] = graphComponents
      .collect { case r: Int => r }.toSet

    DirectedGraph[String](edges, roots)
  }

  /** Converts agiga annotations to a Processors Document sequence */
  def toDocuments(filename: String): Seq[Document] = {
    // Setup Gigaword API Preferences
    val prefs = new AgigaPrefs()
    // label for agiga dependency type
    prefs.setAll(true)
    prefs.setWord(true)
    // Retrieve all gigaword documents contained within a given file
    val reader = new StreamingDocumentReader(filename, prefs)

    val docs: Iterator[Document] = reader.iterator().asScala.map { agigaDoc =>

      val sentences = for {
        s <- agigaDoc.getSents.asScala
        tokens = s.getTokens.asScala
        // words
        words = tokens.map(_.getWord)
        // lemmas
        lemmas = tokens.map(_.getLemma)
        // pos tags
        posTags = tokens.map(_.getPosTag)
        // ner labels
        nerLabels = tokens.map(_.getNerTag)
        // offsets
        startOffsets = tokens.map(_.getCharOffBegin)
        endOffsets = tokens.map(_.getCharOffEnd)
        deps = mkDependencies(s)
      } yield {

        Sentence(
          /** Actual tokens in this sentence */
          words.toArray,
          /** Start character offsets for the words; start at 0 */
          startOffsets.toArray,
          /** End character offsets for the words; start at 0 */
          endOffsets.toArray,
          /** POS tags for words (OPTION) */
          tags = Some(posTags.toArray),
          /** Lemmas (OPTION) */
          lemmas = Some(lemmas.toArray),
          /** NE labels (OPTION) */
          entities = Some(nerLabels.toArray),
          /** Normalized values of named/numeric entities, such as dates (OPTION) */
          norms = None,
          /** Shallow parsing labels (OPTION) */
          chunks = None,
          /** Constituent tree of this sentence; includes head words Option[Tree[String]] */
          tree = None,
          /** *Dependencies */
          deps = GraphMap(Map(GraphMap.STANFORD_COLLAPSED -> deps))
        )
    }
    val doc = new Document(sentences.toArray)
    // store the doc's ID
    doc.id = Some(agigaDoc.getDocId)
    doc
    }
    docs.toVector
  }

  /**
    * Generate a string representation of a sentence's dependencies
    */
  def depsToString(deps: DirectedGraph[String], tokens: Array[String]):String = {
    // include roots in deps representation
    val rootStr = deps.roots.map(r => s"??_root_${tokens(r)}").mkString(" ")
    // everything but roots...
    val depStr = deps.allEdges.map(triple => s"${tokens(triple._1)}_${triple._3}_${tokens(triple._2)}").mkString(" ")
    s"$depStr $rootStr"
  }
}
