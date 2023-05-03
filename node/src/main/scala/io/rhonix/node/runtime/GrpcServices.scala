package io.rhonix.node.runtime

import cats.effect.Concurrent
import io.rhonix.casper.api.{BlockApi, BlockReportApi}
import io.rhonix.casper.protocol.deploy.v1.DeployServiceV1GrpcMonix
import io.rhonix.casper.protocol.propose.v1.ProposeServiceV1GrpcMonix
import io.rhonix.monix.Monixable
import io.rhonix.node.api.{DeployGrpcServiceV1, ProposeGrpcServiceV1, ReplGrpcService}
import io.rhonix.node.model.repl.ReplGrpcMonix
import io.rhonix.rholang.interpreter.RhoRuntime
import io.rhonix.shared.Log
import monix.execution.Scheduler

final case class GrpcServices(
    deploy: DeployServiceV1GrpcMonix.DeployService,
    propose: ProposeServiceV1GrpcMonix.ProposeService,
    repl: ReplGrpcMonix.Repl
)

object GrpcServices {
  def build[F[_]: Monixable: Concurrent: Log](
      blockApi: BlockApi[F],
      blockReportAPI: BlockReportApi[F],
      runtime: RhoRuntime[F]
  )(implicit mainScheduler: Scheduler): GrpcServices = {
    val repl    = ReplGrpcService(runtime, mainScheduler)
    val deploy  = DeployGrpcServiceV1(blockApi, blockReportAPI)
    val propose = ProposeGrpcServiceV1(blockApi)

    GrpcServices(deploy, propose, repl)
  }
}
