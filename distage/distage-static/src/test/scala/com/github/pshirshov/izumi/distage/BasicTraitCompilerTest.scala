package com.github.pshirshov.izumi.distage

import com.github.pshirshov.izumi.distage.model.reflection.universe.RuntimeDIUniverse._
import com.github.pshirshov.izumi.distage.provisioning.AnyConstructor
import org.scalatest.WordSpec

class BasicTraitCompilerTest extends WordSpec {

  trait Aaa {
    def a: Int
    def b: Boolean
  }

  "Trait compiler (whitebox tests)" should {
    "construct a basic trait" in {
      val traitCtor = AnyConstructor[Aaa].provider.get

      val value = traitCtor.unsafeApply(TypedRef(5), TypedRef(false)).asInstanceOf[Aaa]

      assert(value.a == 5)
      assert(value.b == false)

    }
  }

}
