package eu.unicredit

import scala.language.experimental.macros
import scala.reflect.macros.blackbox.Context

import fastparse.core.Parsed

object ConfigMacroLoader {

  var verboseLog = false

  def setVerboseLogImpl(c: Context)(): c.Expr[Unit] = {
    import c.universe._

    verboseLog = true

    c.Expr[Unit](q"{}")
  }

  def setVerboseLog(): Unit = macro setVerboseLogImpl

  def parse(c: Context)(input: c.Expr[String]): c.Expr[shocon.Config.Value] = {
    import c.universe._

    implicit val SimpleValueL = Liftable[eu.unicredit.shocon.Config.SimpleValue] { sv =>
      sv match {
        case nl: eu.unicredit.shocon.Config.NumberLiteral =>
          q"_root_.eu.unicredit.shocon.Config.NumberLiteral(${nl.value})"
        case sl: eu.unicredit.shocon.Config.StringLiteral =>
          q"_root_.eu.unicredit.shocon.Config.StringLiteral(${sl.value})"
        case bl: eu.unicredit.shocon.Config.BooleanLiteral =>
          q"_root_.eu.unicredit.shocon.Config.BooleanLiteral(${bl.value})"
        case _ =>
          q"""_root_.eu.unicredit.shocon.Config.NullLiteral"""
      }
    }

    implicit val ArrayValueL = Liftable[eu.unicredit.shocon.Config.Array] { arr =>

      implicit val ConfigValue: Liftable[eu.unicredit.shocon.Config.Value] = Liftable[eu.unicredit.shocon.Config.Value] { value =>
        value match {
          case sv: eu.unicredit.shocon.Config.SimpleValue =>
            q"$sv"
        }
      }

      q"""eu.unicredit.shocon.Config.Array(Seq(..${arr.elements}))"""
    }

    // implicit val TupleValueL: Liftable[(eu.unicredit.shocon.Config.Key, eu.unicredit.shocon.Config.Value)] = Liftable[(eu.unicredit.shocon.Config.Key, eu.unicredit.shocon.Config.Value)] {
    //   case (k, v) =>
    //     v match {
    //       case sv: eu.unicredit.shocon.Config.SimpleValue =>
    //         q"""($k -> ${SimpleValueL(sv)})"""
    //       case obj: eu.unicredit.shocon.Config.Object =>
    //         q"""($k -> ${ObjectValueL(obj)})"""
    //     }
    // }

    // implicit val ObjectValueL: Liftable[eu.unicredit.shocon.Config.Object] = Liftable[eu.unicredit.shocon.Config.Object] { obj =>
    //   if (obj.fields.size <= 0)
    //     q"""eu.unicredit.shocon.Config.Object(Map())"""
    //   else
    //     q"""eu.unicredit.shocon.Config.Object(Map())"""
    //     // q"""eu.unicredit.shocon.Config.Object.fromPairs(
    //     //     Seq(..${obj.fields.map{
    //     //       case (k, v) =>
    //     //         v match {
    //     //           case sv: eu.unicredit.shocon.Config.SimpleValue =>
    //     //             k -> sv
    //     //           case obj: eu.unicredit.shocon.Config.Object =>
    //     //             k -> _root_.eu.unicredit.shocon.Config.NullLiteral
    //     //         }
    //     //     }}
    //     //     )
    //     // )"""
    // }

    // implicit val ConfigValue: Liftable[eu.unicredit.shocon.Config.Value] = Liftable[eu.unicredit.shocon.Config.Value] { value =>
    //   value match {
    //     case sv: eu.unicredit.shocon.Config.SimpleValue =>
    //       q"$sv"
    //     case arr: eu.unicredit.shocon.Config.Array =>
    //       q"$arr"
    //     case obj: eu.unicredit.shocon.Config.Object =>
    //       q"$obj"
    //   }
    // }


    import eu.unicredit.shocon.Config
    def flatten(key: Seq[String], cfg: Config.Value): Map[String, Tree] = {
      cfg match {
        case v: Config.SimpleValue =>
          Map((key).mkString(".") -> q"$v")
        case arr: Config.Array =>
          Map((key).mkString(".") -> q"$arr")
        case Config.Object(map) =>
          map.flatMap{
            case (k, v) =>
              flatten((key :+ k), v)
          }
      }
    }

    input.tree match {
      case q"""$strLit""" =>
        strLit match {
          case Literal(Constant(str)) =>

            val config =
              eu.unicredit.shocon.Config(str.toString)
            
            val flattened = flatten(Seq(), config).toSeq

            val ast =
              q"""{
                _root_.eu.unicredit.shocon.Config.Object.fromPairs(Seq(..${flattened}))
              }"""

            try {
              if (verboseLog)
                c.info(c.enclosingPosition, "[shocon-parser] optimized at compile time", false)

              c.typecheck(ast)
              c.Expr[shocon.Config.Value](ast)
            } catch {
              case err: Throwable =>
                if (verboseLog)
                  c.warning(c.enclosingPosition, "[shocon-parser] fallback to runtime parser")

                c.Expr[shocon.Config.Value](q"""{
                  eu.unicredit.shocon.ConfigParser.root.parse($input) match{
                    case fastparse.core.Parsed.Success(v,_) => v
                    case f: fastparse.core.Parsed.Failure[_, _] => throw new Error(f.msg)
                  }
                }""")
            }
          case _ =>
            if (verboseLog)
              c.warning(c.enclosingPosition, "[shocon-parser] fallback to runtime parser")

            c.Expr[shocon.Config.Value](q"""{
              eu.unicredit.shocon.ConfigParser.root.parse($input) match{
                case fastparse.core.Parsed.Success(v,_) => v
                case f: fastparse.core.Parsed.Failure[_, _] => throw new Error(f.msg)
              }
            }""")
        }
      // case _ =>
      //   if (verboseLog)
      //       c.warning(c.enclosingPosition, "[shocon-parser] fallback to runtime parser")

      //   c.Expr[shocon.Config.Value](q"""{
      //     eu.unicredit.shocon.ConfigParser.root.parse($input) match{
      //       case fastparse.core.Parsed.Success(v,_) => v
      //       case f: fastparse.core.Parsed.Failure[_, _] => throw new Error(f.msg)
      //     }
      //   }""")
    }
  }

}