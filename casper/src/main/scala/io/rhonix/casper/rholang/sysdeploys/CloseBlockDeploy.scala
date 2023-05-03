package io.rhonix.casper.rholang.sysdeploys

import io.rhonix.casper.rholang.types.{SystemDeploy, SystemDeployUserError}
import io.rhonix.crypto.hash.Blake2b512Random
import io.rhonix.models.NormalizerEnv.{Contains, ToEnvMap}
import io.rhonix.models.rholang.RhoType._

// Currently we use parentHash as initial random seed
final case class CloseBlockDeploy(initialRand: Blake2b512Random) extends SystemDeploy(initialRand) {
  import io.rhonix.models._
  import rholang.{implicits => toPar}
  import shapeless._

  type Output = (RhoBoolean, Either[RhoString, RhoNil])
  type Result = Unit

  val `sys:casper:closeBlock` = Witness("sys:casper:closeBlock")
  type `sys:casper:closeBlock` = `sys:casper:closeBlock`.T

  import toPar._
  type Env =
    (`sys:casper:authToken` ->> GSysAuthToken) :: (`sys:casper:return` ->> GUnforgeable) :: HNil
  protected override val envsReturnChannel = Contains[Env, `sys:casper:return`]
  protected override val toEnvMap          = ToEnvMap[Env]

  protected val normalizerEnv: NormalizerEnv[Env] = new NormalizerEnv(
    mkSysAuthToken :: mkReturnChannel :: HNil
  )

  override val source: String =
    """#new rl(`rho:registry:lookup`),
      #  poSCh,
      #  sysAuthToken(`sys:casper:authToken`),
      #  return(`sys:casper:return`)
      #in {
      #  rl!(`rho:rhonix:pos`, *poSCh) |
      #  for(@(_, Pos) <- poSCh) {
      #    @Pos!("closeBlock", *sysAuthToken, *return)
      #  }
      #}""".stripMargin('#')

  protected override val extractor = Extractor.derive

  protected override def processResult(
      value: (Boolean, Either[String, Unit])
  ): Either[SystemDeployUserError, Unit] = value match {
    case (true, _)               => Right(())
    case (false, Left(errorMsg)) => Left(SystemDeployUserError(errorMsg))
    case _                       => Left(SystemDeployUserError("<no cause>"))
  }
}
