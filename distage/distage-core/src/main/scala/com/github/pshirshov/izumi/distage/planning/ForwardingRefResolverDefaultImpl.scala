package com.github.pshirshov.izumi.distage.planning

import com.github.pshirshov.izumi.distage.model.plan.ExecutableOp.ProxyOp
import com.github.pshirshov.izumi.distage.model.plan.{ResolvedCyclesPlan, ResolvedSetsPlan}
import com.github.pshirshov.izumi.distage.model.planning.{ForwardingRefResolver, PlanAnalyzer}
import com.github.pshirshov.izumi.distage.model.reflection.universe.RuntimeDIUniverse

import scala.collection.mutable


class ForwardingRefResolverDefaultImpl
(
  protected val planAnalyzer: PlanAnalyzer
) extends ForwardingRefResolver {
  override def resolve(plan: ResolvedSetsPlan): ResolvedCyclesPlan = {
    val statements = plan.statements
    val reftable = planAnalyzer.computeFwdRefTable(statements)

    import reftable._

    val proxies = mutable.HashMap[RuntimeDIUniverse.DIKey, ProxyOp.MakeProxy]()

    val resolvedSteps = plan.steps.flatMap {
      case step if dependenciesOf.contains(step.target) =>
        val op = ProxyOp.MakeProxy(step, dependenciesOf(step.target))
        proxies += (step.target -> op)
        Seq(op)

      case step =>
        Seq(step)
    }

    val proxyOps = proxies.foldLeft(Seq.empty[ProxyOp.InitProxy]) {
      case (acc, (proxyKey, proxyDep)) =>
        acc :+ ProxyOp.InitProxy(proxyKey, proxyDep.forwardRefs, proxies(proxyKey))
    }

    ResolvedCyclesPlan(imports = plan.imports, steps = resolvedSteps ++ proxyOps, issues = plan.issues)
  }
}
