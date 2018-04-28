package com.github.pshirshov.izumi.distage

import com.github.pshirshov.izumi.distage.bootstrap.DefaultBootstrapContext
import com.github.pshirshov.izumi.distage.model._
import com.github.pshirshov.izumi.distage.model.definition.{ContextDefinition, TrivialDIDef}


object Injectors {

  import DefaultBootstrapContext._


  def bootstrap(extensions: LocatorExtension*): Injector = {
    bootstrap(TrivialDIDef(Seq.empty), extensions :_*)
  }

  def bootstrap(overrides: ContextDefinition, extensions: LocatorExtension*): Injector = {
    val bootstrapDefinition = defaultBootstrapContextDefinition.overridenBy(overrides)
    val bootstrapLocator = new DefaultBootstrapContext(bootstrapDefinition)
    create(bootstrapLocator, extensions: _*)
  }

  def create(parent: Locator, extensions: LocatorExtension*): Injector = {
    new InjectorDefaultImpl(parent.extend(extensions: _*))
  }
}