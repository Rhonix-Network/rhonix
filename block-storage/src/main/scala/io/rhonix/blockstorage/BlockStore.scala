package io.rhonix.blockstorage

import cats.effect.Sync
import cats.syntax.all._
import io.rhonix.blockstorage.dag.codecs.{codecBlockHash, codecBlockMessage}
import io.rhonix.casper.protocol.{BlockMessage, BlockMessageProto}
import io.rhonix.models.BlockHash.BlockHash
import io.rhonix.shared.Compression
import io.rhonix.shared.syntax._
import io.rhonix.store.{KeyValueStoreManager, KeyValueTypedStore}
import net.jpountz.lz4.{LZ4CompressorWithLength, LZ4DecompressorWithLength}

object BlockStore {
  type BlockStore[F[_]] = KeyValueTypedStore[F, BlockHash, BlockMessage]

  def apply[F[_]](implicit instance: BlockStore[F]): instance.type = instance

  def apply[F[_]: Sync](kvm: KeyValueStoreManager[F]): F[BlockStore[F]] =
    kvm
      .store("blocks")
      .map(_.toTypedStore[BlockHash, BlockMessage](codecBlockHash, codecBlockMessage))

  def bytesToBlockMessage(bytes: Array[Byte]): Either[String, BlockMessage] =
    BlockMessage
      .from(BlockMessageProto.parseFrom(decompressBytes(bytes)))

  def blockMessageToBytes(blockMessage: BlockMessage): Array[Byte] =
    compressBytes(blockMessage.toProto.toByteArray)

  // Compression

  val compressor = new LZ4CompressorWithLength(Compression.factory.fastCompressor())
  // val compressor = new LZ4CompressorWithLength(factory.highCompressor(17)) // Max compression
  val decompressor = new LZ4DecompressorWithLength(Compression.factory.fastDecompressor())

  def compressBytes(bytes: Array[Byte]): Array[Byte]   = compressor.compress(bytes)
  def decompressBytes(bytes: Array[Byte]): Array[Byte] = decompressor.decompress(bytes)
}
