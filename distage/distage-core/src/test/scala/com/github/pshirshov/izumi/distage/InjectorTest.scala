package com.github.pshirshov.izumi.distage

import com.github.pshirshov.izumi.distage.Fixtures.Case16.TestProviderModule
import com.github.pshirshov.izumi.distage.Fixtures._
import com.github.pshirshov.izumi.distage.model.Injector
import com.github.pshirshov.izumi.distage.model.definition.Binding.SingletonBinding
import com.github.pshirshov.izumi.distage.model.definition._
import com.github.pshirshov.izumi.distage.model.exceptions._
import com.github.pshirshov.izumi.distage.model.plan.ExecutableOp.{ImportDependency, WiringOp}
import com.github.pshirshov.izumi.distage.model.plan.PlanningFailure.ConflictingOperation
import com.github.pshirshov.izumi.distage.model.reflection.universe.RuntimeDIUniverse
import com.github.pshirshov.izumi.distage.model.reflection.universe.RuntimeDIUniverse.Tag
import com.github.pshirshov.izumi.distage.model.reflection.universe.RuntimeDIUniverse.TagK
import com.github.pshirshov.izumi.distage.model.reflection.universe.RuntimeDIUniverse.Wiring.UnaryWiring
import org.scalatest.WordSpec

import scala.language.higherKinds
import scala.util.Try

class InjectorTest extends WordSpec {

  def mkInjector(): Injector = Injectors.bootstrap()

  "DI planner" should {

    "maintain correct operation order" in {
      import Case1._
      val definition: ModuleBase = new ModuleDef {
        make[TestClass]
        make[TestDependency3]
        make[TestDependency0].from[TestImpl0]
        make[TestDependency1]
        make[TestCaseClass]
        make[TestInstanceBinding].from(TestInstanceBinding())
      }

      val injector = mkInjector()
      val plan = injector.plan(definition)
      assert(plan.steps.exists(_.isInstanceOf[ImportDependency]))

      intercept[ProvisioningException] {
        injector.produce(plan)
      }

      val fixedPlan = plan.flatMap {
        case ImportDependency(key, _) if key == RuntimeDIUniverse.DIKey.get[NotInContext] =>
          Seq(WiringOp.ReferenceInstance(
            key
            , UnaryWiring.Instance(RuntimeDIUniverse.SafeType.get[NotInContext], new NotInContext {})
          )
          )

        case op =>
          Seq(op)
      }
      injector.produce(fixedPlan)
    }

    "support multiple bindings" in {
      import Case1._
      val definition: ModuleBase = new ModuleDef {
        many[JustTrait].named("named.empty.set")

        many[JustTrait]
          .add[JustTrait]
          .add(new Impl1)

        many[JustTrait].named("named.set")
          .add(new Impl2())

        many[JustTrait].named("named.set")
          .add[Impl3]
      }

      val injector = mkInjector()
      val plan = injector.plan(definition)
      val context = injector.produce(plan)

      assert(context.get[Set[JustTrait]].size == 2)
      assert(context.get[Set[JustTrait]]("named.empty.set").isEmpty)
      assert(context.get[Set[JustTrait]]("named.set").size == 2)
    }

    "support named bindings" in {
      import Case1_1._
      val definition: ModuleBase = new ModuleDef {
        make[TestClass]
          .named("named.test.class")
        make[TestDependency0].from[TestImpl0Bad]
        make[TestDependency0].named("named.test.dependency.0")
          .from[TestImpl0Good]
        make[TestInstanceBinding].named("named.test")
          .from(TestInstanceBinding())
      }

      val injector = mkInjector()
      val plan = injector.plan(definition)
      val context = injector.produce(plan)

      assert(context.get[TestClass]("named.test.class").correctWired())
    }

    "support circular dependencies" in {
      import Case2._

      val definition: ModuleBase = new ModuleDef {
        make[Circular2]
        make[Circular1]
      }

      val injector = mkInjector()
      val plan = injector.plan(definition)
      val context = injector.produce(plan)

      assert(context.get[Circular1] != null)
      assert(context.get[Circular2] != null)
      assert(context.get[Circular2].arg != null)
    }

    "support circular dependencies in providers" in {
      import Case2._

      val definition: ModuleBase = new ModuleDef {
        make[Circular2].from { c: Circular1 => new Circular2(c) }
        make[Circular1].from { c: Circular2 => new Circular1 { override val arg: Circular2 = c } }
      }

      val injector = mkInjector()
      val plan = injector.plan(definition)
      val context = injector.produce(plan)

      assert(context.get[Circular1] != null)
      assert(context.get[Circular2] != null)
      assert(context.get[Circular2].arg != null)
    }

    "support complex circular dependencies" in {
      import Case3._

      val definition: ModuleBase = new ModuleDef {
        make[Circular3]
        make[Circular1]
        make[Circular2]
        make[Circular5]
        make[Circular4]
      }

      val injector = mkInjector()
      val plan = injector.plan(definition)
      val context = injector.produce(plan)
      val c3 = context.get[Circular3]
      val traitArg = c3.arg

      assert(traitArg != null && traitArg.isInstanceOf[Circular4])
      assert(c3.method == 2L)
      assert(traitArg.testVal == 1)
      assert(context.enumerate.nonEmpty)
      assert(context.get[Circular4].factoryFun(context.get[Circular4], context.get[Circular5]) != null)
    }

    "support more complex circular dependencies" in {
      import Case15._

      val definition: ModuleBase = new ModuleDef {
        make[CustomDep1].from(CustomDep1.empty)
        make[CustomTrait].from(customTraitInstance)
        make[CustomClass]
        make[CustomDep2]
        make[CustomApp]
      }

      val injector = mkInjector()
      val plan = injector.plan(definition)
      val context = injector.produce(plan)

      assert(context.get[CustomApp] != null)
    }

    "support generics" in {
      import Case11._

      val definition: ModuleBase = new ModuleDef {
        make[List[Dep]].named("As").from(List(DepA()))
        make[List[Dep]].named("Bs").from(List(DepB()))
        make[List[DepA]].from(List(DepA(), DepA(), DepA()))
        make[TestClass[DepA]]
      }

      val injector = mkInjector()
      val plan = injector.plan(definition)
      val context = injector.produce(plan)

      assert(context.get[List[Dep]]("As").forall(_.isInstanceOf[DepA]))
      assert(context.get[List[DepA]].forall(_.isInstanceOf[DepA]))
      assert(context.get[List[Dep]]("Bs").forall(_.isInstanceOf[DepB]))
      assert(context.get[TestClass[DepA]].inner == context.get[List[DepA]])
    }

    "support classes with typealiases" in {
      import Case11._

      val definition = new ModuleDef {
        make[DepA]
        make[TestClass2[TypeAliasDepA]]
      }

      val injector = mkInjector()
      val plan = injector.plan(definition)
      val context = injector.produce(plan)

      assert(context.get[TestClass2[TypeAliasDepA]].inner.isInstanceOf[TypeAliasDepA])
    }

    "support traits with typealiases" in {
      import Case11._

      val definition = new ModuleDef {
        make[DepA]
        make[TestTrait]
      }

      val injector = mkInjector()
      val plan = injector.plan(definition)
      val context = injector.produce(plan)

      assert(context.get[TestTrait].dep.isInstanceOf[TypeAliasDepA])
    }

    "support trait initialization" in {
      import Case3._

      val definition: ModuleBase = new ModuleDef {
        make[CircularBad1]
        make[CircularBad2]
      }

      val injector = mkInjector()
      val plan = injector.plan(definition)

      val exc = intercept[ProvisioningException] {
        injector.produce(plan)
      }
      assert(exc.getCause.isInstanceOf[TraitInitializationFailedException])
      assert(exc.getCause.getCause.isInstanceOf[RuntimeException])

    }

    "support trait fields" in {
      val definition: ModuleBase = new ModuleDef {
        make[Case9.ATraitWithAField]
      }

      val injector = mkInjector()
      val plan = injector.plan(definition)

      val context = injector.produce(plan)
      assert(context.get[Case9.ATraitWithAField].field == 1)
    }

    "fail on unbindable" in {
      import Case4._

      val definition: ModuleBase = new ModuleBase {
        override def bindings: Set[Binding] = Set(
          SingletonBinding(RuntimeDIUniverse.DIKey.get[Dependency], ImplDef.TypeImpl(RuntimeDIUniverse.SafeType.get[Long]))
        )
      }

      val injector = mkInjector()
      intercept[UnsupportedWiringException] {
        injector.plan(definition)
      }
      //assert(exc.badSteps.lengthCompare(1) == 0 && exc.badSteps.exists(_.isInstanceOf[UnbindableBinding]))
    }

    "fail on unsolvable conflicts" in {
      import Case4._

      val definition: ModuleBase = new ModuleDef {
        make[Dependency].from[Impl1]
        make[Dependency].from[Impl2]
      }

      val injector = mkInjector()
      val exc = intercept[UntranslatablePlanException] {
        injector.plan(definition)
      }
      assert(exc.badSteps.lengthCompare(1) == 0 && exc.badSteps.exists(_.isInstanceOf[ConflictingOperation]))
    }

    "handle factory injections" in {
      import Case5._

      val definition: ModuleBase = new ModuleDef {
        make[Factory]
        make[Dependency]
        make[OverridingFactory]
        make[AssistedFactory]
        make[AbstractFactory]
      }

      val injector = mkInjector()
      val plan = injector.plan(definition)
      val context = injector.produce(plan)

      val factory = context.get[Factory]
      assert(factory.wiringTargetForDependency != null)
      assert(factory.factoryMethodForDependency() != factory.wiringTargetForDependency)
      assert(factory.x().b.isInstanceOf[Dependency])

      val abstractFactory = context.get[AbstractFactory]
      assert(abstractFactory.x().isInstanceOf[AbstractDependencyImpl])

      val fullyAbstract1 = abstractFactory.y()
      val fullyAbstract2 = abstractFactory.y()
      assert(fullyAbstract1.isInstanceOf[FullyAbstractDependency])
      assert(fullyAbstract1.a.isInstanceOf[Dependency])
      assert(!fullyAbstract1.eq(fullyAbstract2))

      val overridingFactory = context.get[OverridingFactory]
      assert(overridingFactory.x(ConcreteDep()).b.isInstanceOf[ConcreteDep])

      val assistedFactory = context.get[AssistedFactory]
      assert(assistedFactory.x(1).a == 1)
      assert(assistedFactory.x(1).b.isInstanceOf[Dependency])
    }

    "handle generic arguments in cglib factory methods" in {
      import Case5._

      val definition: ModuleBase = new ModuleDef {
        make[GenericAssistedFactory]
        make[Dependency].from(ConcreteDep())
      }

      val injector = mkInjector()
      val plan = injector.plan(definition)
      val context = injector.produce(plan)

      val instantiated = context.get[GenericAssistedFactory]
      val product = instantiated.x(List(SpecialDep()), List(5))
      assert(product.a.forall(_.isSpecial))
      assert(product.b.forall(_ == 5))
      assert(product.c == ConcreteDep())
    }

    "handle named assisted dependencies in cglib factory methods" in {
      import Case5._

      val definition: ModuleBase = new ModuleDef {
        make[NamedAssistedFactory]
        make[Dependency]
        make[Dependency].named("special").from(SpecialDep())
        make[Dependency].named("veryspecial").from(VerySpecialDep())
      }

      val injector = mkInjector()
      val plan = injector.plan(definition)
      val context = injector.produce(plan)

      assert(!context.get[Dependency].isSpecial)
      assert(context.get[Dependency]("special").isSpecial)
      assert(context.get[Dependency]("veryspecial").isVerySpecial)

      val instantiated = context.get[NamedAssistedFactory]

      assert(instantiated.dep.isVerySpecial)
      assert(instantiated.x(5).b.isSpecial)
    }

    "cglib factory cannot produce factories" in {
      val fail = Try {
        import Case5._

        val definition: ModuleBase = new ModuleDef {
          make[FactoryProducingFactory]
          make[Dependency]
        }

        val injector = mkInjector()
        val plan = injector.plan(definition)
        val context = injector.produce(plan)

        val instantiated = context.get[FactoryProducingFactory]

        assert(instantiated.x().x().b == context.get[Dependency])
      }.isFailure
      assert(fail)
    }

    "cglib factory always produces new instances" in {
      import Case5._

      val definition: ModuleBase = new ModuleDef {
        make[Dependency]
        make[TestClass]
        make[Factory]
      }

      val injector = mkInjector()
      val plan = injector.plan(definition)
      val context = injector.produce(plan)

      val instantiated = context.get[Factory]

      assert(!instantiated.x().eq(context.get[TestClass]))
      assert(!instantiated.x().eq(instantiated.x()))
    }

    // BasicProvisionerTest
    "instantiate simple class" in {
      import Case1._
      val definition: ModuleBase = new ModuleDef {
        make[TestCaseClass2]
        make[TestInstanceBinding].from(new TestInstanceBinding)
      }

      val injector = mkInjector()
      val plan = injector.plan(definition)
      val context = injector.produce(plan)
      val instantiated = context.get[TestCaseClass2]

      assert(instantiated.a.z.nonEmpty)
    }

    "instantiate provider bindings" in {
      import Case6._

      val definition: ModuleBase = new ModuleDef {
        make[TestClass].from((a: Dependency1) => new TestClass(null))
        make[Dependency1].from(() => new Dependency1Sub {})
      }

      val injector = mkInjector()
      val plan = injector.plan(definition)

      val context = injector.produce(plan)
      assert(context.parent.exists(_.plan.steps.nonEmpty))
      val instantiated = context.get[TestClass]
      assert(instantiated.b == null)
    }

    "support named bindings in cglib traits" in {
      import Case10._

      val definition = new ModuleDef {
        make[Dep].named("A").from[DepA]
        make[Dep].named("B").from[DepB]
        make[Trait]
        make[Trait1]
      }

      val injector = mkInjector()
      val plan = injector.plan(definition)

      val context = injector.produce(plan)
      val instantiated = context.get[Trait]

      assert(instantiated.depA.isA)
      assert(!instantiated.depB.isA)

      val instantiated1 = context.get[Trait1]

      assert(instantiated1.depA.isA)
      assert(!instantiated1.depB.isA)
    }

    "type annotations in di keys do not result in different keys" in {
      import Case8._

      val definition = new ModuleDef {
        make[Dependency1 @Id("special")]
        make[Trait1]
      }

      val injector = mkInjector()
      val plan = injector.plan(definition)
      val context = injector.produce(plan)

      val instantiated = context.find[Dependency1]
      val instantiated1 = context.find[Dependency1 @Id("special")]
      assert(instantiated1.isDefined)
      assert(instantiated.isDefined)
    }

    "support named bindings in method reference providers" in {
      import Case17._

      val definition = new ModuleDef {
        make[TestDependency].named("classdeftypeann1")
        make[TestClass].from(implType _)
      }

      val injector = mkInjector()
      val plan = injector.plan(definition)
      val context = injector.produce(plan)

      val dependency = context.get[TestDependency]("classdeftypeann1")
      val instantiated = context.get[TestClass]

      assert(instantiated.a == dependency)
    }

    "support named bindings in lambda providers" in {
      import Case17._

      val definition = new ModuleDef {
        make[TestDependency].named("classdeftypeann1")
        make[TestClass].from { t: TestDependency@Id("classdeftypeann1") => new TestClass(t) }
      }

      val injector = mkInjector()
      val context = injector.produce(injector.plan(definition))

      val dependency = context.get[TestDependency]("classdeftypeann1")
      val instantiated = context.get[TestClass]

      assert(instantiated.a == dependency)
    }

    "populates implicit parameters in class constructor from explicit DI context instead of scala's implicit resolution" in {
      import Case13._

      val definition = new ModuleDef {
        make[TestClass]
        make[Dep]
        make[DummyImplicit].from[MyDummyImplicit]
      }

      val injector = mkInjector()
      val plan = injector.plan(definition)

      val context = injector.produce(plan)
      val instantiated = context.get[TestClass]

      assert(instantiated.dummyImplicit.isInstanceOf[MyDummyImplicit])
      assert(instantiated.dummyImplicit.asInstanceOf[MyDummyImplicit].imADummy)
    }

    "override protected defs in cglib traits" in {
      import Case14._

      val definition = new ModuleDef {
        make[TestTrait]
        make[Dep]
      }

      val injector = mkInjector()
      val plan = injector.plan(definition)

      val context = injector.produce(plan)
      val instantiated = context.get[TestTrait]

      assert(instantiated.rd == Dep().toString)
    }

    "handle set bindings ordering" in {
      import Case18._

      val definition = new ModuleDef {
        make[Service2]
        make[Service0]
        make[Service1]
        make[Service3]

        many[SetTrait]
          .add[SetImpl1]
          .add[SetImpl2]
          .add[SetImpl3]

        many[SetTrait].named("n1")
          .add[SetImpl1]
          .add[SetImpl2]
          .add[SetImpl3]

        many[SetTrait].named("n2")
          .add[SetImpl1]
          .add[SetImpl2]
          .add[SetImpl3]

        many[SetTrait].named("n3")
          .add[SetImpl1]
          .add[SetImpl2]
          .add[SetImpl3]
      }

      val injector = mkInjector()
      val plan = injector.plan(definition)

      val context = injector.produce(plan)

      assert(context.get[Service0].set.size == 3)
      assert(context.get[Service1].set.size == 3)
      assert(context.get[Service2].set.size == 3)
      assert(context.get[Service3].set.size == 3)
    }

    "Support TODO bindings" in {
      import Case1._

      val injector = mkInjector()

      val def1 = new ModuleDef {
        todo[TestDependency0]
      }
      val def2 = new ModuleDef {
        make[TestDependency0].todo
      }
      val def3 = new ModuleDef {
        make[TestDependency0].named("fug").todo
      }

      val plan1 = injector.plan(def1)
      val plan2 = injector.plan(def2)
      val plan3 = injector.plan(def3)

      assert(Try(injector.produce(plan1)).toEither.left.exists(_.getCause.isInstanceOf[TODOBindingException]))
      assert(Try(injector.produce(plan2)).toEither.left.exists(_.getCause.isInstanceOf[TODOBindingException]))
      assert(Try(injector.produce(plan3)).toEither.left.exists(_.getCause.isInstanceOf[TODOBindingException]))
    }

    "ModuleBuilder supports tags" in {
      import Case18._

      val definition = new ModuleDef {
        many[SetTrait].named("n1").tagged("A", "B")
          .add[SetImpl1].tagged("A")
//          .add[SetImpl1].tagged("B") // illegal now - considered same bindings
          .add[SetImpl2].tagged("B")
          .add[SetImpl3].tagged("A").tagged("B")

        make[Service1].tagged("CA").tagged("CB").from[Service1]

//        make[Service1].tagged("CC") // illegal now - considered same bindings
        make[Service2].tagged("CC")

        many[SetTrait].tagged("A", "B")
      }

      assert(definition.bindings.size == 7)
      assert(definition.bindings.count(_.tags == Set("A", "B")) == 3)
      assert(definition.bindings.count(_.tags == Set("CA", "CB")) == 1)
      assert(definition.bindings.count(_.tags == Set("CC")) == 1)
      assert(definition.bindings.count(_.tags == Set("A")) == 1)
      assert(definition.bindings.count(_.tags == Set("B")) == 1)
    }

    "Tags in different modules are merged" in {
      import Case1._

      val def1 = new ModuleDef {
        make[TestDependency0].tagged("a").tagged("b")
        // make[TestDependency0].tagged("b")
        // FIXME take note: This will be ignored, no tag "b" will be appended. However, before double definitions were illegal anyway...

        tag("1")
      }

      val def2 = new ModuleDef {
        tag("2")

        make[TestDependency0].tagged("x").tagged("y")
      }

      val definition = def1 ++ def2

      assert(definition.bindings.head.tags == Set("1", "2", "a", "b", "x", "y"))
    }

    "Tags in different overriden modules are merged" in {
      import Case1._

      val def1 = new ModuleDef {
        make[TestDependency0].tagged("a").tagged("b")

        tag("1")
      }

      val def2 = new ModuleDef {
        tag("2")

        make[TestDependency0].tagged("x").tagged("y")
      }

      val definition = def1 overridenBy def2

      assert(definition.bindings.head.tags == Set("1", "2", "a", "b", "x", "y"))
    }

    "set elements are the same as global bindings" in {
      import Case19._

      val definition = new ModuleDef {
        make[Service1]

        many[Service]
          .ref[Service1]
      }

      val injector = mkInjector()
      val plan = injector.plan(definition)

      val context = injector.produce(plan)
      val svc = context.get[Service1]
      val set = context.get[Set[Service]]
      assert(set.head eq svc)
    }

    "progression test: cglib can't handle class local path-dependent injections (macros can)" in {
      val fail = Try {
        val definition = new ModuleDef {
          make[TopLevelPathDepTest.TestClass]
          make[TopLevelPathDepTest.TestDependency]
        }

        val injector = mkInjector()
        val plan = injector.plan(definition)

        val context = injector.produce(plan)

        assert(context.get[TopLevelPathDepTest.TestClass].a != null)
      }.isFailure
      assert(fail)
    }

    "progression test: cglib can't handle inner path-dependent injections (macros can)" in {
      val fail = Try {
        new InnerPathDepTest().testCase
      }.isFailure
      assert(fail)
    }

    "progression test: cglib can't handle function local path-dependent injections" in {
      val fail = Try {
        import Case16._

        val testProviderModule = new TestProviderModule

        val definition = new ModuleDef {
          make[testProviderModule.TestClass]
          make[testProviderModule.TestDependency]
        }

        val injector = mkInjector()
        val plan = injector.plan(definition)

        val context = injector.produce(plan)

        assert(context.get[testProviderModule.TestClass].a.isInstanceOf[testProviderModule.TestDependency])
      }.isFailure
      assert(fail)
    }

    "support tagless final style module definitions" in {
      import Case20._

      case class Definition[F[_]: TagK: Pointed](getResult: Int) extends ModuleDef {
        // FIXME: hmmm, what to do with this
        make[Pointed[F]].from(Pointed[F])

        make[TestTrait].from[TestServiceClass[F]]
        make[TestServiceClass[F]]
        make[TestServiceTrait[F]]
        make[Int].named("TestService").from(getResult)
        make[F[String]].from { res: Int @Id("TestService") => Pointed[F].point(s"Hello $res!") }
        make[Either[String, Boolean]].from(Right(true))

//        FIXME: Nothing doesn't resolve properly yet when F is unknown...
//        make[F[Nothing]]
//        make[Either[String, F[Int]]].from(Right(Pointed[F].point(1)))
        make[F[Any]].from(Pointed[F].point(1: Any))
        make[Either[String, F[Int]]].from { fAnyInt: F[Any] => Right[String, F[Int]](fAnyInt.asInstanceOf[F[Int]]) }
        make[F[Either[Int, F[String]]]].from(Pointed[F].point(Right[Int, F[String]](Pointed[F].point("hello")): Either[Int, F[String]]))
      }

      val listInjector = mkInjector()
      val listPlan = listInjector.plan(Definition[List](5))
      val listContext = listInjector.produce(listPlan)

      assert(listContext.get[TestTrait].get == List(5))
      assert(listContext.get[TestServiceClass[List]].get == List(5))
      assert(listContext.get[TestServiceTrait[List]].get == List(10))
      assert(listContext.get[List[String]] == List("Hello 5!"))
      assert(listContext.get[List[Any]] == List(1))
      assert(listContext.get[Either[String, Boolean]] == Right(true))
      assert(listContext.get[Either[String, List[Int]]] == Right(List(1)))
      assert(listContext.get[List[Either[Int, List[String]]]] == List(Right(List("hello"))))

      val optionTInjector = mkInjector()
      val optionTPlan = optionTInjector.plan(Definition[OptionT[List, ?]](5))
      val optionTContext = optionTInjector.produce(optionTPlan)

      assert(optionTContext.get[TestTrait].get == OptionT(List(Option(5))))
      assert(optionTContext.get[TestServiceClass[OptionT[List, ?]]].get == OptionT(List(Option(5))))
      assert(optionTContext.get[TestServiceTrait[OptionT[List, ?]]].get == OptionT(List(Option(10))))
      assert(optionTContext.get[OptionT[List, String]] == OptionT(List(Option("Hello 5!"))))

      val idInjector = mkInjector()
      val idPlan = idInjector.plan(Definition[id](5))
      val idContext = idInjector.produce(idPlan)

      assert(idContext.get[TestTrait].get == 5)
      assert(idContext.get[TestServiceClass[id]].get == 5)
      assert(idContext.get[TestServiceTrait[id]].get == 10)
      assert(idContext.get[id[String]] == "Hello 5!")
    }

    "FIXME: Support [A, F[_]] type shape" in {
      import Case20._

      abstract class Parent[C: Tag, R[_]: TagK: Pointed] extends ModuleDef {
        make[TestProvider[C, R]]
      }

      assert(new Parent[Int, List]{}.bindings.head.key.tpe == RuntimeDIUniverse.SafeType.get[TestProvider[Int, List]])
    }

    "FIXME: Support [A, A, F[_]] type shape" in {
      import Case20._

      abstract class Parent[A: Tag, C: Tag, R[_]: TagK: Pointed] extends ModuleDef {
        make[TestProvider0[A, C, R]]
      }

      assert(new Parent[Int, Boolean, List]{}.bindings.head.key.tpe == RuntimeDIUniverse.SafeType.get[TestProvider0[Int, Boolean, List]])
    }

    "progression test: No proper support for [A, F[_], G[_]] type shape - false positives generated by scala's type lambda generation during implicit search - leaking unresolved WeakTypeTags" in {
      import Case20._

      abstract class Parent[A: Tag, F[_]: TagK, R[_]: TagK: Pointed] extends ModuleDef {
        make[TestProvider1[A, F, R]]
      }

      val fail = Try(assert(new Parent[Int, List, List]{}.bindings.head.key.tpe == RuntimeDIUniverse.SafeType.get[TestProvider1[Int, List, List]])).isFailure
      assert(fail)
    }



    "Handle multiple parameter lists" in {
      import Case21._

      val definition = new ModuleDef {
        make[TestDependency2]
        make[TestDependency1]
        make[TestDependency3]
        make[TestClass]
      }

      val injector = mkInjector()
      val plan = injector.plan(definition)
      val context = injector.produce(plan)

      assert(context.get[TestClass].a != null)
      assert(context.get[TestClass].b != null)
      assert(context.get[TestClass].c != null)
      assert(context.get[TestClass].d != null)
    }

    "Implicit parameters are injected from the DI context, not from Scala's lexical implicit scope" in {
      import Case21._

      val definition = new ModuleDef {
        implicit val testDependency3: TestDependency3 = new TestDependency3

        make[TestDependency1]
        make[TestDependency2]
        make[TestDependency3]
        make[TestClass]
      }

      val injector = mkInjector()
      val plan = injector.plan(definition)
      val context = injector.produce(plan)

      assert(context.get[TestClass].b == context.get[TestClass].d)
    }

  }

  class InnerPathDepTest extends TestProviderModule {
    private val definition = new ModuleDef {
      make[TestClass]
      make[TestDependency]
    }

    def testCase = {
      val injector = mkInjector()
      val plan = injector.plan(definition)

      val context = injector.produce(plan)

      assert(context.get[TestClass].a != null)
    }
  }

  object TopLevelPathDepTest extends TestProviderModule

}
