import spark.DFP
import spark.genRules
import sequential.Util
import java.io._
import scala.io._
// package spark
// package sequential
object Main {
	type Itemset = List[String]
	def writeItemset(writter:PrintWriter, item:Itemset) = {
		item.foreach(t=>writter.write(t + " "))
	}
  def main(args: Array[String]): Unit = {
    val dfps = new DFP()
    // val fileName = "dataset/test_data/simple_data.txt"
    // val fileName = "/home/yiqi/FP-Growth-using-Spark/src/main/resources/datasets/mushroom.txt"
    // val fileName = "dataset/test_data/test_2W.txt"
    val fileName = "dataset/test_data/trans_10W.txt"
    // val fileName = "dataset/data/webdocs_20percent.txt"
    val frequentItemsets = dfps.execute(fileName, separator = " ", minSupport = 0.092)
    val freq_writter = new PrintWriter(new File("output/freqitem.txt"))
    for (item <- frequentItemsets) {
    	item.foreach(t => freq_writter.write(t + " "))
			freq_writter.write("\n")
    }
    freq_writter.close()
    
	val genrules = new genRules()
	println("start gen rules")
    // val freq_file = "output/freqitem.txt.bak"
    // val frequentItemsets = Source.fromFile(freq_file).getLines().map(item=>item.split(" ").toList).toList
	val rules = genrules.genRules(frequentItemsets, fileName)
    val rules_writter = new PrintWriter(new File("output/rules.txt"))
	rules.foreach(item=>{
				writeItemset(rules_writter, item._1)
				rules_writter.write("---> ")
				writeItemset(rules_writter, item._2)
				rules_writter.write("conf: " + item._3 + "\n")
		})
    rules_writter.close()

	println("start recomm")
	val pattem_filename = "dataset/test_data/test_2W.txt"
	// val pattem_filename = "dataset/test_data/simple_pattern.txt"
	val res = genrules.recomend(rules, pattem_filename)
    val res_writter = new PrintWriter(new File("output/result.txt"))
		res.foreach(item=>{
				writeItemset(res_writter, item._1)
				res_writter.write("\n")
		})
    res_writter.close()
  }
}
