package com.github.pshirshov.izumi.distage.model.exceptions

import com.github.pshirshov.izumi.distage.model.reflection.universe.RuntimeDIUniverse

class DuplicateInstancesException(message: String, val key: RuntimeDIUniverse.DIKey) extends DIException(message, null)



