package spark.elsa

import java.nio.file.{Paths, Files}
import java.nio.charset.StandardCharsets

import org.apache.spark.SparkConf
import org.apache.spark.streaming.{Seconds, StreamingContext}
import org.apache.spark.streaming.twitter._

import org.streum.configrity._

object OnlineSA {

  def runAnalysis(elsaConf: Configuration): Unit = {
    // setting up the Spark configuration:
    val conf = new SparkConf().setAppName("ElSA Online").setMaster(elsaConf[String]("master"))
    // setting up the filename where to log the stats to:
    val stats = elsaConf[String]("stats-file")
    // setting up list of topics to be monitored by ElSA:
    val topics: Array[String] = elsaConf[String]("topics").split(",").distinct
    // setting up the Spark Streaming context:
    val ssc = new StreamingContext(conf, Seconds(elsaConf[Int]("batch-window")))

    // setting up system properties for Twitter4j lib OAuth credentials:
    System.setProperty("twitter4j.oauth.consumerKey", elsaConf[String]("consumer-key"))
    System.setProperty("twitter4j.oauth.consumerSecret", elsaConf[String]("consumer-secret"))
    System.setProperty("twitter4j.oauth.accessToken", elsaConf[String]("access-token"))
    System.setProperty("twitter4j.oauth.accessTokenSecret", elsaConf[String]("access-token-secret"))

    // hook into the Twitter firehose and get tweets with the topics of interest:
    val twitterFirehose = TwitterUtils.createStream(ssc, None, topics)

    twitterFirehose.foreachRDD(rdd => {
      val tweetCount = rdd.count()
      println("\n\nIn the past " + elsaConf[Int]("batch-window")  + " seconds " +
              "I found " + tweetCount + " tweet(s) " +
              "containing your topics: "
      )
      for (topic <- topics) print(topic + " ")
      rdd.foreach{ tweet =>
        println("\n===")
        println(tweet.getText)
        println("===")
      }
      // write out the stats:
      Files.write(Paths.get(stats), tweetCount.toString.getBytes(StandardCharsets.UTF_8))
    })

    //    val tweets = twitterFirehose.flatMap(status => status.getText.split(" "))
    //
    //    val tweetsAggregate = tweets.map((_, 1)).reduceByWindow( (a, b) => (a._1, a._2 + b._2) , Seconds(elsaConf[Int]("batch-window")) * 3, Seconds(elsaConf[Int]("batch-window")) * 3)
    //
    //    tweetsAggregate.foreachRDD(rdd => {
    //      println("Topics in last %d seconds (%s total):".format(elsaConf[Int]("batch-window") * 3, rdd.count()))
    //      rdd.foreach{case (count, tag) => println("%s (%s tweets)".format(tag, count))}
    //    })

    // kick off the ongoing stream processing:
    ssc.checkpoint(elsaConf[String]("checkpoint-dir"))
    ssc.start()
    ssc.awaitTermination()
  }

  def main(args: Array[String]) {
    if (args.length < 1) {
      System.err.println("Usage: OnlineSA  <config-file>")
      System.exit(1)
    }
    // setting up configuration:
    val elsaConf = Configuration.load(args(0))

    // makes sure that if and only if we're in production we don't show too verbose logs info:
    if (elsaConf[String]("deployment") == "production") {
      ElsaHelper.setLogLevel()
    }

    runAnalysis(elsaConf)
    System.exit(0)
  }
}