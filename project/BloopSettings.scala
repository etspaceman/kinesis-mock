import sbt.Keys._
import sbt._
import scala.util.Try
import scala.reflect.runtime.universe

trait ExternalSettings {
  @SuppressWarnings(Array("scalafix:DisableSyntax.asInstanceOf"))
  def loadIfExists[T](
      fullyQualifiedName: String,
      args: Option[Seq[Any]],
      default: => T
  ): T = {
    val tokens = fullyQualifiedName.split('.')
    val memberName = tokens.last
    val moduleName = tokens.take(tokens.length - 1).mkString(".")

    val runtimeMirror = universe.runtimeMirror(getClass.getClassLoader)
    val value = Try(runtimeMirror.staticModule(moduleName)).map { module =>
      val obj = runtimeMirror.reflectModule(module)
      val instance = obj.instance
      val instanceMirror = runtimeMirror.reflect(instance)
      val member =
        instanceMirror.symbol.info.member(universe.TermName(memberName))
      args
        .fold(instanceMirror.reflectField(member.asTerm).get)(args =>
          instanceMirror.reflectMethod(member.asMethod)(args: _*)
        )
        .asInstanceOf[T]
    }
    value.getOrElse(default)
  }
}

object BloopSettings extends ExternalSettings {
  val default: Seq[Def.Setting[_]] = loadIfExists(
    "bloop.integrations.sbt.BloopDefaults.configSettings",
    Some(Nil),
    Seq.empty[Def.Setting[_]]
  )
}
