package sequential


import java.io._
import java.util.Properties

// import sequential.Apriori.Itemset
// import sequential.NaiveFIM.Rule

import scala.collection.immutable.SortedMap
import scala.io.Source

object Util {
  type Rule = (List[String], List[String])
  type Itemset = List[String]

  var replicateNTimes: Int = 1
  var minPartitions: Int = 12
  var appName = "FIM"
  var props: Properties = new Properties()

  def absoluteSupport(minSupport: Double, numTransactions: Int) = (numTransactions * minSupport + 0.5).toInt

  def percentageSupport(minSupport: Int, numTransactions: Int) = minSupport / numTransactions.toDouble

  def parseTransactions(lines: List[String], separator: String): List[Itemset] = {
    lines.filter(l => !l.startsWith("#"))
      .filter(!_.trim.isEmpty)
      .map(l => l.split(separator + "+"))
      .map(l => l.map(item => item.trim).toList)
  }

  def parseTransactions(fileName: String, separator: String = ","): List[Itemset] = {

    parseTransactions(
      (1 to replicateNTimes).flatMap(_ => {
        val file = Source.fromFile(fileName, "UTF-8")
        file.getLines
      }).toList, separator)
  }

  def parseTransactionsByText(text: String): List[Itemset] = {
    parseTransactions(text.split("\n").toList, ",")
  }

  def formatRule(rule: Rule): String = {
    "{" + rule._1.mkString(", ") + "} -> {" + rule._2.mkString(", ") + "}"
  }

  // todo: print as matrix, per dataset/algorithm
  def printItemsets(itemsets: List[Itemset]) = {
    println(s"Found ${itemsets.size} itemsets")
    val writter = new PrintWriter(new File("output.txt"))
    SortedMap(itemsets.groupBy(itemset => itemset.size).toSeq: _*)
      .mapValues(i => i.map(set => s"{${set.sorted.mkString(", ")}}").sorted.mkString(", "))
      .foreach(t => writter.write(s"[${t._1}] ${t._2}"))
    writter.close()
  }

  object Tabulator {
    def format(table: Seq[Seq[Any]]) = table match {
      case Seq() => ""
      case _ =>
        val sizes = for (row <- table) yield (for (cell <- row) yield if (cell == null) 0 else cell.toString.length)
        val colSizes = for (col <- sizes.transpose) yield col.max
        val rows = for (row <- table) yield formatRow(row, colSizes)
        formatRows(rowSeparator(colSizes), rows)
    }

    def formatRows(rowSeparator: String, rows: Seq[String]): String = (
      rowSeparator ::
        rows.head ::
        rowSeparator ::
        rows.tail.toList :::
        rowSeparator ::
        List()).mkString("\n")

    def formatRow(row: Seq[Any], colSizes: Seq[Int]) = {
      val cells = (for ((item, size) <- row.zip(colSizes)) yield if (size == 0) "" else ("%" + size + "s").format(item))
      cells.mkString("|", "|", "|")
    }

    def rowSeparator(colSizes: Seq[Int]) = colSizes map {
      "-" * _
    } mkString("+", "+", "+")
  }

}
