package io.rhonix.casper.merging

import cats.effect.Concurrent
import cats.syntax.all._
import io.rhonix.blockstorage.BlockStore
import io.rhonix.blockstorage.BlockStore.BlockStore
import io.rhonix.casper.merging.DeployIndex._
import io.rhonix.casper.protocol.ProcessedSystemDeploy.Succeeded
import io.rhonix.casper.protocol._
import io.rhonix.casper.rholang.RuntimeManager
import io.rhonix.casper.syntax._
import io.rhonix.casper.util.EventConverter
import io.rhonix.models.BlockHash.BlockHash
import io.rhonix.models.syntax._
import io.rhonix.rspace.hashing.Blake2b256Hash
import io.rhonix.rspace.history.HistoryRepository
import io.rhonix.rspace.merger.EventLogMergingLogic.NumberChannelsDiff
import io.rhonix.rspace.merger._
import io.rhonix.rspace.trace.Produce
import io.rhonix.sdk.dag.merging.ConflictResolutionLogic

import scala.collection.concurrent.TrieMap

final case class BlockIndex(blockHash: BlockHash, deployChains: Vector[DeployChainIndex])

object BlockIndex {

  // TODO make proper storage for block indices
  val cache = TrieMap.empty[BlockHash, BlockIndex]

  def getBlockIndex[F[_]: Concurrent: RuntimeManager: BlockStore](
      blockHash: BlockHash
  ): F[BlockIndex] = {
    val cached = BlockIndex.cache.get(blockHash).map(_.pure)
    cached.getOrElse {
      for {
        _            <- io.rhonix.shared.Log.log[F].info(s"Cache miss. Indexing ${blockHash.show}.")
        b            <- BlockStore[F].getUnsafe(blockHash)
        preState     = b.preStateHash
        postState    = b.postStateHash
        sender       = b.sender.toByteArray
        seqNum       = b.seqNum
        mergeableChs <- RuntimeManager[F].loadMergeableChannels(postState, sender, seqNum)
        blockIndex <- BlockIndex(
                       b.blockHash,
                       b.state.deploys,
                       b.state.systemDeploys,
                       preState.toBlake2b256Hash,
                       postState.toBlake2b256Hash,
                       RuntimeManager[F].getHistoryRepo,
                       mergeableChs
                     )
        _ = BlockIndex.cache.putIfAbsent(blockHash, blockIndex)
      } yield blockIndex
    }
  }

  def createEventLogIndex[F[_]: Concurrent, C, P, A, K](
      events: List[Event],
      historyRepository: HistoryRepository[F, C, P, A, K],
      preStateHash: Blake2b256Hash,
      mergeableChs: NumberChannelsDiff
  ): F[EventLogIndex] =
    for {
      preStateReader <- historyRepository.getHistoryReader(preStateHash)
      produceExistsInPreState = (p: Produce) =>
        preStateReader.getData(p.channelsHash).map(_.exists(_.source == p))
      produceTouchesPreStateJoin = (p: Produce) =>
        preStateReader.getJoins(p.channelsHash).map(_.exists(_.size > 1))
      eventLogIndex <- EventLogIndex.apply(
                        events.map(EventConverter.toRspaceEvent),
                        produceExistsInPreState,
                        produceTouchesPreStateJoin,
                        mergeableChs
                      )
    } yield eventLogIndex

  def apply[F[_]: Concurrent, C, P, A, K](
      blockHash: BlockHash,
      usrProcessedDeploys: List[ProcessedDeploy],
      sysProcessedDeploys: List[ProcessedSystemDeploy],
      preStateHash: Blake2b256Hash,
      postStateHash: Blake2b256Hash,
      historyRepository: HistoryRepository[F, C, P, A, K],
      mergeableChanData: Seq[NumberChannelsDiff]
  ): F[BlockIndex] = {
    // Connect mergeable channels data with processed deploys by index
    val usrCount    = usrProcessedDeploys.size
    val sysCount    = sysProcessedDeploys.size
    val deployCount = usrCount + sysCount
    val mrgCount    = mergeableChanData.size

    // Number of deploys must match the size of mergeable channels maps
    assert(deployCount == mrgCount, {
      s"Cache of mergeable channels ($mrgCount) doesn't match deploys count ($deployCount)."
    })

    // Connect deploy with corresponding mergeable channels map
    val (usrDeploys, sysDeploys) = mergeableChanData.toVector
      .splitAt(usrCount)
      .bimap(usrProcessedDeploys.toVector.zip(_), sysProcessedDeploys.toVector.zip(_))

    for {
      usrDeployIndices <- usrDeploys
                           .filterNot(_._1.isFailed)
                           .traverse {
                             case (d, mergeChs) =>
                               DeployIndex(
                                 d.deploy.sig,
                                 d.cost.cost,
                                 d.deployLog,
                                 createEventLogIndex(_, historyRepository, preStateHash, mergeChs)
                               )
                           }
      sysDeploysData = sysDeploys
        .collect {
          case (Succeeded(log, SlashSystemDeployData(_)), mergeChs) =>
            (blockHash.concat(SYS_SLASH_DEPLOY_ID), SYS_SLASH_DEPLOY_COST, log, mergeChs)
          case (Succeeded(log, CloseBlockSystemDeployData), mergeChs) =>
            (
              blockHash.concat(SYS_CLOSE_BLOCK_DEPLOY_ID),
              SYS_CLOSE_BLOCK_DEPLOY_COST,
              log,
              mergeChs
            )
          case (Succeeded(log, Empty), mergeChs) =>
            (blockHash.concat(SYS_EMPTY_DEPLOY_ID), SYS_EMPTY_DEPLOY_COST, log, mergeChs)
        }
      sysDeployIndices <- sysDeploysData.traverse {
                           case (sig, cost, log, mergeChs) =>
                             DeployIndex(
                               sig,
                               cost,
                               log,
                               createEventLogIndex(
                                 _,
                                 historyRepository,
                                 preStateHash,
                                 mergeChs
                               )
                             )
                         }

      deployIndices = (usrDeployIndices ++ sysDeployIndices).toSet

      /** Here deploys from a single block are examined. Atm deploys in block are executed sequentially,
        * so all conflicts are resolved according to order of sequential execution.
        * Therefore there won't be any conflicts between event logs. But there can be dependencies. */
      depends = (l: DeployIndex, r: DeployIndex) =>
        EventLogMergingLogic.depends(l.eventLogIndex, r.eventLogIndex)
      dependencyMap = ConflictResolutionLogic.computeDependencyMap(
        deployIndices,
        deployIndices,
        depends
      )
      deployChains = ConflictResolutionLogic
        .computeGreedyNonIntersectingBranches[DeployIndex](deployIndices, dependencyMap)

      index <- deployChains.toVector
                .traverse(
                  DeployChainIndex(
                    blockHash.toBlake2b256Hash,
                    _,
                    preStateHash,
                    postStateHash,
                    historyRepository
                  )
                )
    } yield BlockIndex(blockHash, index)
  }
}
