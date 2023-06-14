package io.rhonix.node.benchmark.utils

import cats.Functor
import cats.effect.Concurrent
import cats.syntax.all._
import fs2.Stream
import io.rhonix.casper.protocol.{BlockMessage, DeployData}
import io.rhonix.casper.rholang.RuntimeManager
import io.rhonix.casper.util.ConstructDeploy
import io.rhonix.crypto.signatures.Signed
import io.rhonix.models.block.StateHash.StateHash
import io.rhonix.node.benchmark.utils
import io.rhonix.node.revvaultexport.VaultBalanceGetter
import io.rhonix.rspace.hashing.Blake2b256Hash
import io.rhonix.shared.{Base16, Time}
import io.rhonix.shared.syntax._

import scala.util.Random

final case class Payment(
    source: User,
    dest: User,
    amt: Long,
    rejected: Boolean = false,
    timestamp: Long = Random.nextLong() // this is to make all payments different
)
final case class PaymentDeploy(d: Signed[DeployData], payment: Payment)
final case class Charged[A](v: A, charge: Payment)

object Payment {

  final case class BlockWithPayments(b: BlockMessage, payments: Seq[Charged[PaymentDeploy]])

  type BalanceSheet = Map[User, (Long, Seq[Payment])]

  val rnd = new Random(System.currentTimeMillis())

  def random(users: Seq[User], minTxAmt: Int, maxTxAmt: Int): Iterator[Payment] =
    Iterator.continually({
      val s   = users(rnd.nextInt(users.length))
      val t   = users(rnd.nextInt(users.length))
      val amt = minTxAmt + rnd.nextInt((maxTxAmt - minTxAmt) + 1).toLong
      utils.Payment(s, t, amt)
    })

  def randomBatches(
      users: Seq[User],
      minTxAmt: Int,
      maxTxAmt: Int,
      maxSize: Int
  ): Iterator[Seq[Payment]] = {
    require(maxSize > 0, "randomBatches accepts only positive maxSize")
    random(users, minTxAmt, maxTxAmt)
      .grouped(maxSize)
      .map(l => l.take(1 + rnd.nextInt(l.size)))
  }

  def conflictsPresent(payments: List[Seq[Payment]]): Boolean =
    payments
      .combinations(2)
      .filter {
        case List(l, r) =>
          // conflict present if the same sources or destinations are used
          ((l.map(v => v.dest) ++ l.map(v => v.source)) intersect (r.map(v => v.dest) ++ r.map(
            v => v.source
          ))).nonEmpty
      }
      .take(1)
      .nonEmpty

  def mkTxDeploy[F[_]: Functor: Time](
      payment: Payment,
      printDebug: Boolean = true
  ): F[PaymentDeploy] = {
    def txRho(payer: String, payee: String, amt: Long) = {
      val tx =
        if (printDebug) s"""@vault!("transfer", to, amount, *key, *resultCh) |
                          | for (@r <- resultCh) {
                          |   stdout!(("${payer} -> ${payee}", "${amt}", sender, r))
                          | }
                          |"""
        else s"""@vault!("transfer", to, amount, *key, *resultCh)"""

      s"""
         |new
         |  rl(`rho:registry:lookup`), stdout(`rho:io:stdout`),  revVaultCh, log, getBlockData(`rho:block:data`), blockDataCh
         |in {
         |  rl!(`rho:rchain:revVault`, *revVaultCh) |
         |  getBlockData!(*blockDataCh) |
         |  for (@(_, revVault) <- revVaultCh; _, _, @sender <- blockDataCh) {
         |    match ("${payer}", "${payee}", ${amt}) {
         |      (from, to, amount) => {
         |        new vaultCh, revVaultKeyCh, deployerId(`rho:rchain:deployerId`) in {
         |          @revVault!("findOrCreate", from, *vaultCh) |
         |          @revVault!("deployerAuthKey", *deployerId, *revVaultKeyCh) |
         |          for (@(true, vault) <- vaultCh; key <- revVaultKeyCh) {
         |            new resultCh, r in { 
         |              ${tx}
         |            }
         |          }
         |        }
         |      }
         |    }
         |  }
         |}""".stripMargin
    }

    val payerKey  = payment.source.sk
    val payerAddr = payment.source.addr
    val payeeAddr = payment.dest.addr
    val amt       = payment.amt
    ConstructDeploy
      .sourceDeployNowF[F](txRho(payerAddr, payeeAddr, amt), sec = payerKey)
      .map(PaymentDeploy(_, payment))
  }

  def verifyBalances[F[_]: Concurrent](
      balances: Iterator[(User, (Long, Seq[Payment]))],
      state: StateHash
  )(
      implicit runtimeManager: RuntimeManager[F]
  ): F[Unit] = {

    def getVault(addr: String): String =
      s"""new return, rl(`rho:registry:lookup`), RevVaultCh, vaultCh, balanceCh in {
         |  rl!(`rho:rchain:revVault`, *RevVaultCh) |
         |  for (@(_, RevVault) <- RevVaultCh) {
         |    @RevVault!("findOrCreate", "${addr}", *vaultCh) |
         |    for (@(true, vault) <- vaultCh) {
         |      return!(vault)
         |    }
         |  }
         |}
         |""".stripMargin

    Stream
      .fromIterator(balances)
      .parEvalMapProcBounded {
        case (User(_, _, addr), (paperBalance, txs)) =>
          for {
            vaultPar    <- runtimeManager.playExploratoryDeploy(getVault(addr), state)
            runtime     <- runtimeManager.spawnRuntime
            _           <- runtime.reset(Blake2b256Hash.fromByteString(state))
            realBalance <- VaultBalanceGetter.getBalanceFromVaultPar(vaultPar.head, runtime)
            errMsg      = s"""
               |Balance verification for ${addr} failed.               
               |State balance = ${realBalance.get} (${Base16.encode(state.toByteArray)} ), paper balance = $paperBalance.              
               |Transfers list: 
               |${txs.mkString("\n")}
               |""".stripMargin
            _           <- new Exception(errMsg).raiseError.unlessA(realBalance.contains(paperBalance))
          } yield ()
      }
      .compile
      .lastOrError
  }
}
