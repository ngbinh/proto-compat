import scalapb.compiler.Version.{scalapbVersion => ScalapbVersion}
import sbtprotoc.ProtocPlugin.{ProtobufConfig => Protobuf}
import sbtrelease.ReleasePlugin.autoImport.ReleaseTransformations._

lazy val ProtobufJavaVersion = "3.5.1"

lazy val runScriptedTests = taskKey[Unit]("Run all scripted tests.")

inThisBuild(
  Seq(
    organization := "com.anduintransact",
    scalaVersion := "2.12.4",
    // https://github.com/sbt/sbt/issues/3570
    updateOptions := updateOptions.value.withGigahorse(false),
    publishTo := Some(
      Resolver.url(
        "Anduin Transactions Artifactory",
        url("https://artifactory.anduin.co/artifactory/anduin-internal-libraries/")
      )(Resolver.ivyStylePatterns)
    ),
    publishMavenStyle := false
  )
)

lazy val `proto-compat-directives` = crossProject
  .crossType(CrossType.Pure)
  .in(file("modules") / "directives")
  .settings(
    libraryDependencies ++= Seq(
      "com.thesamet.scalapb" %% "scalapb-runtime" % ScalapbVersion excludeAll (
        "com.google.protobuf" % "protobuf-java"
      ),
      "com.google.protobuf" % "protobuf-java" % ProtobufJavaVersion % Protobuf,
      "com.thesamet.scalapb" %% "scalapb-runtime" % ScalapbVersion % Protobuf excludeAll (
        "com.google.protobuf" % "protobuf-java"
      )
    ),
    dependencyOverrides ++= Seq(
      "com.google.protobuf" % "protobuf-java" % ProtobufJavaVersion
    ),
    unmanagedResourceDirectories in Compile += {
      baseDirectory.value.getParentFile / "src" / "main" / "protobuf"
    },
    PB.protoSources in Compile := Seq(
      baseDirectory.value.getParentFile / "src" / "main" / "protobuf"
    ),
    PB.protocVersion := "-v351",
    PB.targets in Compile := Seq(
      protocbridge.gens.java -> (sourceManaged in Compile).value,
      scalapb.gen(
        flatPackage = true,
        grpc = false
      ) -> (sourceManaged in Compile).value
    )
  )

lazy val `proto-compat-directivesJVM` = `proto-compat-directives`.jvm
lazy val `proto-compat-directivesJS` = `proto-compat-directives`.js

lazy val `proto-compat-core` = project
  .in(file("modules") / "core")
  .dependsOn(`proto-compat-directivesJVM`)
  .settings(
    libraryDependencies ++= Seq(
      "org.typelevel" %% "cats-core" % "1.0.1",
      "com.google.protobuf" % "protobuf-java" % ProtobufJavaVersion,
      "com.trueaccord.scalapb" %% "protoc-bridge" % "0.3.0-M1",
      "com.github.os72" % "protoc-jar" % "3.5.1.1",
      "com.thesamet.scalapb" %% "scalapb-runtime" % ScalapbVersion excludeAll (
        "com.google.protobuf" % "protobuf-java"
      )
    ),
    scalacOptions += "-Ypartial-unification"
  )

lazy val `sbt-proto-compat` = project
  .in(file("modules") / "sbt-plugin")
  .dependsOn(`proto-compat-core`)
  .settings(
    sbtPlugin := true,
    libraryDependencies ++= Seq(
      Defaults.sbtPluginExtra(
        "com.thesamet" % "sbt-protoc" % "0.99.13",
        (sbtBinaryVersion in pluginCrossBuild).value,
        (scalaBinaryVersion in pluginCrossBuild).value
      )
    ),
    runScriptedTests := scripted.toTask("").value,
    publishLocal := publishLocal
      .dependsOn(
        // Hacky, why does this project have to know all transitive dependencies?
        publishLocal in `proto-compat-directivesJVM`,
        publishLocal in `proto-compat-core`
      )
      .value,
    scriptedLaunchOpts ++= Seq(
      "-Xmx1024M",
      "-DscalapbCompiler.version=" + ScalapbVersion,
      "-DprotoCompat.version=" + version.value
    ),
    scriptedBufferLog := false
  )

lazy val `proto-compat` = project
  .in(file("."))
  .aggregate(
    `proto-compat-directivesJVM`,
    `proto-compat-directivesJS`,
    `proto-compat-core`,
    `sbt-proto-compat`
  )
  .settings(
    publish := {},
    publishLocal := {},
    publishArtifact := false,
    releaseProcess := Seq[ReleaseStep](
      checkSnapshotDependencies,
      inquireVersions,
      runClean,
      runTest,
      releaseStepInputTask(scripted in `sbt-proto-compat`),
      setReleaseVersion,
      commitReleaseVersion,
      tagRelease,
      publishArtifacts,
      setNextVersion,
      commitNextVersion,
      pushChanges
    )
  )
