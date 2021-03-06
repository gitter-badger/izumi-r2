package com.github.pshirshov.izumi.idealingua.runtime.rpc

import scala.concurrent.{ExecutionContext, Future}
import scala.language.{higherKinds, implicitConversions}
import scala.util.Try


trait IRTServiceResult[R[_]] {
  @inline def map[A, B](r: R[A])(f: A => B): R[B]

  @inline def flatMap[A, B](r: R[A])(f: A => R[B]): R[B]

  @inline def wrap[A](v: => A): R[A]
}

object IRTServiceResult {
  type Id[T] = T

  @inline implicit def toOps[R[_], A](value: R[A]): ServiceResultOps[R, A] = new ServiceResultOps[R, A](value)

  class ServiceResultOps[R[_], A](val value: R[A]) extends AnyVal {
    @inline def map[B](f: A => B)(implicit serviceResult: IRTServiceResult[R]): R[B] = serviceResult.map(value)(f)

    @inline def flatMap[B](f: A => R[B])(implicit serviceResult: IRTServiceResult[R]): R[B] = serviceResult.flatMap(value)(f)
  }

  implicit object ServiceResultId extends IRTServiceResult[Id] {
    @inline override def map[A, B](r: Id[A])(f: A => B) = f(r)

    @inline override def flatMap[A, B](fa: Id[A])(f: A => Id[B]): Id[B] = f(fa)

    @inline override def wrap[A](v: => A): Id[A] = v
  }

  implicit object ServiceResultOption extends IRTServiceResult[Option] {
    @inline override def map[A, B](r: Option[A])(f: A => B): Option[B] = r.map(f)

    @inline override def flatMap[A, B](r: Option[A])(f: A => Option[B]): Option[B] = r.flatMap(f)

    @inline override def wrap[A](v: => A): Option[A] = Option(v)
  }

  implicit object ServiceResultTry extends IRTServiceResult[Try] {
    @inline override def map[A, B](r: Try[A])(f: A => B): Try[B] = r.map(f)

    @inline override def flatMap[A, B](r: Try[A])(f: A => Try[B]): Try[B] = r.flatMap(f)

    @inline override def wrap[A](v: => A): Try[A] = Try(v)
  }

  implicit def toServiceResultFutureOps(implicit ec: ExecutionContext): ServiceResultFuture = new ServiceResultFuture

  class ServiceResultFuture(implicit ec: ExecutionContext) extends IRTServiceResult[Future] {
    @inline override def map[A, B](r: Future[A])(f: A => B): Future[B] = r.map(f)

    @inline override def flatMap[A, B](r: Future[A])(f: A => Future[B]): Future[B] = r.flatMap(f)

    @inline override def wrap[A](v: => A): Future[A] = Future(v)
  }

}
