package com.github.pshirshov.izumi.distage.planning

import com.github.pshirshov.izumi.distage.model.Planner
import com.github.pshirshov.izumi.distage.model.definition.Binding.{EmptySetBinding, SetElementBinding, SingletonBinding}
import com.github.pshirshov.izumi.distage.model.definition.{Binding, ImplDef, ModuleBase}
import com.github.pshirshov.izumi.distage.model.plan.ExecutableOp.{CreateSet, WiringOp}
import com.github.pshirshov.izumi.distage.model.plan._
import com.github.pshirshov.izumi.distage.model.planning._
import com.github.pshirshov.izumi.distage.model.reflection.ReflectionProvider
import com.github.pshirshov.izumi.distage.model.reflection.universe.RuntimeDIUniverse
import com.github.pshirshov.izumi.distage.model.reflection.universe.RuntimeDIUniverse.Wiring.UnaryWiring._
import com.github.pshirshov.izumi.distage.model.reflection.universe.RuntimeDIUniverse.Wiring._
import com.github.pshirshov.izumi.functional.Value


class PlannerDefaultImpl
(
  protected val planResolver: PlanResolver
  , protected val forwardingRefResolver: ForwardingRefResolver
  , protected val reflectionProvider: ReflectionProvider.Runtime
  , protected val sanityChecker: SanityChecker
  , protected val planningObserver: PlanningObserver
  , protected val planMergingPolicy: PlanMergingPolicy
  , protected val planningHooks: Set[PlanningHook]
)
  extends Planner {
  private val hook = new PlanningHookAggregate(planningHooks)

  override def plan(context: ModuleBase): FinalPlan = {
    val plan = hook.hookDefinition(context).bindings.foldLeft(DodgyPlan.empty) {
      case (currentPlan, binding) =>
        Value(computeProvisioning(currentPlan, binding))
          .eff(sanityChecker.assertProvisionsSane)
          .map(planMergingPolicy.extendPlan(currentPlan, binding, _))
          .eff(sanityChecker.assertStepSane)
          .map(hook.hookStep(context, currentPlan, binding, _))
          .eff(planningObserver.onSuccessfulStep)
          .get
    }

    val finalPlan = Value(plan)
      .map(planMergingPolicy.resolve)
      .map(forwardingRefResolver.resolve)
      .eff(planningObserver.onReferencesResolved)
      .map(planResolver.resolve(_, context))
      .map(hook.hookResolved)
      .eff(planningObserver.onResolvingFinished)
      .eff(sanityChecker.assertFinalPlanSane)
      .map(hook.hookFinal)
      .eff(sanityChecker.assertFinalPlanSane)
      .eff(planningObserver.onFinalPlan)
      .get

    finalPlan
  }

  private def computeProvisioning(currentPlan: DodgyPlan, binding: Binding): NextOps = {
    binding match {
      case c: SingletonBinding[_] =>
        val newOps = provisioning(c)

        NextOps(
          Map.empty
          , Seq(newOps.op)
        )

      case s: SetElementBinding[_] =>
        val target = s.key
        val elementKey = RuntimeDIUniverse.DIKey.SetElementKey(target, currentPlan.operations.size, setElementKeySymbol(s.implementation))
        val next = computeProvisioning(currentPlan, SingletonBinding(elementKey, s.implementation))
        val oldSet = next.sets.getOrElse(target, CreateSet(s.key, s.key.tpe, Set.empty))
        val newSet = oldSet.copy(members = oldSet.members + elementKey)

        NextOps(
          next.sets.updated(target, newSet)
          , next.provisions
        )

      case s: EmptySetBinding[_] =>
        val newSet = CreateSet(s.key, s.key.tpe, Set.empty)

        NextOps(
          Map(s.key -> newSet)
          , Seq.empty
        )
    }
  }

  private def provisioning(binding: Binding.ImplBinding): Step = {
    val target = binding.key
    val wiring = hook.hookWiring(binding, bindingToWireable(binding))

    wiring match {
      case w: Constructor =>
        Step(wiring, WiringOp.InstantiateClass(target, w))

      case w: AbstractSymbol =>
        Step(wiring, WiringOp.InstantiateTrait(target, w))

      case w: FactoryMethod =>
        Step(wiring, WiringOp.InstantiateFactory(target, w))

      case w: FactoryFunction =>
        Step(wiring, WiringOp.CallFactoryProvider(target, w))

      case w: Function =>
        Step(wiring, WiringOp.CallProvider(target, w))

      case w: Instance =>
        Step(wiring, WiringOp.ReferenceInstance(target, w))

      case w: Reference =>
        Step(wiring, WiringOp.ReferenceKey(target, w))
    }
  }


  private def bindingToWireable(binding: Binding.ImplBinding): RuntimeDIUniverse.Wiring = {
    binding.implementation match {
      case i: ImplDef.TypeImpl =>
        reflectionProvider.symbolToWiring(i.implType)
      case p: ImplDef.ProviderImpl =>
        reflectionProvider.providerToWiring(p.function)
      case i: ImplDef.InstanceImpl =>
        UnaryWiring.Instance(i.implType, i.instance)
      case r: ImplDef.ReferenceImpl =>
        UnaryWiring.Reference(r.implType, r.key)
    }
  }

  private def setElementKeySymbol(impl: ImplDef): RuntimeDIUniverse.TypeFull = {
    impl match {
      case i: ImplDef.TypeImpl =>
        i.implType
      case i: ImplDef.InstanceImpl =>
        i.implType
      case p: ImplDef.ProviderImpl =>
        p.implType
      case r: ImplDef.ReferenceImpl =>
        r.implType
    }
  }
}
