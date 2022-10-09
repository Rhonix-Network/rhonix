package io.rhonix.comm.transport

import com.google.protobuf.ByteString
import io.rhonix.comm.protocol.routing._
import monix.eval.Task
import monix.execution.Scheduler
import org.scalacheck.Gen
import org.scalatest.funspec.AnyFunSpec
import org.scalatest.matchers.should.Matchers
import org.scalatestplus.scalacheck.ScalaCheckDrivenPropertyChecks

import scala.collection.concurrent.TrieMap
import scala.util.Random

class PacketStoreRestoreSpec extends AnyFunSpec with Matchers with ScalaCheckDrivenPropertyChecks {

  import PacketOps._

  implicit val scheduler: Scheduler = Scheduler.Implicits.global

  describe("Packet store & restore") {
    it("should store and restore to the original Packet") {
      forAll(contentGen) { content: Array[Byte] =>
        // given
        val cache  = TrieMap[String, Array[Byte]]()
        val packet = Packet("Test", ByteString.copyFrom(content))
        // when
        val storedIn = packet.store[Task](cache).runSyncUnsafe().right.get
        val restored = PacketOps.restore[Task](storedIn, cache).runSyncUnsafe().right.get
        // then
        packet shouldBe restored
      }
    }
  }

  val contentGen: Gen[Array[Byte]] =
    for (n <- Gen.choose(10, 50000))
      yield Array.fill(n)((Random.nextInt(256) - 128).toByte)
}
