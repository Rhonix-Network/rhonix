package io.rhonix.node.benchmark.utils

import cats.effect.Concurrent
import cats.syntax.all._
import com.google.protobuf.ByteString
import io.rhonix.casper.protocol.{DeployData, ProcessedDeploy, ProcessedSystemDeploy}
import io.rhonix.casper.rholang.sysdeploys.CloseBlockDeploy
import io.rhonix.casper.rholang.{BlockRandomSeed, RuntimeManager}
import io.rhonix.crypto.signatures.Signed
import io.rhonix.crypto.{PrivateKey, PublicKey}
import io.rhonix.models.block.StateHash.StateHash
import io.rhonix.models.syntax._
import io.rhonix.node.benchmark.utils
import io.rhonix.rholang.interpreter.SystemProcesses.BlockData
import io.rhonix.rholang.interpreter.util.RevAddress
import io.rhonix.shared.{Base16, Time}

object StateTransition {

  final case class StateTransitionResult(
      finalState: StateHash,
      paymentsDone: Seq[Charged[PaymentDeploy]],
      processedDeploys: Seq[ProcessedDeploy],
      processedSysDeploys: Seq[ProcessedSystemDeploy]
  )

  /** Make state transition */
  def make[F[_]: Concurrent: RuntimeManager: Time](
      baseState: StateHash,
      validator: PublicKey,
      seqNum: Int,
      blockNum: Long,
      payments: List[Payment]
  ): F[StateTransitionResult] = {
    val perValidatorVault =
      User(PrivateKey(ByteString.EMPTY), validator, Base16.encode(validator.bytes))

    def computeState(
        userDeploys: Seq[Signed[DeployData]]
    ): F[(StateHash, Seq[ProcessedDeploy], Seq[ProcessedSystemDeploy])] = {
      val rand =
        BlockRandomSeed.randomGenerator("shardId", blockNum, validator, baseState.toBlake2b256Hash)
      val cbRandomSeed = rand.splitByte(userDeploys.size.toByte)
      assert(userDeploys.nonEmpty, "Attempting to compute state without user deploys.")
      RuntimeManager[F]
        .computeState(baseState)(
          terms = userDeploys.distinct,
          systemDeploys = CloseBlockDeploy(cbRandomSeed) :: Nil,
          blockData = BlockData(blockNum, validator, seqNum.toLong),
          rand = rand
        )
    }
    for {
      paymentDeploys <- payments.traverse(Payment.mkTxDeploy(_, printDebug = false))
      r <- computeState(paymentDeploys.map(_.d)).map {
            case (s, processedDeploys, sp) =>
              assert(
                !processedDeploys.exists(_.isFailed),
                "Failed deploys found. Check if you users have enough REV to continue payments."
              )
              val charged =
                processedDeploys.map { d =>
                  val payerAddr = RevAddress.fromPublicKey(d.deploy.pk).get.address.toBase58
                  val charge = utils.Payment(
                    // TODO key not avail here, but not needed actually
                    User(PrivateKey(ByteString.EMPTY), d.deploy.pk, payerAddr),
                    perValidatorVault,
                    d.cost.cost
                  )
                  Charged(paymentDeploys.find(_.d.sig == d.deploy.sig).get, charge)
                }
              StateTransitionResult(s, charged, processedDeploys, sp)
          }
    } yield r
  }
}
