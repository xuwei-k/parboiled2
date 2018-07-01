import com.typesafe.sbt.SbtScalariform.ScalariformKeys
import scalariform.formatter.preferences._
import scala.xml.transform._
import scala.xml.{Node => XNode, NodeSeq}
import sbtcrossproject.{crossProject, CrossType}

val commonSettings = Seq(
  version := "1.0.1-SNAPSHOT",
  scalaVersion := "2.11.12",
  crossScalaVersions := Seq("2.11.12", "2.12.6"),
  organization := "org.http4s",
  homepage := Some(new URL("http://parboiled.org")),
  description := "Fork of parboiled2 for http4s, sans shapeless dependency",
  startYear := Some(2009),
  licenses := Seq("Apache-2.0" -> new URL("http://www.apache.org/licenses/LICENSE-2.0.txt")),
  javacOptions ++= Seq(
    "-encoding", "UTF-8",
    "-source", "1.6",
    "-target", "1.6",
    "-Xlint:unchecked",
    "-Xlint:deprecation"),
  scalacOptions ++= List(
    "-encoding", "UTF-8",
    "-feature",
    "-unchecked",
    "-deprecation",
    "-Xlint",
    "-language:_",
    "-target:jvm-1.6",
    "-Xlog-reflective-calls"))

val formattingSettings = scalariformSettings ++ Seq(
  ScalariformKeys.preferences := ScalariformKeys.preferences.value
    .setPreference(RewriteArrowSymbols, true)
    .setPreference(AlignParameters, true)
    .setPreference(AlignSingleLineCaseStatements, true)
    .setPreference(DoubleIndentClassDeclaration, true)
    .setPreference(PreserveDanglingCloseParenthesis, true))

val publishingSettings = Seq(
  publishMavenStyle := true,
  useGpg := true,
  publishTo := {
    val nexus = "https://oss.sonatype.org/"
    if (version.value.trim.endsWith("SNAPSHOT")) Some("snapshots" at nexus + "content/repositories/snapshots")
    else                                         Some("releases" at nexus + "service/local/staging/deploy/maven2")
  },
  pomIncludeRepository := { _ => false },
  pomExtra :=
    <scm>
      <url>git@github.com:sirthias/parboiled2.git</url>
      <connection>scm:git:git@github.com:sirthias/parboiled2.git</connection>
    </scm>
    <developers>
      <developer>
        <id>sirthias</id>
        <name>Mathias Doenitz</name>
      </developer>
      <developer>
        <id>alexander-myltsev</id>
        <name>Alexander Myltsev</name>
        <url>http://www.linkedin.com/in/alexandermyltsev</url>
      </developer>
    </developers>)

val noPublishingSettings = Seq(
  publishArtifact := false,
  publishTo := Some(Resolver.file("Unused transient repository", file("target/unusedrepo"))))

/////////////////////// DEPENDENCIES /////////////////////////

def scalaReflect(v: String) = "org.scala-lang"  %  "scala-reflect"        % v       % "provided"
val specs2MatcherExtra = Def.setting("org.specs2" %%% "specs2-matcher-extra" % "4.3.0" % "test")
val specs2ScalaCheck   = Def.setting("org.specs2" %%% "specs2-scalacheck"    % "4.3.0" % "test")

/////////////////////// PROJECTS /////////////////////////

lazy val root = project.in(file("."))
  .aggregate(parboiledJVM, parboiledJS)
  .aggregate(parboiledCoreJVM, parboiledCoreJS)
  .settings(noPublishingSettings: _*)

lazy val parboiled = crossProject(JSPlatform, JVMPlatform)
  .dependsOn(parboiledCore)
  .settings(commonSettings: _*)
  .settings(formattingSettings: _*)
  .settings(publishingSettings: _*)
  .jvmSettings(
    mappings in (Compile, packageBin) ++= (mappings in (parboiledCoreJVM.project, Compile, packageBin)).value,
    mappings in (Compile, packageSrc) ++= (mappings in (parboiledCoreJVM.project, Compile, packageSrc)).value,
    mappings in (Compile, packageDoc) ++= (mappings in (parboiledCoreJVM.project, Compile, packageDoc)).value
  )
  .jsSettings(
    mappings in (Compile, packageBin) ++= (mappings in (parboiledCoreJS.project, Compile, packageBin)).value,
    mappings in (Compile, packageSrc) ++= (mappings in (parboiledCoreJS.project, Compile, packageSrc)).value,
    mappings in (Compile, packageDoc) ++= (mappings in (parboiledCoreJS.project, Compile, packageDoc)).value
  )
  .settings(
    libraryDependencies ++= Seq(scalaReflect(scalaVersion.value), specs2MatcherExtra.value),
    mappings in (Compile, packageBin) ~= (_.groupBy(_._2).toSeq.map(_._2.head)), // filter duplicate outputs
    mappings in (Compile, packageDoc) ~= (_.groupBy(_._2).toSeq.map(_._2.head)), // filter duplicate outputs
    pomPostProcess := { // we need to remove the dependency onto the parboiledCore module from the POM
      val filter = new RewriteRule {
        override def transform(n: XNode) = if ((n \ "artifactId").text.startsWith("parboiledcore")) NodeSeq.Empty else n
      }
      new RuleTransformer(filter).transform(_).head
    }
  )

lazy val parboiledJVM = parboiled.jvm
lazy val parboiledJS = parboiled.js

lazy val generateActionOps = taskKey[Seq[File]]("Generates the ActionOps boilerplate source file")

lazy val parboiledCore = crossProject(JSPlatform, JVMPlatform).crossType(CrossType.Pure).in(file("parboiled-core"))
  .settings(commonSettings: _*)
  .settings(formattingSettings: _*)
  .settings(noPublishingSettings: _*)
  .settings(
    libraryDependencies ++= Seq(scalaReflect(scalaVersion.value), specs2MatcherExtra.value, specs2ScalaCheck.value),
    generateActionOps := ActionOpsBoilerplate((sourceManaged in Compile).value, streams.value),
    (sourceGenerators in Compile) += generateActionOps.taskValue)

lazy val parboiledCoreJVM = parboiledCore.jvm
lazy val parboiledCoreJS = parboiledCore.js
