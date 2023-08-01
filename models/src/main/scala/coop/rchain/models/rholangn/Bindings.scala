package coop.rchain.models.rholangn

import coop.rchain.models.Expr.ExprInstance.GBool
import coop.rchain.models._

object Bindings {
  def toProto(p: ParN): Par                             = BindingsToProto.toProto(p)
  def toProto(ps: Seq[ParN]): Seq[Par]                  = ps.map(toProto)
  def toProto(pOpt: Option[ParN]): Option[Par]          = pOpt.map(toProto)
  def toProtoVarOpt(pOpt: Option[VarN]): Option[Var]    = pOpt.map(BindingsToProto.toVar)
  def toProtoExpr(e: ExprN): Expr                       = BindingsToProto.toExpr(e)
  def toProtoVar(v: VarN): Var                          = BindingsToProto.toVar(v)
  def toProtoUnforgeable(u: UnforgeableN): GUnforgeable = BindingsToProto.toUnforgeable(u)
  def toProtoConnective(c: ConnectiveN): Connective     = BindingsToProto.toConnective(c)

  def toProtoSend(x: SendN): Send          = BindingsToProto.toSend(x)
  def toProtoReceive(x: ReceiveN): Receive = BindingsToProto.toReceive(x)
  def toProtoMatch(x: MatchN): Match       = BindingsToProto.toMatch(x)
  def toProtoNew(x: NewN): New             = BindingsToProto.toNew(x)
  def toProtoBool(x: GBoolN): GBool        = BindingsToProto.toGBool(x)

  def fromProto(p: Par): ParN                             = BindingsFromProto.fromProto(p)
  def fromProto(ps: Seq[Par]): Seq[ParN]                  = ps.map(fromProto)
  def fromProto(pOpt: Option[Par]): Option[ParN]          = pOpt.map(fromProto)
  def fromProtoVarOpt(pOpt: Option[Var]): Option[VarN]    = pOpt.map(BindingsFromProto.fromVar)
  def fromProtoExpr(e: Expr): ExprN                       = BindingsFromProto.fromExpr(e)
  def fromProtoVar(v: Var): VarN                          = BindingsFromProto.fromVar(v)
  def fromProtoUnforgeable(u: GUnforgeable): UnforgeableN = BindingsFromProto.fromUnforgeable(u)
  def fromProtoConnective(c: Connective): ConnectiveN     = BindingsFromProto.fromConnective(c)

  def fromProtoSend(x: Send): SendN          = BindingsFromProto.fromSend(x)
  def fromProtoReceive(x: Receive): ReceiveN = BindingsFromProto.fromReceive(x)
  def fromProtoMatch(x: Match): MatchN       = BindingsFromProto.fromMatch(x)
  def fromProtoNew(x: New): NewN             = BindingsFromProto.fromNew(x)
  def fromProtoBool(x: GBool): GBoolN        = BindingsFromProto.fromGBool(x)

}
