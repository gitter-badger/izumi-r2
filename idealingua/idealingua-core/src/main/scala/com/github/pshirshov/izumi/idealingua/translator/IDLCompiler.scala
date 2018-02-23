package com.github.pshirshov.izumi.idealingua.translator

import java.nio.charset.StandardCharsets
import java.nio.file.{Files, Path}

import com.github.pshirshov.izumi.idealingua.il.ILRenderer
import com.github.pshirshov.izumi.idealingua.model.il.DomainDefinition
import com.github.pshirshov.izumi.idealingua.translator.IDLCompiler.{CompilerOptions, IDLResult}
import com.github.pshirshov.izumi.idealingua.translator.toscala.FinalTranslatorScalaImpl


class IDLCompiler(domain: DomainDefinition) {
  def compile(target: Path, options: CompilerOptions): IDLResult = {
    println(new ILRenderer(domain).render())

    val translator = toTranslator(options)
    val modules = translator.translate(domain)

    val files = modules.map {
      module =>
        val parts = module.id.path :+ module.id.name
        val modulePath = parts.foldLeft(target) { case (path, part) => path.resolve(part) }
        modulePath.getParent.toFile.mkdirs()

        //println(s"""$modulePath:\n${module.content}\n\n""")

        Files.write(modulePath, module.content.getBytes(StandardCharsets.UTF_8))
        modulePath
    }
    IDLCompiler.IDLSuccess(files)
  }

  private def toTranslator(options: CompilerOptions) = {
    options.language match {
      case IDLLanguage.Scala =>
        new FinalTranslatorScalaImpl()
      case IDLLanguage.Go =>
        ???
      case IDLLanguage.Typescript =>
        ???
      case IDLLanguage.UnityCSharp =>
        ???
    }
  }
}

object IDLCompiler {

  case class CompilerOptions(language: IDLLanguage)

  trait IDLResult

  case class IDLSuccess(paths: Seq[Path]) extends IDLResult

  case class IDLFailure() extends IDLResult

}