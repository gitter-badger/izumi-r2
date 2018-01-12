package org.bitbucket.pshirshov.izumi.di

import org.bitbucket.pshirshov.izumi.di.model.plan.ReadyPlan

trait Producer {
  def produce(dIPlan: ReadyPlan): Locator
}
