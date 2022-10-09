package io.rhonix.casper.api

import cats.effect.Sync
import cats.effect.concurrent.Ref
import cats.syntax.all._
import com.google.protobuf.ByteString
import io.rhonix.blockstorage.BlockStore
import io.rhonix.blockstorage.BlockStore.BlockStore
import io.rhonix.blockstorage.dag._
import io.rhonix.casper._
import io.rhonix.casper.helper.{BlockApiFixture, BlockDagStorageFixture}
import io.rhonix.casper.protocol.BlockMessage
import io.rhonix.casper.rholang.RuntimeManager
import io.rhonix.casper.util.ConstructDeploy
import io.rhonix.metrics.NoopSpan
import io.rhonix.models.BlockHash.BlockHash
import io.rhonix.models.Validator.Validator
import io.rhonix.models.block.StateHash.StateHash
import io.rhonix.models.blockImplicits.getRandomBlock
import io.rhonix.models.syntax._
import io.rhonix.models.{BlockMetadata, FringeData}
import io.rhonix.shared.{Log, Time}
import monix.eval.Task
import monix.testing.scalatest.MonixTaskTest
import org.mockito.cats.IdiomaticMockitoCats
import org.mockito.{ArgumentMatchersSugar, IdiomaticMockito, Mockito, MockitoSugar}
import org.scalatest._
import org.scalatest.flatspec.AsyncFlatSpec
import org.scalatest.matchers.should.Matchers

import scala.collection.immutable.SortedMap

class BlockQueryResponseAPITest
    extends AsyncFlatSpec
    with MonixTaskTest
    with Matchers
    with EitherValues
    with BlockDagStorageFixture
    with BlockApiFixture
    with IdiomaticMockito
    with IdiomaticMockitoCats
    with ArgumentMatchersSugar {
  implicit val timeEff: Time[Task]                  = Time.fromTimer[Task]
  implicit val spanEff: NoopSpan[Task]              = NoopSpan[Task]()
  implicit val log: Log[Task]                       = mock[Log[Task]]
  implicit val runtimeManager: RuntimeManager[Task] = mock[RuntimeManager[Task]]

  private val tooShortQuery    = "12345"
  private val badTestHashQuery = "1234acd"
  private val invalidHexQuery  = "No such a hash"
  private val unknownDeploy    = ByteString.copyFromUtf8("asdfQwertyUiopxyzcbv")

  private val genesisBlock: BlockMessage = getRandomBlock(setJustifications = Seq().some)

  private val deployCount = 10
  private val randomDeploys =
    (0 until deployCount).toList
      .traverse(i => ConstructDeploy.basicProcessedDeploy[Task](i))
      .runSyncUnsafe()

  private val senderString: String =
    "3456789101112131415161718192345678910111213141516171819261718192113456789101112131415161718192345678910111213141516171819261718192"
  private val sender: ByteString = senderString.unsafeHexToByteString
  private val bondsValidator     = (sender, 1L)

  private val secondBlock: BlockMessage =
    getRandomBlock(
      setValidator = sender.some,
      setDeploys = randomDeploys.some,
      setJustifications = List(genesisBlock.blockHash).some,
      setBonds = Map(bondsValidator).some
    )

  "getBlock" should "return successful block info response" in {
    implicit val bs  = createBlockStore[Task]
    implicit val bds = createBlockDagStorage[Task]

    for {
      _                  <- prepareDagStorage[Task]
      blockApi           <- createBlockApi[Task]("", 1)
      _                  = Mockito.clearInvocations(bs, bds)
      hash               = secondBlock.blockHash.toHexString
      blockQueryResponse <- blockApi.getBlock(hash)
    } yield {
      blockQueryResponse shouldBe 'right
      val blockInfo = blockQueryResponse.value
      blockInfo.deploys shouldBe randomDeploys.map(_.toDeployInfo)
      blockInfo.blockInfo shouldBe BlockApi.getLightBlockInfo(secondBlock)

      bs.get(Seq(secondBlock.blockHash)) wasCalled once
      verifyNoMoreInteractions(bs)

      bds.insert(*, *) wasNever called
      bds.getRepresentation wasCalled twice
      bds.lookupByDeployId(*) wasNever called
    }
  }

  it should "return error when no block exists" in {
    implicit val bs  = createBlockStore[Task]
    implicit val bds = createBlockDagStorage[Task]

    for {
      blockApi           <- createBlockApi[Task]("", 1)
      hash               = badTestHashQuery
      blockQueryResponse <- blockApi.getBlock(hash)
    } yield {
      blockQueryResponse shouldBe 'left
      blockQueryResponse.left.value shouldBe s"Error: Failure to find block with hash: $badTestHashQuery"

      bs.get(Seq(badTestHashQuery.unsafeHexToByteString)) wasCalled once
      verifyNoMoreInteractions(bs)

      bds.insert(*, *) wasNever called
      bds.getRepresentation wasCalled once
      bds.lookupByDeployId(*) wasNever called
    }
  }

  it should "return error when hash is invalid hex string" in {
    implicit val bs  = createBlockStore[Task]
    implicit val bds = createBlockDagStorage[Task]

    for {
      blockApi           <- createBlockApi[Task]("", 1)
      hash               = invalidHexQuery
      blockQueryResponse <- blockApi.getBlock(hash)
    } yield {
      blockQueryResponse shouldBe 'left
      blockQueryResponse.left.value shouldBe s"Input hash value is not valid hex string: $invalidHexQuery"

      verifyNoMoreInteractions(bs)

      bds.insert(*, *) wasNever called
      bds.getRepresentation wasNever called
      bds.lookupByDeployId(*) wasNever called
    }
  }

  it should "return error when hash is to short" in {
    implicit val bs  = createBlockStore[Task]
    implicit val bds = createBlockDagStorage[Task]

    for {
      blockApi           <- createBlockApi[Task]("", 1)
      hash               = tooShortQuery
      blockQueryResponse <- blockApi.getBlock(hash)
    } yield {
      blockQueryResponse shouldBe 'left
      blockQueryResponse.left.value shouldBe s"Input hash value must be at least 6 characters: $tooShortQuery"

      verifyNoMoreInteractions(bs)

      bds.insert(*, *) wasNever called
      bds.getRepresentation wasNever called
      bds.lookupByDeployId(*) wasNever called
    }
  }

  "findDeploy" should "return successful block info response when a block contains the deploy with given signature" in {
    implicit val bs  = createBlockStore[Task]
    implicit val bds = createBlockDagStorage[Task]

    for {
      _                  <- prepareDagStorage[Task]
      blockApi           <- createBlockApi[Task]("", 1)
      _                  = Mockito.clearInvocations(bs, bds)
      deployId           = randomDeploys.head.deploy.sig
      blockQueryResponse <- blockApi.findDeploy(deployId)
    } yield {
      blockQueryResponse shouldBe 'right
      blockQueryResponse.value shouldBe BlockApi.getLightBlockInfo(secondBlock)

      bs.get(Seq(secondBlock.blockHash)) wasCalled once
      verifyNoMoreInteractions(bs)

      bds.insert(*, *) wasNever called
      bds.getRepresentation wasNever called
      bds.lookupByDeployId(deployId) wasCalled once
    }
  }

  it should "return an error when no block contains the deploy with the given signature" in {
    implicit val bs  = createBlockStore[Task]
    implicit val bds = createBlockDagStorage[Task]

    for {
      blockApi           <- createBlockApi[Task]("", 1)
      blockQueryResponse <- blockApi.findDeploy(unknownDeploy)
    } yield {
      blockQueryResponse shouldBe 'left
      blockQueryResponse.left.value shouldBe
        s"Couldn't find block containing deploy with id: ${PrettyPrinter.buildStringNoLimit(unknownDeploy)}"

      verifyNoMoreInteractions(bs)

      bds.insert(*, *) wasNever called
      bds.getRepresentation wasNever called
      bds.lookupByDeployId(unknownDeploy) wasCalled once
    }
  }

  private def createBlockStore[F[_]: Sync] = {
    val bs = mock[BlockStore[F]]
    bs.put(Seq((genesisBlock.blockHash, genesisBlock))) returns ().pure
    bs.put(Seq((secondBlock.blockHash, secondBlock))) returns ().pure
    bs.get(Seq(secondBlock.blockHash)) returnsF Seq(secondBlock.some)
    bs.get(Seq(badTestHashQuery.unsafeHexToByteString)) returnsF Seq(None)
    bs
  }

  private def createBlockDagStorage[F[_]: Sync]: BlockDagStorage[F] = {
    val genesisHash: ByteString = RuntimeManager.emptyStateHashFixed

    val state = Ref.unsafe[F, DagRepresentation](
      DagRepresentation(
        Set(),
        Map(),
        SortedMap(),
        DagMessageState(),
        Map(
          Set(genesisHash) -> FringeData(
            FringeData.fringeHash(Set.empty),
            Set.empty,
            Set.empty,
            genesisHash.toBlake2b256Hash,
            Set.empty,
            Set.empty,
            Set.empty
          )
        )
      )
    )

    val bds = mock[BlockDagStorage[F]]

    bds.insert(*, *) answers { (bmd: BlockMetadata, b: BlockMessage) =>
      state.update { s =>
        val newDagSet = s.dagSet + b.blockHash

        val newChildMap = b.justifications.foldLeft(s.childMap) {
          case (m, h) => m + (h -> (m.getOrElse(h, Set.empty) + b.blockHash))
        } + (b.blockHash -> Set.empty[BlockHash])

        val newHeightMap = s.heightMap + (b.blockNumber -> (s.heightMap
          .getOrElse(b.blockNumber, Set.empty) + b.blockHash))

        val seen = b.justifications
          .flatMap(h => s.dagMessageState.msgMap(h).seen)
          .toSet ++ b.justifications + b.blockHash

        val newMsgMap = s.dagMessageState.msgMap + (b.blockHash -> toMessage(b, seen))

        val newLatestMsgs = newMsgMap.foldLeft(Set.empty[Message[BlockHash, Validator]]) {
          case (acc, (_, msg)) =>
            acc + acc
              .find(_.sender == msg.sender)
              .map(m => if (msg.height > m.height) msg else m)
              .getOrElse(msg)
        }
        val newDagMessageState = s.dagMessageState.copy(newLatestMsgs, newMsgMap)

        s.copy(
          dagSet = newDagSet,
          childMap = newChildMap,
          heightMap = newHeightMap,
          dagMessageState = newDagMessageState
        )
      }
    }

    bds.getRepresentation returns state.get

    bds.lookupByDeployId(randomDeploys.head.deploy.sig) returnsF secondBlock.blockHash.some
    bds.lookupByDeployId(unknownDeploy) returnsF None

    bds
  }

  // Default args only available for public method in Scala 2.12 (https://github.com/scala/bug/issues/12168)
  def toMessage(
      m: BlockMessage,
      seen: Set[BlockHash] = Set.empty[BlockHash]
  ): Message[BlockHash, Validator] =
    Message[BlockHash, Validator](
      m.blockHash,
      m.blockNumber,
      m.sender,
      m.seqNum,
      m.bonds,
      m.justifications.toSet,
      Set(),
      seen
    )

  private def prepareDagStorage[F[_]: Sync: BlockDagStorage: BlockStore]: F[Unit] = {
    import io.rhonix.blockstorage.syntax._
    def insertToDag(b: BlockMessage, stateHash: StateHash): F[Unit] =
      BlockDagStorage[F].insert(BlockMetadata.fromBlock(b).copy(fringeStateHash = stateHash), b)
    for {
      _ <- List(genesisBlock, secondBlock).traverse(BlockStore[F].put(_))
      _ <- insertToDag(genesisBlock, genesisBlock.postStateHash)
      _ <- insertToDag(secondBlock, RuntimeManager.emptyStateHashFixed)
    } yield ()
  }
}
