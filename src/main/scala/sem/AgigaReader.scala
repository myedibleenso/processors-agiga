package sem

import java.io._
import com.typesafe.config.ConfigFactory
import com.typesafe.scalalogging.LazyLogging
import edu.jhu.agiga.{AgigaSentence, StreamingDocumentReader, AgigaPrefs}
import scala.collection.JavaConverters._
import edu.arizona.sista.processors.{DependencyMap, Document, Sentence}
import edu.arizona.sista.struct.DirectedGraph
import scala.collection.parallel.ForkJoinTaskSupport



object AgigaReader extends App with LazyLogging {

  val config = ConfigFactory.load()
  val inDir = new File(config.getString("inputDir"))
  val outDir = new File(config.getString("outDir"))
  val view = config.getString("view")
  val nthreads = config.getInt("nthreads")

  // For working with the agiga deps
  abstract class DependencyRepresentation
  case class Root(i:Int) extends DependencyRepresentation
  case class Edge(headIndex:Int, depIndex:Int, relation: String) extends DependencyRepresentation

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

  def mkOutput(f: File, outDir: File, view: String):Unit = {

    // create a Processors Document
    val doc = agigaDocToDocument(f.getAbsolutePath)

    // get output representation
    val output = view.toLowerCase match {
      case words if words.startsWith("word") =>
        doc.sentences.map(_.words.mkString(" ")).mkString("\n")
      case tags if tags.startsWith("tag") =>
        doc.sentences.map(_.tags.get.mkString(" ")).mkString("\n")
      case lemma if lemma.startsWith("lemma") =>
        doc.sentences.map(_.lemmas.get.mkString(" ")).mkString("\n")
      case entities if entities.startsWith("entities") || entities.startsWith("ner") =>
        doc.sentences.map(_.entities.get.mkString(" ")).mkString("\n")
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

  // filter out any non- *.xml.gz files in the directory
  // and parallelize the Array of valid Files...
  val files = inDir
    .listFiles
    .filter(_.getName.endsWith(".xml.gz"))
    .par

  // limit parallelization
  files.tasksupport = new ForkJoinTaskSupport(new scala.concurrent.forkjoin.ForkJoinPool(nthreads))

  logger.info(s"View: $view")
  logger.info(s"Input: $inDir")
  logger.info(s"Output folder: $outDir")
  logger.info(s"Threads to use: $nthreads")

  // process files
  files.foreach(f => mkOutput(f, outDir, view))
}