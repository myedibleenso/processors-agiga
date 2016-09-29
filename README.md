# `processors-agiga`

## What is it?

Just a way to convert [`agiga`](https://github.com/mgormley/agiga) data into a `processors` `Document`.

Why spend the time and resources parsing and annotating over 183 million sentences when it has already been done?

## Dependencies

1. [Java 8](http://www.oracle.com/technetwork/java/javase/overview/java8-2100321.html)
2. [sbt](http://www.scala-sbt.org)
3. [A copy of the Annotated English Gigaword](https://catalog.ldc.upenn.edu/LDC2012T21)

## Reading an annotated English Gigaword `xml` file

```scala
import org.clulab.agiga

// build a processors.Document
val doc = agiga.toDocument("path/to/agiga/xml/ltw_eng_200705.xml.gz")
```

## Running `AgigaReader`

### Example 1: dump a lemmatized form of the English Gigaword

Everything is configured in the [`application.conf`](src/main/resources/scala/application.conf) file.

1. Change the `view` property to "lemmas"  

2. Change the `inputDir` property to wherever your copy of `agiga` is nestled on your disk  

3. Change the `outputDir` property to wherever you want your compressed of the lemmatized English Gigaword to be written  
4. (Optional) Change the `nthreads` property to the maximum number of threads you prefer to use for parallelization.

All that's left is to run `AgigaReader`:

```scala
sbt "runMain sem.AgigaReader"
```

### Options for "view"
| Value         	| Description 	|
|---------------	|-------------	|
| "words"       	| word form of each token            	|
| "lemmas"      	| lemma form of each token            	|
| "tags"        	| PoS tag of each token             	|
| "entities"    	| NE labels of each token     	|
| "deps"        	| `<word form of head>_<relation>_<word form of dependent>`           	|
| "lemma-deps"  	| `<lemmatized head>_<relation>_<lemmatized dependent>`         	|
| "tag-deps"    	| `<pos tag of head>_<relation>_<pos tag of dependent>`         	|
| "entity-deps" 	| `<NE label of head>_<relation>_<NE label of dependent>`           	|
|               	|             	|


## TODO

- Add output options for dependencies using the DFS ordering described in ["Higher-order Lexical Semantic Models for Non-factoid Answer Reranking"](https://tacl2013.cs.columbia.edu/ojs/index.php/tacl/article/viewFile/550/122)


## References

1. [Annotated Gigaword](https://github.com/mgormley/agiga)
