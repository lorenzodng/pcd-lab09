package it.unibo.pcd.akka.cluster.basics

import akka.actor.typed.scaladsl.*
import akka.actor.typed.scaladsl.adapter.*
import akka.actor.typed.{ActorSystem, Behavior}
import akka.cluster.ClusterEvent.{LeaderChanged, MemberEvent}
import akka.cluster.typed.{Cluster, Join, Subscribe, Leave}
import com.typesafe.config.ConfigFactory
import akka.actor.AddressFromURIString
import it.unibo.pcd.akka.cluster.*

//esempio di gestione del cluster non utilizzando i nodi seed
@main def join(): Unit =

  //simulo due macchine del cluster
  val first = startup("base-cluster-no-seed", 3521)(Behaviors.empty) //creo un actor system sulla porta 3521
  val second = startup("base-cluster-no-seed", 3522)(Behaviors.empty) //creo un actor system sulla porta 3522
  
  val clusterRefA = Cluster(first) //prendo il riferimento del cluster a cui l'actor system first appartiene
  clusterRefA.manager ! Join(clusterRefA.selfMember.address) //unisco il nodo a sè stesso. In questo modo, rendo il nodo il primo del cluster 
  Thread.sleep(5000)
  val clusterRefB = Cluster(second) //prendo il riferimento del cluster a cui l'actor system second appartiene
  clusterRefB.manager ! Join(clusterRefA.selfMember.address) //unisco il nodo al cluster attraverso il nodo principale clusterRefA
  Thread.sleep(5000)
  println(clusterRefA.state) //stampo lo stato del cluster
  assert(clusterRefA.state == clusterRefB.state) //verifico che i due nodi abbiano lo stesso stato (se l'assert fallisce, allora viene lanciata autoamticamente un'eccezione e il programma termina)
  clusterRefA.manager ! Leave(clusterRefB.selfMember.address) //se l'assert è true, rimuovo il secondo nodo dal cluster
  Thread.sleep(5000)
  println(clusterRefA.state) //stampo (di nuovo) lo stato del cluster
  first.terminate() //termino il primo sistema akka (rendo il nodo non più accessibile)
  second.terminate() //termino il secondo sistema akka (rendo il nodo non più accessibile)

//esempio di creazione di sistemi akka utilizzando i nodi seed
@main def withSeed(): Unit =
  val systems = seeds.map(port => startup(port = port)(Behaviors.empty)) //per ogni nodo seed creo un actor system
  Thread.sleep(10000)
  systems.foreach(_.terminate()) //termino ogni sistema akka

//esempio di g
@main def usingRemote(myPort: Int, seedPort: Int): Unit =
  val system = startup("base-cluster-no-seed", myPort)(Behaviors.empty) //creo un actor system sulla porta data in input
  val seed = AddressFromURIString.parse(s"akka://ClusterSystem@127.0.0.1:$seedPort") //creo un nodo di accesso al cluster
  Cluster(system).manager ! Join(seed) //aggiungo l'actor system al cluster
