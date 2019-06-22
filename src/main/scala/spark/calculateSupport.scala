package spark
import scala.collection.mutable
import org.apache.spark.SparkContext
import org.apache.spark.rdd.RDD
import org.apache.spark.sql.SparkSession
import sequential.Util
import java.io._
import scala.io._
import scala.math._
class genRules extends Serializable {
	type Itemset = List[String]
  val clusterMode:Boolean = false
	val separator = " "
	def toInteger(bool : Boolean): Int = {
		var out = 0
		if (bool == true)
			out = 1
		out
	}
	// No Spark version
	def calculateSupport(transactions: List[Itemset], file_name: String): List[(Itemset,Int)] = {
		val dataset = Source.fromFile(file_name)
		var trans = transactions.map(item=>(item, 0))
		var i = 0
		dataset.getLines().foreach(item=>{
			trans = trans.map(trans_items=>
									(trans_items._1,
									trans_items._2 + toInteger(trans_items._1.toSet.subsetOf(item.split(" ").toSet))))
			i = i + 1
			if (i % 100 == 0)
				println(i.toString + "'th Item is cal")
			}
		)
		trans
	}
    def prune(transaction: Itemset, singletons: Seq[String]): Itemset = {
      transaction
        .filter(i => singletons.contains(i))
        .sorted
        // .sortWith((a, b) => singletons.indexOf(a) < singletons.indexOf(b))
    }
	// Spark version
	def calSupp(freqItemset: List[Itemset], fileName: String): List[(Itemset,Int)] = {
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
      val t0 = System.currentTimeMillis()
      val singletons = freqItemset.filter(_.size == 1).map(t=>t.apply(0)).sorted.toSeq
      var freqRDD = sc.parallelize(freqItemset.map(t=>t.sorted), Util.minPartitions)
      val trans = Source.fromFile(fileName).getLines().map(t=>prune(t.split(" ").toList, singletons)).filter(_.size > 0).toList
      println("pre-process done")
      freqRDD.map(item=>(item, findFreq(trans, item))).collect().toList
	}

	var iter = 0
    def subset(trans:Itemset,item: Itemset):Boolean = {
      var idx = 0
      for (i<-0 to item.size-1) {
        idx = trans.indexOf(item.apply(i), idx)
        if (idx == -1) {
          return false
        }
      }
      return true
    }
    var total = 0
	def findFreq(trans:List[Itemset], item:Itemset):Int = {
		iter = iter + 1
        val item_set = item.toSet
		if (iter % 100 == 0)
			println(iter.toString + "'th freqitemsets' support has been computed")
		trans.filter(trans_item=>subset(trans_item, item)).size
	}
	def remove(trans:Itemset, item:String):Itemset = {
		var res : Itemset = Nil
		trans.foreach(t=>{
			if (t != item)
				res = res :+ (t)
		})
		res
	}
   /* 
	def genRules(freqItemset: List[Itemset], fileName: String): List[(Itemset,Itemset,Double)] = {
		var freq_supp = calSupp(freqItemset, fileName).toMap
		var rules: List[(Itemset, Itemset, Double)] = Nil
		var iter:Int = 0
    val t0 = System.currentTimeMillis()
		freq_supp.keys.foreach(freq_item=>{
			if (freq_item.size > 1) {
                val freq = freq_supp.get(freq_item).get.toDouble
				freq_item.foreach(item=>{
					rules = rules :+ ((remove(freq_item, item), List(item), freq / freq_supp.get(List(item)).get.toDouble))	
				})

				iter = iter + 1
				if (iter % 100 == 0) {
					println(iter.toString + s"'th rule has genareted in ${(System.currentTimeMillis() - t0) / 1000}s.")
				}
			}
		})
		// rules.foreach(item=>println(item._1 +" --> " +item._2 + "  conf = " +item._3))
		val sorted_rules = rules.map(t=>(t._1.sorted, t._2, t._3)).sortWith((a, b)=>le(a, b))
		sorted_rules
	}
*/    

	def genRules(freqItemset: List[Itemset], fileName: String): List[(Itemset,Itemset,Double)] = {
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
      val t0 = System.currentTimeMillis()
      var freq_supp = calSupp(freqItemset, fileName).map(t=>(t._1.sorted, t._2))

      val supp_writter = new PrintWriter(new File("output/freq_supp.txt"))
      freq_supp.foreach(t=>{
        t._1.foreach(item=>{
         supp_writter.write(item + " ") 
        })
        supp_writter.write(": " + t._2 + "\n") 
      })
      supp_writter.close()

      var singletons = freq_supp.filter(t=>t._1.size == 1).toMap
      var rules = sc.parallelize(freq_supp, Util.minPartitions)
      rules.flatMap(item=>{
        var rule : List[(Itemset, Itemset, Double)] = Nil
        val freq = item._2.toDouble
        item._1.foreach(t=>{
          if (item._1.size > 1)
            rule = rule :+ ((remove(item._1, t), List(t), freq / singletons.get(List(t)).get.toDouble))
        })
        rule
      }).collect().toList.sortWith((a,b)=>le(a, b)).reverse
    }

	def le(a:Itemset, b:Itemset) : Boolean = {
		for (i <- 0 to min(a.size, b.size) - 1) {
			val ai = a.apply(i)
			val bi = a.apply(i)
			if (ai != bi)
				return ai < bi
		}
		return a.size < b.size
	}

	def le(a:(Itemset, Itemset, Double),b:(Itemset, Itemset, Double)) : Boolean = {
		if (a._3 != b._3)
			return a._3 < b._3
		else {
			if (a._1 != b._1)
				return le(a._1, b._1)
			else
				return le(a._2, b._2)
		}	
	}

	def findrecom(rules:List[(Itemset, Itemset, Double)], item:Itemset) : (Itemset, Double) = {
		for (i <- 0 to rules.size - 1) {
			val ai = rules.apply(i)
			if (ai._1.toSet.subsetOf(item.toSet))
				return (ai._2, ai._3)
		}
		return (List("0"), 0)
	}

/*
	// No Spark Version:
	def recomend(rules:List[(Itemset, Itemset, Double)], fileName: String):List[(Itemset,Double)] = {
		var res:List[(Itemset, Double)] = Nil
		val pattern = Source.fromFile(fileName)
		var i:Int = 0
		pattern.getLines().foreach(item=>{
			val rec = findrecom(rules, item.split(" ").toList)
			res = res:+ (rec)
			i = i + 1
			if (i % 10 == 0)
				println(i.toString + "'th Item is cal")
			}
		)
		res
	}
*/
	def recomend(rules:List[(Itemset, Itemset, Double)], fileName: String):List[(Itemset,Double)] = {
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
      var transactionsRDD: RDD[Itemset] = null
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
      }
	  var res:List[(Itemset, Double)] = Nil
	  // val sorted_rules = rules.sortWith((a, b)=>le(a, b))
	  var iter : Int = 0
      val t0 = System.currentTimeMillis()
	  transactionsRDD.map(item=>{
		iter = iter + 1
		if (iter % 100 == 0) {
				println(iter.toString + s"'th recomendation has genareted in ${(System.currentTimeMillis() - t0) / 1000}s.")
		}
		findrecom(rules, item)
	  }).collect().toList	
	}
}
















