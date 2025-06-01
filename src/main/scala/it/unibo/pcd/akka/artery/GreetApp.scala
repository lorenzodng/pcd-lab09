package it.unibo.pcd.akka.artery

import akka.actor.typed.scaladsl.*
import akka.actor.typed.scaladsl.adapter.*
import akka.actor.typed.{Behavior, ActorRef, ActorSystem}
import akka.actor.ActorSystem as ClassicActorSystem // Used for actorSelection
import akka.util.Timeout
import com.typesafe.config.{Config, ConfigFactory}
import it.unibo.pcd.akka.Message
import it.unibo.pcd.akka.artery.GreetApp.*

import concurrent.duration.DurationInt
import scala.concurrent.duration.FiniteDuration

import scala.concurrent.ExecutionContext.Implicits.global

import scala.language.postfixOps

// NB! Do not use akka artery, it is a low level library (indeed it does not support typed)
//esempio di interazione tra attori in un sistema distribuito
object GreetApp:
  case class Greet(whom: String, replyTo: ActorRef[Greeted]) extends Message //Message è un'estensione necessaria per trasferire i messaggi sulla rete
  case class Greeted(whom: String, sender: ActorRef[Greet]) extends Message

  def greet(me: String): Behavior[Greet] = Behaviors.receive { case (ctx, Greet(whom, replyTo)) => // comportamento dell'attore quando riceve un messaggio Greet
    ctx.log.info(s" $whom at ${replyTo.path} greet to me!")
    replyTo ! Greeted(me, ctx.self)
    Behaviors.same
  }

  def greeted(): Behavior[Greeted] = Behaviors.receive { case (ctx, Greeted(whom, sender)) => // comportamento dell'attore quando riceve un messaggio Greeted
    ctx.log.info(s"$whom at ${sender.path} has received my greet!")
    Behaviors.stopped
  }

//è una funzione che genera una configurazione in cui il sistema di attori ascolta su una porta specifica per la comunicazione remota
def configFrom(port: Int): Config = ConfigFactory
  .parseString(s"""akka.remote.artery.canonical.port=$port""")
  .withFallback(ConfigFactory.load("base-remote"))

@main def alice(): Unit =
  val config = configFrom(8080)
  ActorSystem(greet("alice"), "alice", config) //creo un attore

@main def gianluca(): Unit =
  anyGreet("gianluca", 8081, "alice")

@main def anyGreet(who: String, port: Int, toWhom: String): Unit =
  given Timeout = 2 seconds // imposto un timer per la ricerca dell'attore remoto
  val remoteReferencePath = s"akka://$toWhom@127.0.0.1:8080/user/" //costruisco il path dell'attore remoto
  val config = configFrom(port) //creo una configurazione con la porta specificata, necessaria per la comunicazione tra attori
  val system = ClassicActorSystem.apply(who, config) // creo un sistema di attori "classico" che possa contenere l'attore remoto cercato
  val remoteReference = system.actorSelection(remoteReferencePath).resolveOne() // cerco l'attore remoto
  for remote <- remoteReference do //quando trovo l'attore remoto a cui inviare il messaggio
    val actor = system.spawn(greeted(), who) //creo un attore locale
    remote ! GreetApp.Greet(who, actor) //invio il messaggio Greet all'attore remoto
