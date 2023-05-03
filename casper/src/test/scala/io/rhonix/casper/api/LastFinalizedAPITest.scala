package io.rhonix.casper.api

import cats.Applicative
import cats.syntax.all._
import io.rhonix.blockstorage.BlockStore.BlockStore
import io.rhonix.blockstorage.dag.{BlockDagStorage, DagMessageState, DagRepresentation, Message}
import io.rhonix.casper.ValidatorIdentity
import io.rhonix.casper.helper._
import io.rhonix.casper.rholang.RuntimeManager
import io.rhonix.casper.util.GenesisBuilder._
import io.rhonix.metrics.Span
import io.rhonix.models.BlockHash.BlockHash
import io.rhonix.models.Validator.Validator
import io.rhonix.models.syntax._
import io.rhonix.shared.Log
import monix.eval.Task
import monix.testing.scalatest.MonixTaskTest
import org.mockito.IdiomaticMockito
import org.mockito.cats.IdiomaticMockitoCats
import org.scalatest.EitherValues
import org.scalatest.flatspec.AsyncFlatSpec
import org.scalatest.matchers.should.Matchers

import scala.collection.immutable.SortedMap

class LastFinalizedAPITest
    extends AsyncFlatSpec
    with MonixTaskTest
    with Matchers
    with EitherValues
    with BlockGenerator
    with BlockDagStorageFixture
    with BlockApiFixture
    with IdiomaticMockito
    with IdiomaticMockitoCats {

  private val knownHash   = "abc"
  private val unknownHash = "bcd"
  private val wrongHash   = "xyz"

  private val createValidator = ValidatorIdentity(randomValidatorKeyPairs.take(1).toList.head._1)
  private val createSender    = createValidator.publicKey.bytes.toByteString

  "isFinalized" should "return true for a block placed in the DAG" in {
    implicit val (log, sp, rm, bs, bds) = createMocks[Task]
    for {
      blockApi <- createBlockApi[Task]("root", 50, createValidator.some)
      res      <- blockApi.isFinalized(knownHash)
    } yield {
      res.value shouldBe true
      bds.getRepresentation wasCalled once

      verifyNoMoreInteractions(bs)
      verifyNoMoreInteractions(bds)
    }
  }

  "isFinalized" should "return false for a block not placed in the DAG" in {
    implicit val (log, sp, rm, bs, bds) = createMocks[Task]
    for {
      blockApi <- createBlockApi[Task]("root", 50, createValidator.some)
      res      <- blockApi.isFinalized(unknownHash)
    } yield {
      res.value shouldBe false
      bds.getRepresentation wasCalled once

      verifyNoMoreInteractions(bs)
      verifyNoMoreInteractions(bds)
    }
  }

  "isFinalized" should "not throw exception and return false for wrong hash" in {
    implicit val (log, sp, rm, bs, bds) = createMocks[Task]
    for {
      blockApi <- createBlockApi[Task]("root", 50, createValidator.some)

      // No exception is thrown here, because the decoding implementation simply discards non-hex characters
      res <- blockApi.isFinalized(wrongHash)
    } yield {
      res.value shouldBe false
      bds.getRepresentation wasCalled once

      verifyNoMoreInteractions(bs)
      verifyNoMoreInteractions(bds)
    }
  }

  "isFinalized" should "return true for hash which becomes known after removing wrong characters" in {
    implicit val (log, sp, rm, bs, bds) = createMocks[Task]
    for {
      blockApi <- createBlockApi[Task]("root", 50, createValidator.some)
      res      <- blockApi.isFinalized(wrongHash + knownHash)
    } yield {
      res.value shouldBe true
      bds.getRepresentation wasCalled once

      verifyNoMoreInteractions(bs)
      verifyNoMoreInteractions(bds)
    }
  }

  private def createMocks[F[_]: Applicative]
      : (Log[F], Span[F], RuntimeManager[F], BlockStore[F], BlockDagStorage[F]) = {
    val log = mock[Log[F]]
    val sp  = mock[Span[F]]
    val rm  = mock[RuntimeManager[F]]
    val bs  = mock[BlockStore[F]]

    val bds = mock[BlockDagStorage[F]]

    val knownHashBS = knownHash.unsafeHexToByteString
    val msg = new Message[BlockHash, Validator](
      knownHashBS,
      0,
      createSender,
      0,
      Map.empty,
      Set.empty,
      // DAG contains only one message, which is finalized and sees itself
      Set(knownHashBS),
      Set(knownHashBS)
    )

    bds.getRepresentation returnsF DagRepresentation(
      Set.empty,
      Map.empty,
      SortedMap.empty,
      new DagMessageState(Set(msg), Map(msg.id -> msg)),
      Map.empty
    )

    (log, sp, rm, bs, bds)
  }
}
