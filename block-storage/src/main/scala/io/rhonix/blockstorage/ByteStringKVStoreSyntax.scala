package io.rhonix.blockstorage

import cats.effect.Sync
import cats.syntax.all._
import io.rhonix.casper.PrettyPrinter
import io.rhonix.models.Validator.Validator
import io.rhonix.store.KeyValueTypedStore
import io.rhonix.shared.syntax._

trait ByteStringKVStoreSyntax {
  implicit final def blockStorageSyntaxByteStringKVTypedStore[F[_], V](
      store: KeyValueTypedStore[F, Validator, V]
  ): ByteStringKVStoreOps[F, V] = new ByteStringKVStoreOps[F, V](store)
}

final case class ByteStringKVInconsistencyError(message: String) extends Exception(message)

final class ByteStringKVStoreOps[F[_], V](
    // KeyValueTypedStore extensions / syntax
    private val store: KeyValueTypedStore[F, Validator, V]
) extends AnyVal {
  def getUnsafe(key: Validator)(
      implicit f: Sync[F],
      line: sourcecode.Line,
      file: sourcecode.File,
      enclosing: sourcecode.Enclosing
  ): F[V] = {
    def source = s"${file.value}:${line.value} ${enclosing.value}"
    def errMsg = s"ByteStringKVStore is missing key ${PrettyPrinter.buildString(key)}\n $source"
    store.get1(key) >>= (_.liftTo(ByteStringKVInconsistencyError(errMsg)))
  }
}
