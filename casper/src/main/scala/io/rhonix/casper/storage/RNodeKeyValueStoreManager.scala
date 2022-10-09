package io.rhonix.casper.storage

import cats.effect.Concurrent
import io.rhonix.shared.Log
import io.rhonix.store.LmdbDirStoreManager.{gb, tb, Db, LmdbEnvConfig}
import io.rhonix.store.{KeyValueStoreManager, LmdbDirStoreManager}

import java.nio.file.Path

object RNodeKeyValueStoreManager {
  def apply[F[_]: Concurrent: Log](dirPath: Path): F[KeyValueStoreManager[F]] =
    LmdbDirStoreManager[F](dirPath, rnodeDbMapping.toMap)

  // Config name is used as a sub-folder for LMDB files

  // RSpace
  private val rspaceHistoryEnvConfig = LmdbEnvConfig(name = "rspace/history", maxEnvSize = 1 * tb)
  private val rspaceColdEnvConfig    = LmdbEnvConfig(name = "rspace/cold", maxEnvSize = 1 * tb)
  // RSpace evaluator
  private val evalHistoryEnvConfig = LmdbEnvConfig(name = "eval/history", maxEnvSize = 1 * tb)
  private val evalColdEnvConfig    = LmdbEnvConfig(name = "eval/cold", maxEnvSize = 1 * tb)
  // Blocks
  private val blockStorageEnvConfig = LmdbEnvConfig(name = "blockstorage", maxEnvSize = 1 * tb)
  private val dagStorageEnvConfig   = LmdbEnvConfig(name = "dagstorage", maxEnvSize = 100 * gb)
  private val deployPoolEnvConfig   = LmdbEnvConfig(name = "deploypoolstorage", maxEnvSize = 1 * gb)
  // Temporary storage / cache
  private val reportingEnvConfig   = LmdbEnvConfig(name = "reporting", maxEnvSize = 10 * tb)
  private val transactionEnvConfig = LmdbEnvConfig(name = "transaction")

  // Database name to store instance name mapping (sub-folder for LMDB store)
  // - keys with the same instance will be in one LMDB file (environment)
  def rnodeDbMapping: Seq[(Db, LmdbEnvConfig)] =
    Seq(
      // Block storage
      (Db("blocks"), blockStorageEnvConfig),
      // Block metadata storage
      (Db("block-metadata"), dagStorageEnvConfig),
      (Db("fringe-data"), dagStorageEnvConfig),
      (Db("finalized-store"), dagStorageEnvConfig),
      // Deploys from blocks
      (Db("deploy-index"), dagStorageEnvConfig),
      // Runtime mergeable store (cache of mergeable channels for block-merge)
      (Db("mergeable-channel-cache"), dagStorageEnvConfig),
      // Deploys waiting to be added
      (Db("deploy-pool"), deployPoolEnvConfig),
      // Reporting (trace) cache
      (Db("reporting-cache"), reportingEnvConfig),
      // On-chain RSpace (Rholang state)
      // - history and roots maps are part of the same LMDB file (environment)
      (Db("rspace-history"), rspaceHistoryEnvConfig),
      (Db("rspace-roots"), rspaceHistoryEnvConfig),
      (Db("rspace-cold"), rspaceColdEnvConfig),
      // Transaction store
      (Db("transaction"), transactionEnvConfig),
      // Evaluator RSpace (Rholang state)
      (Db("eval-history"), evalHistoryEnvConfig),
      (Db("eval-roots"), evalHistoryEnvConfig),
      (Db("eval-cold"), evalColdEnvConfig)
    )
}
