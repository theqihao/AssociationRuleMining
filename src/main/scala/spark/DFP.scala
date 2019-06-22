package spark
// import experiments.Runner
import org.apache.spark.SparkContext
import org.apache.spark.rdd.RDD
import org.apache.spark.sql.SparkSession
import scala.collection.mutable

import sequential.fpgrowth.{FPGrowth, FPNode, FPTree}
import sequential.Util
import sequential.Util.absoluteSupport

class DFP extends Serializable {
 	type Itemset = List[String]
  var executionTime: Long = 0
  val clusterMode:Boolean = false
  def execute(transactions: List[Itemset], minSupport: Double): List[Itemset] = {
    executionTime = 0
    val t0 = System.currentTimeMillis()
    val itemsets = findFrequentItemsets("", "", transactions, minSupport)
    if (executionTime == 0)
      executionTime = System.currentTimeMillis() - t0
    println(f"Elapsed time: ${executionTime / 1000d}%1.2f seconds. Class: ${getClass.getSimpleName}. Items: ${transactions.size}")
    itemsets
  }

  def execute(fileName: String, separator: String, minSupport: Double): List[Itemset] = {
    executionTime = 0
    val t0 = System.currentTimeMillis()
    val itemsets = findFrequentItemsets(fileName, separator, List.empty, minSupport)
    if (executionTime == 0)
      executionTime = System.currentTimeMillis() - t0
    println(f"Elapsed time: ${executionTime / 1000d}%1.2f seconds. Class: ${getClass.getSimpleName}.")
    itemsets
  }

  def executeByText(transactions: String, minSupport: Double): List[Itemset] = {
    execute(Util.parseTransactionsByText(transactions), minSupport)
  }

  def findFrequentItemsets(fileName: String, separator: String, transactions: List[Itemset], minSupport: Double): List[Itemset] = {
    var spark: SparkSession = null
    val appName = Util.appName
    if (!clusterMode) {
      spark = SparkSession.builder()
        .appName(appName)
        .master("local[4]")
        //.config("spark.eventLog.enabled", "true")
        .getOrCreate()
    }
    else {
      spark = SparkSession.builder().appName(appName).getOrCreate()
    }

    val sc = spark.sparkContext
    sc.setLogLevel("WARN")
    var t0 = System.currentTimeMillis()

    var transactionsRDD: RDD[Itemset] = null
    var support: Int = 0

    if (!fileName.isEmpty) {
      // Fetch transaction
      val file = List.fill(Util.replicateNTimes)(fileName).mkString(",")
      var fileRDD: RDD[String] = null
      if (Util.minPartitions == -1)
        fileRDD = sc.textFile(file)
      else
        fileRDD = sc.textFile(file, Util.minPartitions)

      transactionsRDD = fileRDD.filter(!_.trim.isEmpty)
        .map(_.split(separator + "+"))
        .map(l => l.map(_.trim).toList)

      if (Util.props.getProperty("fim.cache", "false").toBoolean) {
        transactionsRDD = transactionsRDD.cache()
        println("cached")
      }
      support = absoluteSupport(minSupport, transactionsRDD.count().toInt)
    }
    else {
      transactionsRDD = sc.parallelize(transactions)
      support = absoluteSupport(minSupport, transactions.size)
    }

    t0 = System.currentTimeMillis()
    // Generate singletons
		println("gen singleton starts")
    val singletonsRDD = transactionsRDD
      .flatMap(identity)
      .map(item => (item, 1))
      .reduceByKey(_ + _)
      .filter(_._2 >= support)
    println(s"Generate singletons in ${(System.currentTimeMillis() - t0) / 1000}s.")


		println("findFrequentItemsets starts")
    val frequentItemsets = findFrequentItemsets(transactionsRDD, singletonsRDD, support, spark, sc)

    executionTime = System.currentTimeMillis() - t0

    if (Util.props.getProperty("fim.unpersist", "false").toBoolean) {
      transactionsRDD.unpersist()
      println("unpersited")
    }

    if (Util.props.getProperty("fim.closeContext", "false").toBoolean) {
      spark.sparkContext.stop()
      println("stopped")
    }

    frequentItemsets
  }

  def findFrequentItemsets(transactions: RDD[Itemset], singletons: RDD[(String, Int)], minSupport: Int,
                                    spark: SparkSession, sc: SparkContext): List[Itemset] = {
    // Generate singletons
    val sortedSingletons = singletons.collect.map(t => t._1)

    if (sortedSingletons.nonEmpty) {
      transactions
        .map(t => pruneAndSort(t, sortedSingletons))
        .flatMap(buildConditionalPatternsBase)
        .groupByKey(sortedSingletons.length)
        .flatMap(t => minePatternFragment(t._1, t._2.toList, minSupport))
        .collect().toList ++ sortedSingletons.map(List(_))
    }
    else List.empty[Itemset]
  }

  def minePatternFragment(prefix: String, conditionalPatterns: List[Itemset], minSupport: Int) = {
    val fpGrowth = new FPGrowth
    val singletons = mutable.LinkedHashMap(fpGrowth.findSingletons(conditionalPatterns, minSupport).map(i => i -> Option.empty[FPNode]): _*)
    val condFPTree = new FPTree(conditionalPatterns.map((_, 1)), minSupport, singletons)
    val prefixes = fpGrowth.generatePrefixes(List(prefix), singletons.keySet)

    val t0 = System.currentTimeMillis()
    val r = prefixes.flatMap(p => fpGrowth.findFrequentItemsets(condFPTree, p, minSupport))
    println(s"Searched fp-tree in ${(System.currentTimeMillis() - t0) / 1000}s.")
    r
  }

  /**
    * in: f,c,a,m,p
    * out:
    * p -> f,c,a,m
    * m -> f,c,a
    * a -> f,c
    * c -> f
    */
  def buildConditionalPatternsBase(transaction: Itemset): List[(String, Itemset)] = {
    (1 until transaction.size).map(i => (transaction(i), transaction.slice(0, i))).toList
  }

  def pruneAndSort(transaction: Itemset, singletons: Seq[String]): Itemset = {
    transaction
      .filter(i => singletons.contains(i))
      .sortWith((a, b) => singletons.indexOf(a) < singletons.indexOf(b)) // todo: anyway to speedup initial sorting?
  }

}
