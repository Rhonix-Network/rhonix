package coop.rchain.rholang.interpreter

import cats.effect.Sync
import cats.syntax.all._
import cats.{Applicative, Monad}
import coop.rchain.models.Connective.ConnectiveInstance
import coop.rchain.models.Connective.ConnectiveInstance._
import coop.rchain.models.Expr.ExprInstance._
import coop.rchain.models.Var.VarInstance._
import coop.rchain.models._
import coop.rchain.models.rholang.implicits._
import coop.rchain.models.rholang.sorter._
import coop.rchain.models.rholangN.Bindings._
import coop.rchain.models.rholangN._
import coop.rchain.rholang.interpreter.accounting.CostAccounting.CostStateRef
import coop.rchain.rholang.interpreter.accounting._
import coop.rchain.rholang.interpreter.errors.SubstituteError

trait Substitute[M[_], A] {
  def substitute(term: A)(implicit depth: Int, env: Env[Par]): M[A]
  def substituteNoSort(term: A)(implicit depth: Int, env: Env[Par]): M[A]
}

object Substitute {
  private[interpreter] def charge[A: Chargeable, M[_]: Sync: CostStateRef](
      substitutionResult: M[A],
      failureCost: Cost
  ): M[A] =
    substitutionResult.attempt
      .map(
        _.fold(
          th => (Left(th), failureCost),
          substTerm => (Right(substTerm), Cost(substTerm, "substitution"))
        )
      )
      .flatMap({ case (result, cost) => accounting.charge[M](cost).as(result) })
      .rethrow

  def substituteAndCharge[A: Chargeable, M[_]: CostStateRef: Substitute[*[_], A]: Sync](
      term: A,
      depth: Int,
      env: Env[Par]
  ): M[A] =
    charge(Substitute[M, A].substitute(term)(depth, env), Cost(term))

  def substituteNoSortAndCharge[A: Chargeable, M[_]: CostStateRef: Substitute[*[_], A]: Sync](
      term: A,
      depth: Int,
      env: Env[Par]
  ): M[A] =
    charge(Substitute[M, A].substituteNoSort(term)(depth, env), Cost(term))

  def substitute2[M[_]: Monad, A, B, C](termA: A, termB: B)(
      f: (A, B) => C
  )(implicit evA: Substitute[M, A], evB: Substitute[M, B], depth: Int, env: Env[Par]): M[C] =
    (evA.substitute(termA), evB.substitute(termB)).mapN(f)

  def substituteNoSort2[M[_]: Monad, A, B, C](termA: A, termB: B)(
      f: (A, B) => C
  )(implicit evA: Substitute[M, A], evB: Substitute[M, B], depth: Int, env: Env[Par]): M[C] =
    (evA.substituteNoSort(termA), evB.substituteNoSort(termB)).mapN(f)

  def apply[M[_], A](implicit ev: Substitute[M, A]): Substitute[M, A] = ev

  def maybeSubstitute[M[_]: Sync](
      term: Var
  )(implicit depth: Int, env: Env[Par]): M[Either[Var, Par]] =
    if (depth != 0) term.asLeft[Par].pure[M]
    else
      term.varInstance match {
        case BoundVar(index) =>
          Sync[M].delay(env.get(index).toRight(left = term))
        case _ =>
          Sync[M].raiseError(SubstituteError(s"Illegal Substitution [$term]"))
      }

  def maybeSubstitute[M[_]: Sync](
      term: EVar
  )(implicit depth: Int, env: Env[Par]): M[Either[EVar, Par]] =
    maybeSubstitute[M](term.v).map(_.leftMap(EVar(_)))

  def maybeSubstitute[M[_]: Sync](
      term: VarRef
  )(implicit depth: Int, env: Env[Par]): M[Either[VarRef, Par]] =
    if (term.depth != depth) term.asLeft[Par].pure[M]
    else Sync[M].delay(env.get(term.index).toRight(left = term))

  implicit def substituteBundle[M[_]: Sync]: Substitute[M, Bundle] =
    new Substitute[M, Bundle] {
      import BundleOps._

      override def substitute(term: Bundle)(implicit depth: Int, env: Env[Par]): M[Bundle] =
        substitutePar[M].substitute(term.body).map { subBundle =>
          subBundle.singleBundle().map(term.merge).getOrElse(term.copy(body = subBundle))
        }

      override def substituteNoSort(term: Bundle)(implicit depth: Int, env: Env[Par]): M[Bundle] =
        substitutePar[M].substituteNoSort(term.body).map { subBundle =>
          subBundle.singleBundle().map(term.merge).getOrElse(term.copy(body = subBundle))
        }
    }

  implicit def substitutePar[M[_]: Sync]: Substitute[M, Par] =
    new Substitute[M, Par] {
      def subExp(exprs: Seq[Expr])(implicit depth: Int, env: Env[Par]): M[Par] =
        exprs.toList.reverse.foldM(VectorPar()) { (par, expr) =>
          expr.exprInstance match {
            case EVarBody(e) =>
              maybeSubstitute[M](e).map {
                case Left(_e)    => par.prepend(_e, depth)
                case Right(_par) => _par ++ par
              }
            case _ => substituteExpr[M].substituteNoSort(expr).map(par.prepend(_, depth))
          }
        }

      def subConn(conns: Seq[Connective])(implicit depth: Int, env: Env[Par]): M[Par] =
        conns.toList.reverse.foldM(VectorPar()) { (par, conn) =>
          conn.connectiveInstance match {
            case VarRefBody(v) =>
              maybeSubstitute[M](v).map {
                case Left(_)       => par.prepend(conn, depth)
                case Right(newPar) => newPar ++ par
              }
            case ConnectiveInstance.Empty => par.pure[M]
            case ConnAndBody(ConnectiveBody(ps)) =>
              ps.toVector
                .traverse(substitutePar[M].substituteNoSort(_))
                .map(ps => par.prepend(Connective(ConnAndBody(ConnectiveBody(ps))), depth))
            case ConnOrBody(ConnectiveBody(ps)) =>
              ps.toVector
                .traverse(substitutePar[M].substituteNoSort(_))
                .map(ps => par.prepend(Connective(ConnOrBody(ConnectiveBody(ps))), depth))
            case ConnNotBody(p) =>
              substitutePar[M]
                .substituteNoSort(p)
                .map(p => Connective(ConnNotBody(p)))
                .map(par.prepend(_, depth))
            case c: ConnBool      => par.prepend(Connective(c), depth).pure[M]
            case c: ConnInt       => par.prepend(Connective(c), depth).pure[M]
            case c: ConnBigInt    => par.prepend(Connective(c), depth).pure[M]
            case c: ConnString    => par.prepend(Connective(c), depth).pure[M]
            case c: ConnUri       => par.prepend(Connective(c), depth).pure[M]
            case c: ConnByteArray => par.prepend(Connective(c), depth).pure[M]
          }
        }

      override def substituteNoSort(term: Par)(implicit depth: Int, env: Env[Par]): M[Par] =
        for {
          _           <- Sync[M].delay(()) // TODO why removing this breaks StackSafetySpec?
          exprs       <- subExp(term.exprs)
          connectives <- subConn(term.connectives)
          sends       <- term.sends.toVector.traverse(substituteSend[M].substituteNoSort(_))
          bundles     <- term.bundles.toVector.traverse(substituteBundle[M].substituteNoSort(_))
          receives    <- term.receives.toVector.traverse(substituteReceive[M].substituteNoSort(_))
          news        <- term.news.toVector.traverse(substituteNew[M].substituteNoSort(_))
          matches     <- term.matches.toVector.traverse(substituteMatch[M].substituteNoSort(_))
          par = exprs ++
            connectives ++
            Par(
              exprs = Nil,
              sends = sends,
              bundles = bundles,
              receives = receives,
              news = news,
              matches = matches,
              unforgeables = term.unforgeables,
              connectives = Nil,
              locallyFree = term.locallyFree.rangeUntil(env.shift),
              connectiveUsed = term.connectiveUsed
            )
        } yield par
      override def substitute(term: Par)(implicit depth: Int, env: Env[Par]): M[Par] =
        substituteNoSort(term).flatMap(Sortable.sortMatch(_)).map(_.term)
    }

  implicit def substituteSend[M[_]: Sync]: Substitute[M, Send] =
    new Substitute[M, Send] {
      override def substituteNoSort(term: Send)(implicit depth: Int, env: Env[Par]): M[Send] =
        for {
          channelsSub <- substitutePar[M].substituteNoSort(term.chan)
          parsSub     <- term.data.traverse(substitutePar[M].substituteNoSort(_))
          send = SendN(
            chan = fromProto(channelsSub),
            data = fromProto(parsSub),
            persistent = term.persistent
          )
        } yield toProtoSend(send)
      override def substitute(term: Send)(implicit depth: Int, env: Env[Par]): M[Send] =
        substituteNoSort(term).flatMap(Sortable.sortMatch(_)).map(_.term)
    }

  implicit def substituteReceive[M[_]: Sync]: Substitute[M, Receive] =
    new Substitute[M, Receive] {
      override def substituteNoSort(term: Receive)(implicit depth: Int, env: Env[Par]): M[Receive] =
        for {
          bindsSub <- term.binds.traverse {
                       case ReceiveBind(patterns, chan, rem, freeCount) =>
                         for {
                           subChannel <- substitutePar[M].substituteNoSort(chan)
                           subPatterns <- patterns.toVector.traverse(
                                           pattern =>
                                             substitutePar[M]
                                               .substituteNoSort(pattern)(depth + 1, env)
                                         )
                         } yield ReceiveBindN(
                           fromProto(subPatterns),
                           fromProto(subChannel),
                           fromProtoVarOpt(rem),
                           freeCount
                         )
                     }
          bodySub <- substitutePar[M]
                      .substituteNoSort(term.body)(
                        depth,
                        env.shift(term.bindCount)
                      )
                      .map(fromProto)
          rec = ReceiveN(
            binds = bindsSub,
            body = bodySub,
            persistent = term.persistent,
            peek = term.peek,
            bindCount = term.bindCount
          )
        } yield toProtoReceive(rec)
      override def substitute(term: Receive)(implicit depth: Int, env: Env[Par]): M[Receive] =
        substituteNoSort(term).flatMap(Sortable.sortMatch(_)).map(_.term)

    }

  implicit def substituteNew[M[_]: Sync]: Substitute[M, New] =
    new Substitute[M, New] {
      private def fromProtoInjections(ps: Seq[(String, Par)]): Seq[(String, ParN)] =
        ps.map(kv => (kv._1, fromProto(kv._2)))

      override def substituteNoSort(term: New)(implicit depth: Int, env: Env[Par]): M[New] =
        substitutePar[M]
          .substituteNoSort(term.p)(depth, env.shift(term.bindCount))
          .map(
            newSub =>
              NewN(
                bindCount = term.bindCount,
                p = fromProto(newSub),
                uri = term.uri,
                injections = fromProtoInjections(term.injections.toSeq)
              )
          )
          .map(toProtoNew)
      override def substitute(term: New)(implicit depth: Int, env: Env[Par]): M[New] =
        substituteNoSort(term).flatMap(Sortable.sortMatch(_)).map(_.term)
    }

  implicit def substituteMatch[M[_]: Sync]: Substitute[M, Match] =
    new Substitute[M, Match] {
      override def substituteNoSort(term: Match)(implicit depth: Int, env: Env[Par]): M[Match] =
        for {
          targetSub <- substitutePar[M].substituteNoSort(term.target).map(fromProto)
          casesSub <- term.cases.toVector.traverse {
                       case MatchCase(_case, _par, freeCount) =>
                         for {
                           par <- substitutePar[M]
                                   .substituteNoSort(_par)(
                                     depth,
                                     env.shift(freeCount)
                                   )
                                   .map(fromProto)
                           subCase <- substitutePar[M]
                                       .substituteNoSort(_case)(depth + 1, env)
                                       .map(fromProto)
                         } yield MatchCaseN(subCase, par, freeCount)
                     }
          mat = MatchN(
            targetSub,
            casesSub
          )
        } yield toProtoMatch(mat)
      override def substitute(term: Match)(implicit depth: Int, env: Env[Par]): M[Match] =
        substituteNoSort(term).flatMap(mat => Sortable.sortMatch(mat)).map(_.term)
    }

  implicit def substituteExpr[M[_]: Sync]: Substitute[M, Expr] =
    new Substitute[M, Expr] {
      private[this] def substituteDelegate(
          term: ExprN,
          s1: ParN => M[ParN],
          s2: (ParN, ParN) => ((ParN, ParN) => ExprN) => M[ExprN]
      ): M[ExprN] =
        term match {
          case x: ENotN            => s1(x.p).map(ENotN(_))
          case x: ENegN            => s1(x.p).map(ENegN(_))
          case x: EMultN           => s2(x.p1, x.p2)(EMultN(_, _))
          case x: EDivN            => s2(x.p1, x.p2)(EDivN(_, _))
          case x: EModN            => s2(x.p1, x.p2)(EModN(_, _))
          case x: EPercentPercentN => s2(x.p1, x.p2)(EPercentPercentN(_, _))
          case x: EPlusN           => s2(x.p1, x.p2)(EPlusN(_, _))
          case x: EMinusN          => s2(x.p1, x.p2)(EMinusN(_, _))
          case x: EPlusPlusN       => s2(x.p1, x.p2)(EPlusPlusN(_, _))
          case x: EMinusMinusN     => s2(x.p1, x.p2)(EMinusMinusN(_, _))
          case x: ELtN             => s2(x.p1, x.p2)(ELtN(_, _))
          case x: ELteN            => s2(x.p1, x.p2)(ELteN(_, _))
          case x: EGtN             => s2(x.p1, x.p2)(EGtN(_, _))
          case x: EGteN            => s2(x.p1, x.p2)(EGteN(_, _))
          case x: EEqN             => s2(x.p1, x.p2)(EEqN(_, _))
          case x: ENeqN            => s2(x.p1, x.p2)(ENeqN(_, _))
          case x: EAndN            => s2(x.p1, x.p2)(EAndN(_, _))
          case x: EOrN             => s2(x.p1, x.p2)(EOrN(_, _))
          case x: EShortAndN       => s2(x.p1, x.p2)(EShortAndN(_, _))
          case x: EShortOrN        => s2(x.p1, x.p2)(EShortOrN(_, _))
          case x: EMatchesN        => s2(x.target, x.pattern)(EMatchesN(_, _))
          case x: EListN           => x.ps.toVector.traverse(s1).map(EListN(_, x.remainder))
          case x: ETupleN          => x.ps.toVector.traverse(s1).map(ETupleN(_))
          case x: ESetN            => x.sortedPs.toVector.traverse(s1).map(ESetN(_, x.remainder))
          case x: EMapN =>
            x.sortedPs.toVector.traverse(_.bimap(s1, s1).bisequence).map(EMapN(_, x.remainder))
          case x: EMethodN =>
            for {
              subTarget    <- s1(x.target)
              subArguments <- x.arguments.toVector.traverse(s1)
            } yield EMethodN(x.methodName, subTarget, subArguments)

          case g @ _ => Applicative[M].pure(term)
        }
      override def substitute(term: Expr)(implicit depth: Int, env: Env[Par]): M[Expr] =
        substituteDelegate(
          fromProtoExpr(term),
          p => substitutePar[M].substitute(toProto(p)).map(fromProto),
          (p11, p12) =>
            f =>
              substitute2(toProto(p11), toProto(p12))(
                (p21, p22) => f(fromProto(p21), fromProto(p22))
              )
        ).map(toProtoExpr)
      override def substituteNoSort(term: Expr)(implicit depth: Int, env: Env[Par]): M[Expr] =
        substituteDelegate(
          fromProtoExpr(term),
          p => substitutePar[M].substituteNoSort(toProto(p)).map(fromProto),
          (p11, p12) =>
            f =>
              substituteNoSort2(toProto(p11), toProto(p12))(
                (p21, p22) => f(fromProto(p21), fromProto(p22))
              )
        ).map(toProtoExpr)
    }
}
