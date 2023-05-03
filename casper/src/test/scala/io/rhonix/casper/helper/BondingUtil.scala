package io.rhonix.casper.helper

import cats.Functor
import cats.syntax.functor._
import io.rhonix.casper.protocol.DeployData
import io.rhonix.casper.util.ConstructDeploy
import io.rhonix.crypto.PrivateKey
import io.rhonix.crypto.signatures.Signed
import io.rhonix.shared.Time

object BondingUtil {
  def bondingDeploy[F[_]: Functor: Time](
      amount: Long,
      privateKey: PrivateKey,
      shardId: String = ""
  ): F[Signed[DeployData]] =
    ConstructDeploy
      .sourceDeployNowF(
        s"""
         |new retCh, PosCh, rl(`rho:registry:lookup`), stdout(`rho:io:stdout`), deployerId(`rho:rhonix:deployerId`) in {
         |  rl!(`rho:rhonix:pos`, *PosCh) |
         |  for(@(_, Pos) <- PosCh) {
         |    @Pos!("bond", *deployerId, $amount, *retCh)
         |  }
         |}
         |""".stripMargin,
        shardId = shardId,
        sec = privateKey
      )
}
