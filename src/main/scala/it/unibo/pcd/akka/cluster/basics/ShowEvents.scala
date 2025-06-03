package it.unibo.pcd.akka.cluster.basics

import akka.actor.typed.scaladsl.*
import akka.actor.typed.scaladsl.adapter.*
import akka.actor.typed.{ActorSystem, Behavior}
import akka.cluster.ClusterEvent.{LeaderChanged, MemberEvent}
import akka.cluster.typed.{Cluster, Subscribe}
import com.typesafe.config.ConfigFactory
import it.unibo.pcd.akka.cluster.*

object ShowEvents:
  def apply(): Behavior[MemberEvent | LeaderChanged] = Behaviors.setup { ctx => //
    val cluster = Cluster(ctx.system) //prendo il riferimento del cluster a cui l'attore appartiene
    classOf[MemberEvent] :: classOf[LeaderChanged] :: Nil foreach (event => 
      cluster.subscriptions ! Subscribe(ctx.self, event) //iscrivo l'attore alla ricezione degli eventi di tipo MemberEvent e LeaderChanged provienenti dal cluster
    )
    Behaviors.receiveMessage { msg => //definisco il comportamento di risposta, eseguito quando l'attore riceve i messaggi
      ctx.log.info(s"EVENT LISTENER: ${msg.toString}")
      Behaviors.same
    }
  }

//metodo che crea un actor system su una determinata porta, impostando i nodi seed come valori di fallback
def startup(port: Int)(root: => Behavior[_]): Unit =
  val config = ConfigFactory
    .parseString(s"""akka.remote.artery.canonical.port=$port""")
    .withFallback(ConfigFactory.load("base-cluster"))
  // Create an Akka system
  ActorSystem(root, "ClusterSystem", config)

//creo due actor system su due porte diverse  
@main def multipleActorsSystems(): Unit =
  startup(seeds.head)(ShowEvents()) 
  startup(seeds.last)(Behaviors.empty)

// To run multiple jvm, use:
// a) open two sbt shells
// b) in one of them, runMain it.unibo.pcd.akka.cluster.basics.singleJVM <a seed port> true
// c) in another console, type: runMain it.unibo.pcd.akka.cluster.basics.singleJVM <another port> false
// d) in the first console, you should see the events
// e) when you are done, type exit
@main def singleJVM(port: Int, listener: Boolean) =
  startup(port)(if (listener) ShowEvents() else Behaviors.empty)
