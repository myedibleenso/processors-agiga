package sem

import java.io._
import com.typesafe.config.ConfigFactory
import com.typesafe.scalalogging.LazyLogging
import edu.jhu.agiga.{AgigaSentence, StreamingDocumentReader, AgigaPrefs}
import scala.collection.JavaConverters._
import edu.arizona.sista.processors.{DependencyMap, Document, Sentence}
import edu.arizona.sista.struct.DirectedGraph
import scala.collection.parallel.ForkJoinTaskSupport

// For working with the agiga deps
abstract class DependencyRepresentation
case class Root(i:Int) extends DependencyRepresentation
case class Edge(headIndex:Int, depIndex:Int, relation: String) extends DependencyRepresentation

object AgigaReader extends App with LazyLogging {

  val config = ConfigFactory.load()
  val inDir = new File(config.getString("inputDir"))
  val outDir = new File(config.getString("outDir"))
  val view = config.getString("view")
  val nthreads = config.getInt("nthreads")

  def mkDependencies(s: AgigaSentence):DirectedGraph[String] = {
    // collapsed dependencies...
    val collapsedDeps = s.getColCcprocDeps.asScala

    val graphComponents =
      for {
        c <- collapsedDeps
        // component indices for edge construction
        depIndex = c.getDepIdx
        headIndex = c.getGovIdx
        // relation
        rel = c.getType
    } yield {
        headIndex match {
          case -1 => Root(depIndex)
          case _ => Edge(headIndex, depIndex, rel)
        }
      }

    val edges:List[(Int, Int, String)] = graphComponents
      .collect { case e: Edge => e }
      .map( e => (e.headIndex, e.depIndex, e.relation))
      .toList

    val roots:Set[Int] = graphComponents.collect { case r: Root => r }.map( r => r.i).toSet

    new DirectedGraph[String](edges, roots)
  }

  /** Converts agiga annotations to a Processors Document
    * and then generates a text representation of that Document using a specified "view"
    * view: words, lemmas, tags, entities, etc
    */
  def agigaDocToDocument(filename: String): Document = {
    // Setup Gigaword API Preferences
    val prefs = new AgigaPrefs()
    // label for agiga dependency type
    prefs.setAll(true)
    prefs.setWord(true)
    // Retrieve all gigaword documents contained within a given file
    val reader = new StreamingDocumentReader(filename, prefs)
    val sentences = for {
      agigaDoc <- reader.iterator().asScala
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

        val s = new Sentence(
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
          syntacticTree = None,
          /** *Dependencies */
          dependenciesByType = new DependencyMap)
        // 1 for collapsed Stanford dependencies
        s.setDependencies(1, deps)
        s
      }
    new Document(sentences.toArray)
  }

  /**
   * Generate a string representation of a sentence's dependencies
   */
  def depsToString(deps:DirectedGraph[String], tokens:Array[String]):String = {
    deps.allEdges.map(triple => s"${tokens(triple._1)}_${triple._3}_${tokens(triple._2)}").mkString(" ")
  }

  def mkOutput(f: File, outDir: File, view: String):Unit = {

    // create a Processors Document
    val doc = agigaDocToDocument(f.getAbsolutePath)

    // get output representation
    val output = view.toLowerCase match {
      case "words" =>
        doc.sentences.map(_.words.mkString(" ")).mkString("\n")
      case "tags" =>
        doc.sentences.map(_.tags.get.mkString(" ")).mkString("\n")
      case "lemmas" =>
        doc.sentences.map(_.lemmas.get.mkString(" ")).mkString("\n")
      case entities if entities == "entities" || entities == "ner" =>
        doc.sentences.map(_.entities.get.mkString(" ")).mkString("\n")
      // these are unordered
      case "lemma-deps" =>
        doc.sentences.map { s =>
          val deps = s.dependencies.get
          val lemmas = s.lemmas.get
          depsToString(deps, lemmas)
        }.mkString("\n")
      case "tag-deps" =>
        doc.sentences.map { s =>
          val deps = s.dependencies.get
          val tags = s.tags.get
          depsToString(deps, tags)
        }.mkString("\n")
      case "entity-deps" =>
        doc.sentences.map { s =>
          val deps = s.dependencies.get
          val entities = s.entities.get
          depsToString(deps, entities)
        }.mkString("\n")
      case "dep" =>
        doc.sentences.map { s =>
          val deps = s.dependencies.get
          val words = s.words
          depsToString(deps, words)
        }.mkString("\n")
    }

    // prepare output file
    val fName = f.getName.replace(".xml.gz", "")
    val outFile = new File(s"$outDir/$fName-$view.txt")
    val pw = new PrintWriter(outFile)
    // write processed text to file
    pw.write(output)
    pw.close()
    // compress file
    compress(outFile)
    // delete uncompress out file
    outFile.delete

    logger.info(s"Successfully processed ${f.getName}")
  }

  // create dir if it doesn't exist...
  outDir.mkdirs()

  logger.info(s"Input: $inDir")
  logger.info(s"Output folder: $outDir")
  logger.info(s"View: $view")
  
  val files = inDir.listFiles
    // filter out any non- *.xml.gz files in the directory
    .filter(_.getName.endsWith(".xml.gz"))
    // and parallelize the Array of valid Files...
    .par

  logger.info(s"Files to process: ${files.size}")

  // limit parallelization
  files.tasksupport = new ForkJoinTaskSupport(new scala.concurrent.forkjoin.ForkJoinPool(nthreads))

  logger.info(s"Threads to use: $nthreads")

  // process files
  files.foreach(f => mkOutput(f, outDir, view))
}