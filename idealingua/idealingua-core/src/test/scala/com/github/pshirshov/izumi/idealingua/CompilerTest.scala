package com.github.pshirshov.izumi.idealingua

import com.github.pshirshov.izumi.fundamentals.platform.files.IzFiles
import org.scalatest.WordSpec


class CompilerTest extends WordSpec {

  import IDLTestTools._

  "IDL compiler" should {
    "be able to compile into scala" in {
      assume(IzFiles.haveExecutables("scalac"), "scalac not available")
      assert(compilesScala(getClass.getSimpleName, loadDefs()))
    }
    "be able to compile into typescript" in {
      assume(false, "TS SUPPORT BROKEN, needs NPM moment module import to compile.")
      assume(IzFiles.haveExecutables("tsc"), "tsc not available")
      assert(compilesTypeScript(getClass.getSimpleName, loadDefs()))
    }
    "be able to compile into golang" in {
      assume(IzFiles.haveExecutables("go"), "go not available")
      assert(compilesGolang(getClass.getSimpleName, loadDefs()))
    }
    "be able to compile into csharp" in {
      assume(IzFiles.haveExecutables("csc", "nunit-console"), "csc not available")
      assert(compilesCSharp(getClass.getSimpleName, loadDefs()))
    }
  }
}

