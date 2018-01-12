package org.bitbucket.pshirshov.izumi.di.planning

import org.bitbucket.pshirshov.izumi.di.model.plan.DodgyOp.Statement
import org.bitbucket.pshirshov.izumi.di.model.plan.DodgyPlan
import org.bitbucket.pshirshov.izumi.di.model.plan.ExecutableOp.{InitProxies, MakeProxy}



class ForwardingRefResolverDefaultImpl extends ForwardingRefResolver with WithPlanAnalysis {
  override def resolve(plan: DodgyPlan): DodgyPlan = {
    val reftable = computeFwdRefTable(plan.steps.collect { case Statement(op) => op }.toStream)

    import reftable._

    val resolvedSteps = plan.steps.flatMap {
      case Statement(step) if dependencies.contains(step.target) =>
        Seq(Statement(MakeProxy(step, dependencies(step.target))))

      case s@Statement(step) if dependants.contains(step.target) =>
        Seq(Statement(InitProxies(step, dependants(step.target))))


      case step =>
        Seq(step)
    }
    DodgyPlan(resolvedSteps)
  }
}