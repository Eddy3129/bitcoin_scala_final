val AkkaVersion = "2.6.17"

resolvers += ("custome1" at "http://4thline.org/m2").withAllowInsecureProtocol(true)

libraryDependencies ++= Seq(
  "com.typesafe.akka" %% "akka-actor" % AkkaVersion, // Added for untyped actors
  "com.typesafe.akka" %% "akka-actor-typed" % AkkaVersion,
  "com.typesafe.akka" %% "akka-remote" % AkkaVersion,
  "com.typesafe.akka" %% "akka-cluster" % AkkaVersion, // Added for Akka Cluster
  "com.typesafe.akka" %% "akka-cluster-typed" % AkkaVersion,
  "com.typesafe.akka" %% "akka-serialization-jackson" % AkkaVersion,
  "org.fourthline.cling" % "cling-core" % "2.1.2",
  "org.fourthline.cling" % "cling-support" % "2.1.2",
  "ch.qos.logback" % "logback-classic" % "1.2.3",
  "org.scalafx" %% "scalafx" % "8.0.192-R14",
  "org.scalafx" %% "scalafxml-core-sfx8" % "0.5",
  "com.fasterxml.jackson.module" %% "jackson-module-scala" % "2.15.2"
)

addCompilerPlugin("org.scalamacros" % "paradise" % "2.1.1" cross CrossVersion.full)

fork := true
