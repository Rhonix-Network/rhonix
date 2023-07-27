package coop.rchain.models.rholangN.ParManager

import coop.rchain.models.rholangN._

private[ParManager] object EvalRequired {
  private def eReq(p: RhoTypeN): Boolean       = p.evalRequired
  private def eReq(ps: Seq[RhoTypeN]): Boolean = ps.exists(eReq)

  def evalRequiredFn(p: RhoTypeN): Boolean = p match {

    /** Par */
    case pProc: ParProcN => eReq(pProc.ps)

    /** Basic types */
    case _: BasicN => true

    /** Ground types */
    case _: GroundN => false

    /** Collections */
    case eList: EListN   => eReq(eList.ps)
    case eTuple: ETupleN => eReq(eTuple.ps)

    /** Vars */
    case _: VarN => true

    /** Unforgeable names */
    case _: UnforgeableN => false

    /** Operations */
    case _: OperationN => true

    /** Bundle */
    /** Connective */
    /** Auxiliary types */
    case _: ReceiveBindN => true
    case _: MatchCaseN   => true

    /** Other types */
    case _: SysAuthToken => false

    case _ =>
      assert(assertion = false, "Not defined type")
      false
  }
}
