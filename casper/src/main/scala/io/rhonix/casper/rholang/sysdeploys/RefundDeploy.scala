package io.rhonix.casper.rholang.sysdeploys

import io.rhonix.casper.rholang.types.{SystemDeploy, SystemDeployUserError}
import io.rhonix.crypto.hash.Blake2b512Random
import io.rhonix.models.NormalizerEnv.{Contains, ToEnvMap}
import io.rhonix.models.rholang.RhoType._

final class RefundDeploy(refundAmount: Long, rand: Blake2b512Random) extends SystemDeploy(rand) {
  import io.rhonix.models._
  import Expr.ExprInstance._
  import rholang.{implicits => toPar}
  import shapeless._
  import shapeless.syntax.singleton._

  type Output = (RhoBoolean, Either[RhoString, RhoNil])
  type Result = Unit

  val `sys:casper:refundAmount` = Witness("sys:casper:refundAmount")
  type `sys:casper:refundAmount` = `sys:casper:refundAmount`.T

  type Env =
    (`sys:casper:refundAmount` ->> GInt) :: (`sys:casper:authToken` ->> GSysAuthToken) :: (`sys:casper:return` ->> GUnforgeable) :: HNil

  import toPar._
  protected override val envsReturnChannel = Contains[Env, `sys:casper:return`]
  protected override val toEnvMap          = ToEnvMap[Env]
  protected override val normalizerEnv = new NormalizerEnv(
    ("sys:casper:refundAmount" ->> GInt(refundAmount)) :: mkSysAuthToken :: mkReturnChannel :: HNil
  )

  override val source: String =
    """#new rl(`rho:registry:lookup`),
       #  poSCh,
       #  refundAmount(`sys:casper:refundAmount`),
       #  sysAuthToken(`sys:casper:authToken`),
       #  return(`sys:casper:return`)
       #in {
       #  rl!(`rho:rhonix:pos`, *poSCh) |
       #  for(@(_, Pos) <- poSCh) {
       #    @Pos!("refundDeploy", *refundAmount, *sysAuthToken, *return)
       # }
       #}""".stripMargin('#')

  protected override val extractor = Extractor.derive

  protected def processResult(
      value: (Boolean, Either[String, Unit])
  ): Either[SystemDeployUserError, Unit] =
    value match {
      case (true, _)               => Right(())
      case (false, Left(errorMsg)) => Left(SystemDeployUserError(errorMsg))
      case _                       => Left(SystemDeployUserError("<no cause>"))
    }

}
