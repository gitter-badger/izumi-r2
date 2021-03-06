package com.github.pshirshov.izumi.distage.model.planning

import com.github.pshirshov.izumi.distage.model.definition.Binding
import com.github.pshirshov.izumi.distage.model.plan.{DodgyPlan, NextOps, ResolvedSetsPlan}

trait PlanMergingPolicy {
  def extendPlan(currentPlan: DodgyPlan, binding: Binding, currentOp: NextOps): DodgyPlan
  def resolve(completedPlan: DodgyPlan): ResolvedSetsPlan

}
