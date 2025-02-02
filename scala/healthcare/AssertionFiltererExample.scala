object AssertionFiltererExample extends App{

        implicit val session=spark

        val testDS=Seq(
        "Has a past history of gastroenteritis and stomach pain, however patient shows no stomach pain now. "+
        "We don't care about gastroenteritis here, but we do care about heart failure. "+
        "Test for asma, no asma.").toDF("text")
        val reader=new NegexDatasetReader

        val datasetPath="src/test/resources/rsAnnotations-1-120-random.txt"
        val trainDS=reader.readDataframe(datasetPath).withColumnRenamed("sentence","text").cache


        val documentAssembler=new DocumentAssembler()
        .setInputCol("text")
        .setOutputCol("document")

        val sentenceDetector=new SentenceDetector()
        .setInputCols(Array("document"))
        .setOutputCol("sentence")

        val tokenizer=new Tokenizer()
        .setInputCols(Array("sentence"))
        .setOutputCol("token")

        val POSTag=PerceptronModel
        .pretrained()
        .setInputCols("sentence","token")
        .setOutputCol("pos")

        val chunker=new Chunker()
        .setInputCols(Array("pos","sentence"))
        .setOutputCol("chunk")
        .setRegexParsers(Array("(<NN>)+"))

        val pubmed=WordEmbeddingsModel.pretrained("embeddings_clinical","en","clinical/models")
        .setInputCols("sentence","token")
        .setOutputCol("embeddings")
        .setCaseSensitive(false)

        val assertionStatus=AssertionDLModel.pretrained("assertion_dl","en","clinical/models")
        .setInputCols("sentence","chunk","embeddings")
        .setOutputCol("assertion")
        .setIncludeConfidence(true)


        val assertionFilterer=new AssertionFilterer()
        .setInputCols("sentence","chunk","assertion")
        .setOutputCol("filtered")
        .setCriteria("assertion")
        .setWhiteList("present")

        val stages=Array(documentAssembler,sentenceDetector,tokenizer,POSTag,chunker,pubmed,
        assertionStatus,assertionFilterer)
        val pipeline=new Pipeline()
        .setStages(stages)
        val model=pipeline.fit(trainDS)
        model.write.overwrite().save("./tmp_assertiondl_negex")
        val outDf=model.transform(testDS)


        outDf.selectExpr("filtered").show(truncate=false)
        outDf.selectExpr("assertion").show(truncate=false)
        }
