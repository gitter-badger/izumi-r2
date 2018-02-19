package com.github.pshirshov.izumi.idealingua.translator.toscala

import com.github.pshirshov.izumi.idealingua.model.finaldef.{DomainDefinition, FinalDefinition, Service}
import com.github.pshirshov.izumi.idealingua.model.runtime._

class IDLRuntimeTypes(conv: ScalaTypeConverter) {
  final val idtGenerated = conv.toScala[IDLGenerated].init()
  final val idtService = conv.toScala[IDLService].init()
  final val inputInit = conv.toScala[IDLInput].init()
  final val outputInit = conv.toScala[IDLOutput].init()
  final val typeCompanionInit = conv.toScala[IDLTypeCompanion].init()
  final val enumInit = conv.toScala[IDLEnum].init()
  final val enumElInit = conv.toScala[IDLEnumElement].init()
  final val serviceCompanionInit = conv.toScala[IDLServiceCompanion].init()

  final val tIDLIdentifier = conv.toScala[IDLIdentifier]
  final val tFinalDefinition = conv.toScala[FinalDefinition]
  final val tDomainCompanion = conv.toScala[IDLDomainCompanion]
  final val tDomainDefinition = conv.toScala[DomainDefinition]
  final val tService = conv.toScala[Service]

}