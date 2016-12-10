package org.clulab.agiga

import java.io._
import com.typesafe.config.ConfigFactory
import com.typesafe.scalalogging.LazyLogging
import scala.collection.parallel.ForkJoinTaskSupport


object AgigaReader extends App with LazyLogging {

  val config = ConfigFactory.load()
  val inDir = new File(config.getString("inputDir"))
  val outDir = new File(config.getString("outDir"))
  val view = config.getString("view")
  val nthreads = config.getInt("nthreads")

  def mkOutput(f: File, outDir: File, view: String): Unit = {

    // create a Processors Document
    val docs = toDocuments(f.getAbsolutePath)

    for (doc <- docs) {
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
      //val fName = f.getName.replace(".xml.gz", "")
      val outFile = new File(outDir, s"${doc.id.get}-$view.txt")
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