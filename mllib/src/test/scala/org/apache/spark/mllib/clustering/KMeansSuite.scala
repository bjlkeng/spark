/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.spark.mllib.clustering

import scala.util.Random

import org.scalatest.FunSuite

import org.apache.spark.mllib.linalg.{DenseVector, SparseVector, Vector, Vectors}
import org.apache.spark.mllib.linalg.BLAS.{scal, axpy}
import org.apache.spark.mllib.util.{LocalClusterSparkContext, MLlibTestSparkContext}
import org.apache.spark.mllib.util.TestingUtils._
import org.apache.spark.util.Utils

class KMeansSuite extends FunSuite with MLlibTestSparkContext {

  import org.apache.spark.mllib.clustering.KMeans.{K_MEANS_PARALLEL, RANDOM}

  test("single cluster") {
    val data = sc.parallelize(Array(
      Vectors.dense(1.0, 2.0, 6.0),
      Vectors.dense(1.0, 3.0, 0.0),
      Vectors.dense(1.0, 4.0, 6.0)
    ))

    val center = Vectors.dense(1.0, 3.0, 4.0)

    // No matter how many runs or iterations we use, we should get one cluster,
    // centered at the mean of the points

    var model = KMeans.train(data, k = 1, maxIterations = 1)
    assert(model.clusterCenters.head ~== center absTol 1E-5)

    model = KMeans.train(data, k = 1, maxIterations = 2)
    assert(model.clusterCenters.head ~== center absTol 1E-5)

    model = KMeans.train(data, k = 1, maxIterations = 5)
    assert(model.clusterCenters.head ~== center absTol 1E-5)

    model = KMeans.train(data, k = 1, maxIterations = 1, runs = 5)
    assert(model.clusterCenters.head ~== center absTol 1E-5)

    model = KMeans.train(data, k = 1, maxIterations = 1, runs = 5)
    assert(model.clusterCenters.head ~== center absTol 1E-5)

    model = KMeans.train(data, k = 1, maxIterations = 1, runs = 1, initializationMode = RANDOM)
    assert(model.clusterCenters.head ~== center absTol 1E-5)

    model = KMeans.train(
      data, k = 1, maxIterations = 1, runs = 1, initializationMode = K_MEANS_PARALLEL)
    assert(model.clusterCenters.head ~== center absTol 1E-5)
  }

  test("single cluster - spherical") {
    val points = Array(
      Vectors.dense(1.0, 2.0, 6.0),
      Vectors.dense(1.0, 3.0, 0.0),
      Vectors.dense(1.0, 4.0, 6.0)
    )
    val data = sc.parallelize(points)

    // Compute the center which should be the unit-length mean of the
    // unit-length points.
    val norms = points.map(Vectors.norm(_, 2.0))
    val unitPoints = points.clone()
    for ((x, i) <- unitPoints.zipWithIndex) 
        scal(1.0 / norms(i), x)
    val sum = Vectors.zeros(3)
    for (x <- points)
        axpy(1.0, x, sum)
    scal(1.0 / 3.0, sum)
    scal(1.0 / Vectors.norm(sum, 2.0), sum)
    val center = sum

    // No matter how many runs or iterations we use, we should get one cluster
    var model = KMeans.trainSpherical(data, k = 1, maxIterations = 1)
    assert(model.clusterCenters.head ~== center absTol 1E-5)

    model = KMeans.trainSpherical(data, k = 1, maxIterations = 2)
    assert(model.clusterCenters.head ~== center absTol 1E-5)

    model = KMeans.trainSpherical(data, k = 1, maxIterations = 5)
    assert(model.clusterCenters.head ~== center absTol 1E-5)

    model = KMeans.trainSpherical(data, k = 1, maxIterations = 1, runs = 5)
    assert(model.clusterCenters.head ~== center absTol 1E-5)

    model = KMeans.trainSpherical(data, k = 1, maxIterations = 1, runs = 5)
    assert(model.clusterCenters.head ~== center absTol 1E-5)

    model = KMeans.trainSpherical(data, k = 1, maxIterations = 1, runs = 1)
    assert(model.clusterCenters.head ~== center absTol 1E-5)
  }

  test("no distinct points") {
    val data = sc.parallelize(
      Array(
        Vectors.dense(1.0, 2.0, 3.0),
        Vectors.dense(1.0, 2.0, 3.0),
        Vectors.dense(1.0, 2.0, 3.0)),
      2)
    val center = Vectors.dense(1.0, 2.0, 3.0)

    // Make sure code runs.
    var model = KMeans.train(data, k=2, maxIterations=1)
    assert(model.clusterCenters.size === 2)

    model = KMeans.trainSpherical(data, k=2, maxIterations=1)
    assert(Vectors.norm(model.clusterCenters.head, 2.0) ~== 1.0 absTol 1E-8)
    assert(model.clusterCenters.size === 2)
  }

  test("more clusters than points") {
    val data = sc.parallelize(
      Array(
        Vectors.dense(1.0, 2.0, 3.0),
        Vectors.dense(1.0, 3.0, 4.0)),
      2)

    // Make sure code runs.
    var model = KMeans.train(data, k=3, maxIterations=1)
    assert(model.clusterCenters.size === 3)
    model = KMeans.trainSpherical(data, k=3, maxIterations=1)
    assert(Vectors.norm(model.clusterCenters.head, 2.0) ~== 1.0 absTol 1E-8)
    assert(model.clusterCenters.size === 3)
  }

  test("deterministic initialization") {
    // Create a large-ish set of points for clustering
    val points = List.tabulate(1000)(n => Vectors.dense(n, n))
    val rdd = sc.parallelize(points, 3)

    for (initMode <- Seq(RANDOM, K_MEANS_PARALLEL)) {
      // Create three deterministic models and compare cluster means
      val model1 = KMeans.train(rdd, k = 10, maxIterations = 2, runs = 1,
        initializationMode = initMode, seed = 42)
      val centers1 = model1.clusterCenters

      val model2 = KMeans.train(rdd, k = 10, maxIterations = 2, runs = 1,
        initializationMode = initMode, seed = 42)
      val centers2 = model2.clusterCenters

      centers1.zip(centers2).foreach { case (c1, c2) =>
        assert(c1 ~== c2 absTol 1E-14)
      }
    }
  }

  test("deterministic initialization - spherical") {
    // Create a large-ish set of points for clustering
    val points = List.tabulate(1000)(n => Vectors.dense(n, n))
    val rdd = sc.parallelize(points, 3)

    val initMode = RANDOM
    // Create three deterministic models and compare cluster means
    val model1 = KMeans.train(rdd, k = 10, maxIterations = 2, runs = 1,
      initializationMode = initMode, seed = 42)
    val centers1 = model1.clusterCenters

    val model2 = KMeans.train(rdd, k = 10, maxIterations = 2, runs = 1,
      initializationMode = initMode, seed = 42)
    val centers2 = model2.clusterCenters

    centers1.zip(centers2).foreach { case (c1, c2) =>
      assert(c1 ~== c2 absTol 1E-14)
    }
  }

  test("single cluster with big dataset") {
    val smallData = Array(
      Vectors.dense(1.0, 2.0, 6.0),
      Vectors.dense(1.0, 3.0, 0.0),
      Vectors.dense(1.0, 4.0, 6.0)
    )
    val data = sc.parallelize((1 to 100).flatMap(_ => smallData), 4)

    // No matter how many runs or iterations we use, we should get one cluster,
    // centered at the mean of the points

    val center = Vectors.dense(1.0, 3.0, 4.0)

    var model = KMeans.train(data, k = 1, maxIterations = 1)
    assert(model.clusterCenters.size === 1)
    assert(model.clusterCenters.head ~== center absTol 1E-5)

    model = KMeans.train(data, k = 1, maxIterations = 2)
    assert(model.clusterCenters.head ~== center absTol 1E-5)

    model = KMeans.train(data, k = 1, maxIterations = 5)
    assert(model.clusterCenters.head ~== center absTol 1E-5)

    model = KMeans.train(data, k = 1, maxIterations = 1, runs = 5)
    assert(model.clusterCenters.head ~== center absTol 1E-5)

    model = KMeans.train(data, k = 1, maxIterations = 1, runs = 5)
    assert(model.clusterCenters.head ~== center absTol 1E-5)

    model = KMeans.train(data, k = 1, maxIterations = 1, runs = 1, initializationMode = RANDOM)
    assert(model.clusterCenters.head ~== center absTol 1E-5)

    model = KMeans.train(data, k = 1, maxIterations = 1, runs = 1,
      initializationMode = K_MEANS_PARALLEL)
    assert(model.clusterCenters.head ~== center absTol 1E-5)
  }

  test("single cluster with big dataset - spherical") {
    val smallData = Array(
      Vectors.dense(1.0, 2.0, 6.0),
      Vectors.dense(1.0, 3.0, 0.0),
      Vectors.dense(1.0, 4.0, 6.0)
    )
    val data = sc.parallelize((1 to 100).flatMap(_ => smallData), 4)

    // Compute the center which should be the unit-length mean of the
    // unit-length points.
    val norms = smallData.map(Vectors.norm(_, 2.0))
    val unitPoints = smallData.clone()
    for ((x, i) <- unitPoints.zipWithIndex) 
        scal(1.0 / norms(i), x)
    val sum = Vectors.zeros(3)
    for (x <- smallData)
        axpy(1.0, x, sum)
    scal(1.0 / 3.0, sum)
    scal(1.0 / Vectors.norm(sum, 2.0), sum)
    val center = sum

    var model = KMeans.trainSpherical(data, k = 1, maxIterations = 1)
    assert(model.clusterCenters.size === 1)
    assert(model.clusterCenters.head ~== center absTol 1E-5)

    model = KMeans.trainSpherical(data, k = 1, maxIterations = 2)
    assert(model.clusterCenters.head ~== center absTol 1E-5)

    model = KMeans.trainSpherical(data, k = 1, maxIterations = 5)
    assert(model.clusterCenters.head ~== center absTol 1E-5)

    model = KMeans.trainSpherical(data, k = 1, maxIterations = 1, runs = 5)
    assert(model.clusterCenters.head ~== center absTol 1E-5)

    model = KMeans.trainSpherical(data, k = 1, maxIterations = 1, runs = 5)
    assert(model.clusterCenters.head ~== center absTol 1E-5)

    model = KMeans.trainSpherical(data, k = 1, maxIterations = 1, runs = 1)
    assert(model.clusterCenters.head ~== center absTol 1E-5)
  }


  test("single cluster with sparse data") {
    val n = 10000
    val data = sc.parallelize((1 to 100).flatMap { i =>
      val x = i / 1000.0
      Array(
        Vectors.sparse(n, Seq((0, 1.0 + x), (1, 2.0), (2, 6.0))),
        Vectors.sparse(n, Seq((0, 1.0 - x), (1, 2.0), (2, 6.0))),
        Vectors.sparse(n, Seq((0, 1.0), (1, 3.0 + x))),
        Vectors.sparse(n, Seq((0, 1.0), (1, 3.0 - x))),
        Vectors.sparse(n, Seq((0, 1.0), (1, 4.0), (2, 6.0 + x))),
        Vectors.sparse(n, Seq((0, 1.0), (1, 4.0), (2, 6.0 - x)))
      )
    }, 4)

    data.persist()

    // No matter how many runs or iterations we use, we should get one cluster,
    // centered at the mean of the points

    val center = Vectors.sparse(n, Seq((0, 1.0), (1, 3.0), (2, 4.0)))

    var model = KMeans.train(data, k = 1, maxIterations = 1)
    assert(model.clusterCenters.head ~== center absTol 1E-5)

    model = KMeans.train(data, k = 1, maxIterations = 2)
    assert(model.clusterCenters.head ~== center absTol 1E-5)

    model = KMeans.train(data, k = 1, maxIterations = 5)
    assert(model.clusterCenters.head ~== center absTol 1E-5)

    model = KMeans.train(data, k = 1, maxIterations = 1, runs = 5)
    assert(model.clusterCenters.head ~== center absTol 1E-5)

    model = KMeans.train(data, k = 1, maxIterations = 1, runs = 5)
    assert(model.clusterCenters.head ~== center absTol 1E-5)

    model = KMeans.train(data, k = 1, maxIterations = 1, runs = 1, initializationMode = RANDOM)
    assert(model.clusterCenters.head ~== center absTol 1E-5)

    model = KMeans.train(data, k = 1, maxIterations = 1, runs = 1,
      initializationMode = K_MEANS_PARALLEL)
    assert(model.clusterCenters.head ~== center absTol 1E-5)

    data.unpersist()
  }

  test("single cluster with sparse data - spherical") {
    val n = 10000
    val points = (1 to 100).flatMap { i =>
      val x = i / 1000.0
      Array(
        Vectors.sparse(n, Seq((0, 1.0 + x), (1, 2.0), (2, 6.0))),
        Vectors.sparse(n, Seq((0, 1.0 - x), (1, 2.0), (2, 6.0))),
        Vectors.sparse(n, Seq((0, 1.0), (1, 3.0 + x))),
        Vectors.sparse(n, Seq((0, 1.0), (1, 3.0 - x))),
        Vectors.sparse(n, Seq((0, 1.0), (1, 4.0), (2, 6.0 + x))),
        Vectors.sparse(n, Seq((0, 1.0), (1, 4.0), (2, 6.0 - x)))
      )
    }
    val data = sc.parallelize(points, 4)

    data.persist()

    // Compute the center which should be the unit-length mean of the
    // unit-length points.
    val norms = points.map(Vectors.norm(_, 2.0))
    val unitPoints = scala.collection.mutable.ArraySeq(points:_*)

    for ((x, i) <- unitPoints.zipWithIndex) 
        scal(1.0 / norms(i), x)
    val sum = Vectors.zeros(n)
    for (x <- points)
        axpy(1.0, x, sum)
    scal(1.0 / n, sum)
    scal(1.0 / Vectors.norm(sum, 2.0), sum)
    val center = sum

    var model = KMeans.trainSpherical(data, k = 1, maxIterations = 1)
    assert(model.clusterCenters.head ~== center absTol 1E-5)

    model = KMeans.trainSpherical(data, k = 1, maxIterations = 2)
    assert(model.clusterCenters.head ~== center absTol 1E-5)

    model = KMeans.trainSpherical(data, k = 1, maxIterations = 5)
    assert(model.clusterCenters.head ~== center absTol 1E-5)

    model = KMeans.trainSpherical(data, k = 1, maxIterations = 1, runs = 5)
    assert(model.clusterCenters.head ~== center absTol 1E-5)

    model = KMeans.trainSpherical(data, k = 1, maxIterations = 1, runs = 5)
    assert(model.clusterCenters.head ~== center absTol 1E-5)

    model = KMeans.trainSpherical(data, k = 1, maxIterations = 1, runs = 1)
    assert(model.clusterCenters.head ~== center absTol 1E-5)

    data.unpersist()
  }


  test("k-means|| initialization") {

    case class VectorWithCompare(x: Vector) extends Ordered[VectorWithCompare] {
      override def compare(that: VectorWithCompare): Int = {
        if (this.x.toArray.foldLeft[Double](0.0)((acc, x) => acc + x * x) >
          that.x.toArray.foldLeft[Double](0.0)((acc, x) => acc + x * x)) {
          -1
        } else {
          1
        }
      }
    }

    val points = Seq(
      Vectors.dense(1.0, 2.0, 6.0),
      Vectors.dense(1.0, 3.0, 0.0),
      Vectors.dense(1.0, 4.0, 6.0),
      Vectors.dense(1.0, 0.0, 1.0),
      Vectors.dense(1.0, 1.0, 1.0)
    )
    val rdd = sc.parallelize(points)

    // K-means|| initialization should place all clusters into distinct centers because
    // it will make at least five passes, and it will give non-zero probability to each
    // unselected point as long as it hasn't yet selected all of them

    var model = KMeans.train(rdd, k = 5, maxIterations = 1)

    assert(model.clusterCenters.sortBy(VectorWithCompare(_))
      .zip(points.sortBy(VectorWithCompare(_))).forall(x => x._1 ~== (x._2) absTol 1E-5))

    // Iterations of Lloyd's should not change the answer either
    model = KMeans.train(rdd, k = 5, maxIterations = 10)
    assert(model.clusterCenters.sortBy(VectorWithCompare(_))
      .zip(points.sortBy(VectorWithCompare(_))).forall(x => x._1 ~== (x._2) absTol 1E-5))

    // Neither should more runs
    model = KMeans.train(rdd, k = 5, maxIterations = 10, runs = 5)
    assert(model.clusterCenters.sortBy(VectorWithCompare(_))
      .zip(points.sortBy(VectorWithCompare(_))).forall(x => x._1 ~== (x._2) absTol 1E-5))
  }

  test("two clusters") {
    val points = Seq(
      Vectors.dense(0.0, 0.0),
      Vectors.dense(0.0, 0.1),
      Vectors.dense(0.1, 0.0),
      Vectors.dense(9.0, 0.0),
      Vectors.dense(9.0, 0.2),
      Vectors.dense(9.2, 0.0)
    )
    val rdd = sc.parallelize(points, 3)

    for (initMode <- Seq(RANDOM, K_MEANS_PARALLEL)) {
        // Two iterations are sufficient no matter where the initial centers are.
        val model = KMeans.train(rdd, k = 2, maxIterations = 2, runs = 1, initMode)

        val predicts = model.predict(rdd).collect()

        assert(predicts(0) === predicts(1))
        assert(predicts(0) === predicts(2))
        assert(predicts(3) === predicts(4))
        assert(predicts(3) === predicts(5))
        assert(predicts(0) != predicts(3))
    }
  }

  test("two clusters - euclidean vs spherical") {
    val points = Seq(
      Vectors.dense(0.50, 0.85),
      Vectors.dense(0.50, 0.866),
      Vectors.dense(0.866, 0.50),
      Vectors.dense(50, 86.6),
      Vectors.dense(86.6, 50),
      Vectors.dense(86.6, 51)
    )
    val rdd = sc.parallelize(points, 3)

    val initMode = RANDOM

    val model = KMeans.train(rdd, k = 2, maxIterations = 5, runs = 1, initMode)
    val predicts = model.predict(rdd).collect()

    assert(predicts(0) === predicts(1))
    assert(predicts(0) === predicts(2))
    assert(predicts(3) === predicts(4))
    assert(predicts(3) === predicts(5))
    assert(predicts(0) != predicts(3))

    val model2 = KMeans.trainSpherical(rdd, k = 2, maxIterations = 5, runs = 1)
    assert(Vectors.norm(model2.clusterCenters.head, 2.0) ~== 1.0 absTol 1E-8)
    assert(Vectors.norm(model2.clusterCenters.last, 2.0) ~== 1.0 absTol 1E-8)
    val predicts2 = model2.predict(rdd).collect()

    assert(predicts2(0) === predicts2(1))
    assert(predicts2(0) === predicts2(3))
    assert(predicts2(2) === predicts2(4))
    assert(predicts2(2) === predicts2(5))
    assert(predicts2(0) != predicts2(2))
  }

  test("model save/load") {
    val tempDir = Utils.createTempDir()
    val path = tempDir.toURI.toString

    Array(true, false).foreach { case selector =>
      val model = KMeansSuite.createModel(10, 3, selector)
      // Save model, load it back, and compare.
      try {
        model.save(sc, path)
        val sameModel = KMeansModel.load(sc, path)
        KMeansSuite.checkEqual(model, sameModel)
      } finally {
        Utils.deleteRecursively(tempDir)
      }
    }
  }
}

object KMeansSuite extends FunSuite {
  def createModel(dim: Int, k: Int, isSparse: Boolean): KMeansModel = {
    val singlePoint = isSparse match {
      case true =>
        Vectors.sparse(dim, Array.empty[Int], Array.empty[Double])
      case _ =>
        Vectors.dense(Array.fill[Double](dim)(0.0))
    }
    new KMeansModel(Array.fill[Vector](k)(singlePoint))
  }

  def checkEqual(a: KMeansModel, b: KMeansModel): Unit = {
    assert(a.k === b.k)
    a.clusterCenters.zip(b.clusterCenters).foreach {
      case (ca: SparseVector, cb: SparseVector) =>
        assert(ca === cb)
      case (ca: DenseVector, cb: DenseVector) =>
        assert(ca === cb)
      case _ =>
        throw new AssertionError("checkEqual failed since the two clusters were not identical.\n")
    }
  }
}

class KMeansClusterSuite extends FunSuite with LocalClusterSparkContext {

  test("task size should be small in both training and prediction") {
    val m = 4
    val n = 200000
    val points = sc.parallelize(0 until m, 2).mapPartitionsWithIndex { (idx, iter) =>
      val random = new Random(idx)
      iter.map(i => Vectors.dense(Array.fill(n)(random.nextDouble)))
    }.cache()
    for (initMode <- Seq(KMeans.RANDOM, KMeans.K_MEANS_PARALLEL)) {
      // If we serialize data directly in the task closure, the size of the serialized task would be
      // greater than 1MB and hence Spark would throw an error.
      val model = KMeans.train(points, 2, 2, 1, initMode)
      val predictions = model.predict(points).collect()
      val cost = model.computeCost(points)
    }
  }
}
