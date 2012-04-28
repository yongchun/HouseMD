package com.github.zhongl.insider

import java.lang.System.{currentTimeMillis => now}
import java.lang.reflect.Method
import instrument.{ClassFileTransformer, Instrumentation}
import java.security.ProtectionDomain
import java.io.FileNotFoundException
import scala.Predef._
import scala.Array

/**
 * @author <a href="mailto:zhong.lunfu@gmail.com">zhongl<a>
 */
class Transformer(inst: Instrumentation, methodRegexs: Traversable[String]) {

  private[this] lazy val toProbeClasses = inst.getAllLoadedClasses.filter(toProbe)

  private[this] lazy val probeTransformer = classFileTransformer {
    (loader: ClassLoader, className: String, classfileBuffer: Array[Byte]) =>
    // TODO LOGGER.info(format("probe class {1} from {0}", loader, className))
      ClassDecorator.decorate(classfileBuffer, methodRegexs)
  }

  private[this] lazy val resetTransformer = classFileTransformer {
    (loader: ClassLoader, className: String, classfileBuffer: Array[Byte]) =>
    // TODO log "reset class {1} from {0}", loader, className
      val stream = loader.getResourceAsStream(className + ".class")
      if (stream == null) throw new FileNotFoundException
      Utils.toBytes(stream)
  }

  def probe() {transformBy(probeTransformer)}

  def reset() {transformBy(resetTransformer)}

  private[this] def transformBy(t: ClassFileTransformer) {
    inst.addTransformer(t)
    try {
      inst.retransformClasses(toProbeClasses: _*)
    } finally {
      inst.removeTransformer(t)
    }
  }

  private[this] def toProbe(method: Method) = {
    val fullName = method.getDeclaringClass.getName + "." + method.getName
    !methodRegexs.find(fullName.matches).isEmpty
  }

  private[this] def toProbe(klass: Class[_]):Boolean = {
    val methods = (klass.getDeclaredMethods ++ klass.getMethods).toSet
    !methods.find(toProbe).isEmpty
  }

  private[this] def classFileTransformer(fun: (ClassLoader, String, Array[Byte]) => Array[Byte]) =
    new ClassFileTransformer {
      def transform(
                     loader: ClassLoader,
                     className: String,
                     classBeingRedefined: Class[_],
                     protectionDomain: ProtectionDomain,
                     classfileBuffer: Array[Byte]) = {
        var bytes = classfileBuffer
        try {
          bytes = fun(loader, className, classfileBuffer)
        } catch {
          case e =>
          // TODO log "transfor but not reset class {1} from {0}", loader, className
        }
        bytes
      }
    }
}
