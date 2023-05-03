package io.rhonix.casper.rholang
import io.rhonix.casper.protocol.{ProcessedDeploy, ProcessedSystemDeploy}
import io.rhonix.rholang.interpreter.EvaluateResult
import io.rhonix.rspace.merger.EventLogMergingLogic.NumberChannelsEndVal

object RuntimeDeployResult {
  final case class UserDeployRuntimeResult(
      deploy: ProcessedDeploy,
      mergeable: NumberChannelsEndVal,
      evalResult: EvaluateResult
  )
  final case class SystemDeployRuntimeResult(
      deploy: ProcessedSystemDeploy,
      mergeable: NumberChannelsEndVal
  )
}
