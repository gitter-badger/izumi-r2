package com.github.pshirshov.izumi.distage.model.reflection

import com.github.pshirshov.izumi.fundamentals.reflection._


case class SelectedConstructor(constructorSymbol: TypeSymb, arguments: Seq[TypeSymb])