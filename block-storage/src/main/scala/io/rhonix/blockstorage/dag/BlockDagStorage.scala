package io.rhonix.blockstorage.dag

import com.google.protobuf.ByteString
import io.rhonix.blockstorage.dag.BlockDagStorage.DeployId
import io.rhonix.casper.protocol.{BlockMessage, DeployData}
import io.rhonix.crypto.signatures.Signed
import io.rhonix.models.BlockHash.BlockHash
import io.rhonix.models.BlockMetadata

trait BlockDagStorage[F[_]] {

  def getRepresentation: F[DagRepresentation]

  def insert(blockMetadata: BlockMetadata, block: BlockMessage): F[Unit]

  def lookup(blockHash: BlockHash): F[Option[BlockMetadata]]

  /* Deploys included in the DAG */

  def lookupByDeployId(blockHash: DeployId): F[Option[BlockHash]]

  /* Deploy pool, not processed (finalized) deploys */

  def addDeploy(d: Signed[DeployData]): F[Unit]
  def pooledDeploys: F[Map[DeployId, Signed[DeployData]]]
  def containsDeployInPool(deployId: DeployId): F[Boolean]
}

object BlockDagStorage {
  type DeployId = ByteString

  def apply[F[_]](implicit instance: BlockDagStorage[F]): BlockDagStorage[F] = instance
}
