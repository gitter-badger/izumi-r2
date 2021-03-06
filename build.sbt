import com.typesafe.sbt.pgp.PgpSettings
import sbt.Keys.{baseDirectory, pomExtra, publishMavenStyle, sourceDirectory}
import com.github.pshirshov.izumi.sbt.deps.IzumiDeps._
import IzumiConvenienceTasksPlugin.Keys._
import IzumiPublishingPlugin.Keys._
import ReleaseTransformations._


enablePlugins(IzumiGitEnvironmentPlugin)
disablePlugins(AssemblyPlugin, ScriptedPlugin)

name := "izumi-r2"
organization in ThisBuild := "com.github.pshirshov.izumi.r2"
defaultStubPackage in ThisBuild := Some("com.github.pshirshov.izumi")
publishMavenStyle in ThisBuild := true
pomExtra in ThisBuild := <url>https://bitbucket.org/pshirshov/izumi-r2</url>
  <licenses>
    <license>
      <name>BSD-style</name>
      <url>http://www.opensource.org/licenses/bsd-license.php</url>
      <distribution>repo</distribution>
    </license>
  </licenses>
  <developers>
    <developer>
      <id>pshirshov</id>
      <name>Pavel Shirshov</name>
      <url>https://github.com/pshirshov</url>
    </developer>
  </developers>


releaseProcess := Seq[ReleaseStep](
  checkSnapshotDependencies, // : ReleaseStep
  inquireVersions, // : ReleaseStep
  runClean, // : ReleaseStep
  runTest, // : ReleaseStep
  setReleaseVersion, // : ReleaseStep
  commitReleaseVersion, // : ReleaseStep, performs the initial git checks
  tagRelease, // : ReleaseStep
  //publishArtifacts,                       // : ReleaseStep, checks whether `publishTo` is properly set up
  setNextVersion, // : ReleaseStep
  commitNextVersion, // : ReleaseStep
  pushChanges // : ReleaseStep, also checks that an upstream branch is properly configured
)

publishTargets in ThisBuild := PublishTarget.typical("sonatype-nexus", sonatypeTarget.value.root)

val GlobalSettings = new DefaultGlobalSettingsGroup {
  override val settings: Seq[sbt.Setting[_]] = Seq(
    crossScalaVersions := Seq(
      V.scala_212,
      V.scala_213,
    )
    , sonatypeProfileName := "com.github.pshirshov"
    , addCompilerPlugin(R.kind_projector)
  )
}

val AppSettings = new SettingsGroup {
  override val disabledPlugins: Set[AutoPlugin] = Set(SitePlugin)
  override val plugins = Set(AssemblyPlugin)
}


val LibSettings = new SettingsGroup {
  override val settings: Seq[sbt.Setting[_]] = Seq(
    Seq(
      libraryDependencies ++= R.essentials
      , libraryDependencies ++= T.essentials
    )
  ).flatten
}

val SbtSettings = new SettingsGroup {
  override val settings: Seq[sbt.Setting[_]] = Seq(
    Seq(
      target ~= { t => t.toPath.resolve("primary").toFile }
      , crossScalaVersions := Seq(
        V.scala_212
      )
      , libraryDependencies ++= Seq(
        "org.scala-sbt" % "sbt" % sbtVersion.value
      )
      , sbtPlugin := true
    )
  ).flatten
}

val ShadingSettings = new SettingsGroup {
  override val plugins: Set[Plugins] = Set(ShadingPlugin)

  override val settings: Seq[sbt.Setting[_]] = Seq(
    inConfig(_root_.coursier.ShadingPlugin.Shading)(PgpSettings.projectSettings ++ IzumiPublishingPlugin.projectSettings) ++
      _root_.coursier.ShadingPlugin.projectSettings ++
      Seq(
        publish := publish.in(Shading).value
        , publishLocal := publishLocal.in(Shading).value
        , PgpKeys.publishSigned := PgpKeys.publishSigned.in(Shading).value
        , PgpKeys.publishLocalSigned := PgpKeys.publishLocalSigned.in(Shading).value
        , shadingNamespace := "izumi.shaded"
        , shadeNamespaces ++= Set(
          "fastparse"
          , "sourcecode"
          //            , "net.sf.cglib"
          //            , "org.json4s"
        )
      )
  ).flatten
}

val WithoutBadPlugins = new SettingsGroup {
  override val disabledPlugins: Set[AutoPlugin] = Set(AssemblyPlugin, SitePlugin, ScriptedPlugin)

}

val WithoutBadPluginsSbt = new SettingsGroup {
  override val disabledPlugins: Set[AutoPlugin] = Set(AssemblyPlugin, SitePlugin)

}


val SbtScriptedSettings = new SettingsGroup {
  override val plugins: Set[Plugins] = Set(ScriptedPlugin)

  override val settings: Seq[sbt.Setting[_]] = Seq(
    Seq(
      scriptedLaunchOpts := {
        scriptedLaunchOpts.value ++
          Seq("-Xmx1024M", "-Dplugin.version=" + version.value)
      }
      , scriptedBufferLog := false
    )
  ).flatten
}




// --------------------------------------------

lazy val inRoot = In(".")
  .settings(GlobalSettings)

lazy val base = Seq(GlobalSettings, LibSettings, WithoutBadPlugins)

lazy val fbase = base ++ Seq(WithFundamentals)

lazy val inFundamentals = In("fundamentals")
  .settingsSeq(base)

lazy val inShade = In("shade")
  .settingsSeq(base)

lazy val inSbt = In("sbt")
  .settings(GlobalSettings, WithoutBadPluginsSbt)
  .settings(SbtSettings, SbtScriptedSettings)

lazy val inDiStage = In("distage")
  .settingsSeq(fbase)

lazy val inLogStage = In("logstage")
  .settingsSeq(fbase)

lazy val inIdealinguaBase = In("idealingua")
  .settings(GlobalSettings, WithFundamentals)

lazy val inIdealingua = inIdealinguaBase
  .settingsSeq(fbase)



// --------------------------------------------

lazy val fundamentalsCollections = inFundamentals.as.module
lazy val fundamentalsPlatform = inFundamentals.as.module
lazy val fundamentalsFunctional = inFundamentals.as.module

lazy val WithFundamentals = new SettingsGroup {
  override def sharedLibs: Seq[ProjectReferenceEx] = Seq(
    fundamentalsCollections
    , fundamentalsPlatform
    , fundamentalsFunctional
  )
}
// --------------------------------------------

lazy val fundamentalsReflection = inFundamentals.as.module
  .dependsOn(fundamentalsPlatform)
  .settings(
    libraryDependencies ++= Seq(
      R.scala_reflect % scalaVersion.value
    )
  )

lazy val distageModel = inDiStage.as.module
  .depends(fundamentalsReflection)

lazy val distageProxyCglib = inDiStage.as.module
  .depends(distageModel)
  .settings(
    libraryDependencies ++= Seq(
      R.scala_reflect % scalaVersion.value
      , R.cglib_nodep
    )
  )

lazy val distagePlugins = inDiStage.as.module
  .depends(distageCore)
  .settings(
    libraryDependencies ++= Seq(R.fast_classpath_scanner)
  )

lazy val distageConfig = inDiStage.as.module
  .depends(distageCore)
  .settings(
    libraryDependencies ++= Seq(
      R.typesafe_config
    )
  )

lazy val distageApp = inDiStage.as.module
  .depends(distageCore, distagePlugins, distageConfig, logstageDi)

lazy val distageCore = inDiStage.as.module
  .depends(fundamentalsFunctional, distageModel, distageProxyCglib)
  .settings(
    libraryDependencies ++= Seq(
      R.scala_reflect % scalaVersion.value
    )
  )

lazy val distageCats = inDiStage.as.module
  .depends(distageModel, distageCore.testOnlyRef)
  .settings(
    libraryDependencies += R.cats_kernel
    , libraryDependencies ++= T.cats_all
  )

lazy val distageStatic = inDiStage.as.module
  .depends(distageCore, distageApp.testOnlyRef)
  .settings(
    libraryDependencies += R.shapeless
  )

//-----------------------------------------------------------------------------

lazy val logstageApiBase = inLogStage.as.module

lazy val logstageApiBaseMacro = inLogStage.as.module
  .depends(logstageApiBase, fundamentalsReflection)
  .settings(
    libraryDependencies ++= Seq(
      R.scala_reflect % scalaVersion.value
    )
  )

lazy val logstageApiLogger = inLogStage.as.module
  .depends(logstageApiBaseMacro)

lazy val logstageDi = inLogStage.as.module
  .depends(
    logstageApiLogger
    , distageModel
  )
  .dependsSeq(Seq(
    distageCore
  ).map(_.testOnlyRef))


lazy val logstageAdapterSlf4j = inLogStage.as.module
  .depends(logstageApiLogger)
  .settings(
    libraryDependencies += R.slf4j_api
    , compileOrder in Compile := CompileOrder.Mixed
    , compileOrder in Test := CompileOrder.Mixed
  )

lazy val logstageRenderingJson4s = inLogStage.as.module
  .depends(logstageApiLogger)
  .dependsSeq(Seq(
    logstageSinkConsole
  ).map(_.testOnlyRef))
  .settings(libraryDependencies ++= Seq(R.json4s_native))

lazy val logstageSinkConsole = inLogStage.as.module
  .depends(logstageApiBase)
  .dependsSeq(Seq(
    logstageApiLogger
  ).map(_.testOnlyRef))

lazy val logstageSinkFile = inLogStage.as.module
  .depends(logstageApiBase)
  .dependsSeq(Seq(
    logstageApiLogger
  ).map(_.testOnlyRef))

lazy val logstageSinkSlf4j = inLogStage.as.module
  .depends(logstageApiBase)
  .dependsSeq(Seq(
    logstageApiLogger
  ).map(_.testOnlyRef))
  .settings(libraryDependencies ++= Seq(R.slf4j_api, T.slf4j_simple))
//-----------------------------------------------------------------------------

lazy val fastparseShaded = inShade.as.module
  .settings(libraryDependencies ++= Seq(R.fastparse))
  .settings(ShadingSettings)

lazy val idealinguaModel = inIdealingua.as.module
  .settings()

lazy val idealinguaRuntimeRpc = inIdealingua.as.module

lazy val idealinguaTestDefs = inIdealingua.as.module.dependsOn(idealinguaRuntimeRpc, idealinguaRuntimeRpcCirce)

lazy val idealinguaCore = inIdealingua.as.module
  .settings(libraryDependencies ++= Seq(R.scala_reflect % scalaVersion.value, R.scalameta) ++ Seq(R.scala_compiler % scalaVersion.value % "test"))
  .depends(idealinguaModel, idealinguaRuntimeRpc, fastparseShaded, idealinguaRuntimeRpcTypescript, idealinguaRuntimeRpcGo, idealinguaRuntimeRpcCSharp)
  .dependsSeq(Seq(idealinguaTestDefs).map(_.testOnlyRef))
  .settings(ShadingSettings)


lazy val idealinguaRuntimeRpcCirce = inIdealingua.as.module
  .depends(idealinguaRuntimeRpc)
  .settings(libraryDependencies ++= R.circe)

lazy val idealinguaRuntimeRpcCats = inIdealingua.as.module
  .depends(idealinguaRuntimeRpc)
  .settings(libraryDependencies ++= R.cats_all)

lazy val idealinguaRuntimeRpcHttp4s = inIdealingua.as.module
  .depends(idealinguaRuntimeRpcCirce, idealinguaRuntimeRpcCats)
  .dependsSeq(Seq(idealinguaTestDefs).map(_.testOnlyRef))
  .settings(libraryDependencies ++= R.http4s_all)

lazy val idealinguaExtensionRpcFormatCirce = inIdealingua.as.module
  .depends(idealinguaCore, idealinguaRuntimeRpcCirce)
  .dependsSeq(Seq(idealinguaTestDefs).map(_.testOnlyRef))


lazy val idealinguaRuntimeRpcTypescript = inIdealingua.as.module

lazy val idealinguaRuntimeRpcCSharp = inIdealingua.as.module

lazy val idealinguaRuntimeRpcGo = inIdealingua.as.module


lazy val idealinguaCompiler = inIdealinguaBase.as.module
  .depends(idealinguaCore, idealinguaExtensionRpcFormatCirce, idealinguaRuntimeRpcTypescript, idealinguaRuntimeRpcGo, idealinguaRuntimeRpcCSharp)
  .settings(AppSettings)
  .enablePlugins(ScriptedPlugin)
  .settings(
    libraryDependencies ++= Seq(R.scopt)
    , mainClass in assembly := Some("com.github.pshirshov.izumi.idealingua.compiler.CliIdlCompiler")
  )
  .settings(addArtifact(artifact in(Compile, assembly), assembly))


lazy val sbtIzumi = inSbt.as
  .module

lazy val sbtIzumiDeps = inSbt.as
  .module
  .settings(withBuildInfo("com.github.pshirshov.izumi.sbt.deps", "Izumi"))

lazy val sbtIdealingua = inSbt.as
  .module
  .depends(idealinguaCore, idealinguaExtensionRpcFormatCirce)

lazy val sbtTests = inSbt.as
  .module
  .depends(sbtIzumiDeps, sbtIzumi, sbtIdealingua)

lazy val logstage: Seq[ProjectReference] = Seq(
  logstageApiLogger
  , logstageDi
  , logstageSinkConsole
  , logstageSinkFile
  , logstageSinkSlf4j
  , logstageAdapterSlf4j
  , logstageRenderingJson4s
)
lazy val distage: Seq[ProjectReference] = Seq(
  distageApp
  , distageCats
  , distageStatic
)
lazy val idealingua: Seq[ProjectReference] = Seq(
  idealinguaCore
  , idealinguaRuntimeRpcHttp4s
  , idealinguaRuntimeRpcCats
  , idealinguaRuntimeRpcCirce
  , idealinguaExtensionRpcFormatCirce
  , idealinguaCompiler
)
lazy val izsbt: Seq[ProjectReference] = Seq(
  sbtIzumi, sbtIdealingua, sbtTests
)

lazy val allProjects = distage ++ logstage ++ idealingua ++ izsbt

lazy val `izumi-r2` = inRoot.as
  .root
  .transitiveAggregateSeq(allProjects)
  .enablePlugins(ScalaUnidocPlugin, ParadoxSitePlugin, SitePlugin, GhpagesPlugin, ParadoxMaterialThemePlugin)
  .settings(
    sourceDirectory in Paradox := baseDirectory.value / "doc" / "paradox"
    , siteSubdirName in ScalaUnidoc := "api"
    , previewFixedPort := Some(9999)
    , scmInfo := Some(ScmInfo(url("https://github.com/pshirshov/izumi-r2"), "git@github.com:pshirshov/izumi-r2.git"))
    , git.remoteRepo := scmInfo.value.get.connection
    , excludeFilter in ghpagesCleanSite := new FileFilter {
      def accept(f: File) = (ghpagesRepository.value / "CNAME").getCanonicalPath == f.getCanonicalPath
    }
    , paradoxProperties ++= Map(
      "scaladoc.izumi.base_url" -> s"/api/com/github/pshirshov/",
      "izumi.version" -> version.value,
    )
  )
  .settings(addMappingsToSiteDir(mappings in(ScalaUnidoc, packageDoc), siteSubdirName in ScalaUnidoc))
  .settings(ParadoxMaterialThemePlugin.paradoxMaterialThemeSettings(Paradox))
