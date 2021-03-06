package com.ldaniels528.trifecta.support.avro

import java.io.ByteArrayOutputStream
import java.lang.reflect.Method

import com.ldaniels528.trifecta.util.TxUtils._
import org.apache.avro.Schema
import org.apache.avro.generic.{GenericDatumReader, GenericDatumWriter, GenericRecord}
import org.apache.avro.io.{DecoderFactory, EncoderFactory}
import org.apache.avro.specific.SpecificRecordBase
import org.slf4j.LoggerFactory

import scala.language.existentials
import scala.util.Try

/**
 * Avro Conversion Utility
 * @author Lawrence Daniels <lawrence.daniels@gmail.com>
 */
object AvroConversion {
  private lazy val logger = LoggerFactory.getLogger(getClass)

  /**
   * Copies data from a Scala case class to a Java Bean (Avro Builder)
   * @param bean the given Scala case class
   * @param builder the given Java Bean (Avro Builder)
   */
  def copy[A, B](bean: A, builder: B) {
    // retrieve the source data (name-value pairs of the case class)
    val srcData = {
      val beanClass = bean.getClass
      beanClass.getDeclaredFields map (f => (f.getName, beanClass.getMethod(f.getName).invoke(bean), f.getType))
    }

    // populate the Java Bean
    val builderClass = builder.getClass
    srcData foreach { case (name, value, valueClass) =>
      Try {
        val setterName = "set%c%s".format(name.head.toUpper, name.tail)
        setValue(value, valueClass, builder, builderClass, setterName)
      }
    }
  }

  private def setValue[A, B](value: Any, valueClass: Class[A], dstInst: Any, dstClass: Class[B], setterName: String) {
    val results = value match {
      case o: Option[_] =>
        o.map { myValue =>
          val myObjectValue = myValue.asInstanceOf[Object]
          val myObjectValueClass = Option(myObjectValue) map (_.getClass) getOrElse valueClass
          (myObjectValue, myObjectValueClass)
        }
      case v =>
        val myObjectValue = value.asInstanceOf[Object]
        val myObjectValueClass = Option(myObjectValue) map (_.getClass) getOrElse valueClass
        Option((myObjectValue, myObjectValueClass))
    }

    results foreach { case (myValue, myValueClass) =>
      findMethod(dstClass, setterName, myValueClass).foreach { m =>
        m.invoke(dstInst, myValue)
      }
    }
  }

  private def findMethod(dstClass: Class[_], setterName: String, setterParamClass: Class[_]): Option[Method] = {
    dstClass.getDeclaredMethods find (m =>
      m.getName == setterName &&
        m.getParameterTypes.length == 1 &&
        m.getParameterTypes.headOption.exists(isCompatible(_, setterParamClass)))
  }

  private def isCompatible(typeA: Class[_], typeB: Class[_], first: Boolean = true): Boolean = {
    if (typeA == typeB) true
    else
      typeA match {
        case c if c == classOf[Byte] => typeB == classOf[java.lang.Byte]
        case c if c == classOf[Char] => typeB == classOf[java.lang.Character]
        case c if c == classOf[Double] => typeB == classOf[java.lang.Double]
        case c if c == classOf[Float] => typeB == classOf[java.lang.Float]
        case c if c == classOf[Int] => typeB == classOf[Integer]
        case c if c == classOf[Long] => typeB == classOf[java.lang.Long]
        case c if c == classOf[Short] => typeB == classOf[java.lang.Short]
        case c if first => isCompatible(typeB, typeA, !first)
        case _ => false
      }
  }

  /**
   * Converts the given byte array to an Avro Generic Record
   * @param schema the given Avro Schema
   * @param bytes the given byte array
   * @return an Avro Generic Record
   */
  def decodeRecord(schema: Schema, bytes: Array[Byte]): GenericRecord = {
    val reader = new GenericDatumReader[GenericRecord](schema)
    val decoder = DecoderFactory.get().binaryDecoder(bytes, null)
    reader.read(null, decoder)
  }

  /**
   * Converts an Avro Java Bean into a byte array
   * @param schema the given Avro Schema
   * @param datum the given Avro Java Bean
   * @return a byte array
   */
  def encodeRecord[T <: SpecificRecordBase](schema: Schema, datum: T): Array[Byte] = {
    new ByteArrayOutputStream(1024) use { out =>
      val writer = new GenericDatumWriter[GenericRecord](schema)
      val encoder = EncoderFactory.get().binaryEncoder(out, null)
      writer.write(datum, encoder)
      encoder.flush()
      out.toByteArray
    }
  }

}
