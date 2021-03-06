package com.github.pshirshov.izumi.fundamentals.reflection

import scala.reflect.api.Universe

// TODO: collect method, better api
object AnnotationTools {
  def find(u: Universe)(annType: u.Type, symb: u.Symbol): Option[u.Annotation] = (
    findSymbolAnnotation(u)(annType, symb)
      orElse findTypeAnnotation(u)(annType, symb.typeSignature.finalResultType)
    )

  def findSymbolAnnotation(u: Universe)(annType: u.Type, symb: u.Symbol): Option[u.Annotation] =
      symb.annotations.find(annotationTypeEq(u)(annType, _))

  def findTypeAnnotation(u: Universe)(annType: u.Type, typ: u.Type): Option[u.Annotation] =
    getAllTypeAnnotations(u)(typ).find(annotationTypeEq(u)(annType, _))

  def getAllAnnotations(u: Universe)(symb: u.Symbol): List[u.Annotation] =
    symb.annotations ++ getAllTypeAnnotations(u)(symb.typeSignature.finalResultType)

  def getAllTypeAnnotations(u: Universe)(typ: u.Type): List[u.Annotation] =
    typ match {
       case t: u.AnnotatedTypeApi =>
         t.annotations
       case _ =>
         List()
     }

  def annotationTypeEq(u: Universe)(tpe: u.Type, ann: u.Annotation): Boolean =
    ann.tree.tpe.erasure =:= tpe.erasure

  def findMatchingBody[T: u.TypeTag, R](u: Universe)(symb: u.Symbol, matcher: PartialFunction[u.Tree, R]): Option[R] =
    find(u)(u.typeOf[T], symb).map(_.tree.children.tail).flatMap(_.collectFirst[R](matcher))

  def findArgument[R](ann: Universe#Annotation)(matcher: PartialFunction[Universe#Tree, R]): Option[R] =
    ann.tree.children.tail.collectFirst(matcher)

  def collectArguments[R](ann: Universe#Annotation)(matcher: PartialFunction[Universe#Tree, R]): List[R] =
    ann.tree.children.tail.collect(matcher)

  def mkModifiers(u: Universe)(anns: List[u.Annotation]): u.Modifiers =
    u.Modifiers.apply(u.NoFlags, u.typeNames.EMPTY, anns.map(_.tree))
}
