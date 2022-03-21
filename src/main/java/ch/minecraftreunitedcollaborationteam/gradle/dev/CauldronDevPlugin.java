package ch.minecraftreunitedcollaborationteam.gradle.dev;

import static ch.minecraftreunitedcollaborationteam.gradle.dev.DevConstants.*;

import ch.minecraftreunitedcollaborationteam.gradle.common.Constants;
import ch.minecraftreunitedcollaborationteam.gradle.tasks.*;
import ch.minecraftreunitedcollaborationteam.gradle.tasks.abstractutil.DelayedJar;
import ch.minecraftreunitedcollaborationteam.gradle.tasks.abstractutil.ExtractTask;
import ch.minecraftreunitedcollaborationteam.gradle.tasks.dev.GenBinaryPatches;
import ch.minecraftreunitedcollaborationteam.gradle.tasks.dev.GenDevProjectsTask;
import ch.minecraftreunitedcollaborationteam.gradle.tasks.dev.SubprojectTask;
import groovy.lang.Closure;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.Date;

import ch.minecraftreunitedcollaborationteam.gradle.CopyInto;
import ch.minecraftreunitedcollaborationteam.gradle.common.Constants;
import ch.minecraftreunitedcollaborationteam.gradle.delayed.DelayedFile;
import ch.minecraftreunitedcollaborationteam.gradle.tasks.ApplyS2STask;
import ch.minecraftreunitedcollaborationteam.gradle.tasks.DecompileTask;
import ch.minecraftreunitedcollaborationteam.gradle.tasks.ExtractS2SRangeTask;
import ch.minecraftreunitedcollaborationteam.gradle.tasks.ProcessJarTask;
import ch.minecraftreunitedcollaborationteam.gradle.tasks.ProcessSrcJarTask;
import ch.minecraftreunitedcollaborationteam.gradle.tasks.RemapSourcesTask;
import ch.minecraftreunitedcollaborationteam.gradle.tasks.abstractutil.DelayedJar;
import ch.minecraftreunitedcollaborationteam.gradle.tasks.abstractutil.ExtractTask;
import ch.minecraftreunitedcollaborationteam.gradle.tasks.abstractutil.FileFilterTask;
import ch.minecraftreunitedcollaborationteam.gradle.tasks.dev.ChangelogTask;
import ch.minecraftreunitedcollaborationteam.gradle.tasks.dev.FMLVersionPropTask;
import ch.minecraftreunitedcollaborationteam.gradle.tasks.dev.GenBinaryPatches;
import ch.minecraftreunitedcollaborationteam.gradle.tasks.dev.GenDevProjectsTask;
import ch.minecraftreunitedcollaborationteam.gradle.tasks.dev.GeneratePatches;
import ch.minecraftreunitedcollaborationteam.gradle.tasks.dev.ObfuscateTask;
import ch.minecraftreunitedcollaborationteam.gradle.tasks.dev.SubprojectTask;

import org.gradle.api.DefaultTask;
import org.gradle.api.Task;
import org.gradle.api.file.ConfigurableFileTree;
import org.gradle.api.file.DuplicatesStrategy;
import org.gradle.api.java.archives.Manifest;
import org.gradle.api.specs.Spec;
import org.gradle.api.tasks.Delete;
import org.gradle.api.tasks.bundling.Zip;

public class CauldronDevPlugin extends DevBasePlugin
{
    @Override
    public void applyPlugin()
    {
        super.applyPlugin();

        // set folders
        getExtension().setFmlDir("forge/fml");
        getExtension().setForgeDir("forge");
        getExtension().setBukkitDir("bukkit");

        /* Not needed for anything and is broken. **
        // configure genSrg task.
        GenSrgTask genSrgTask = (GenSrgTask) project.getTasks().getByName("genSrgs");
        {
            String[] paths = {DevConstants.FML_RESOURCES, DevConstants.FORGE_RESOURCES, DevConstants.EXTRA_RESOURCES};
            for (String path : paths)
            {
                for (File f : project.fileTree(delayedFile(path).call()).getFiles())
                {
                    if(f.getPath().endsWith(".exc"))
                        genSrgTask.addExtraExc(f);
                    else if(f.getPath().endsWith(".srg"))
                        genSrgTask.addExtraSrg(f);
                }
            }
        }
        */

        createJarProcessTasks();
        createProjectTasks();
        createEclipseTasks();
        createMiscTasks();
        createSourceCopyTasks();
        createPackageTasks();

        // the master setup task.
        Task task = makeTask("setupCauldron", DefaultTask.class);
        task.dependsOn("extractCauldronSources", "generateProjects", "eclipse", "copyAssets");
        task.setGroup("Cauldron");

        // clean packages
        {
            Delete del = makeTask("cleanPackages", Delete.class);
            del.delete("build/distributions");
        }

//        // the master task.
        task = makeTask("buildPackages");
        //task.dependsOn("launch4j", "createChangelog", "packageUniversal", "packageInstaller", "genJavadocs");
        task.dependsOn("cleanPackages", "createChangelog", "packageUniversal", "packageInstaller");
        task.setGroup("Cauldron");
    }

    @Override
    protected final DelayedFile getDevJson()
    {
        return delayedFile(DevConstants.EXTRA_JSON_DEV);
    }

    protected void createJarProcessTasks()
    {
        ProcessJarTask task2 = makeTask("deobfuscateJar", ProcessJarTask.class);
        {
            task2.setInJar(delayedFile(Constants.JAR_MERGED));
            task2.setOutCleanJar(delayedFile(DevConstants.JAR_SRG_CDN));
            task2.setSrg(delayedFile(DevConstants.JOINED_SRG));
            task2.setExceptorCfg(delayedFile(DevConstants.JOINED_EXC));
            task2.setExceptorJson(delayedFile(DevConstants.EXC_JSON));
            task2.addTransformerClean(delayedFile(DevConstants.FML_RESOURCES + "/fml_at.cfg"));
            task2.addTransformerClean(delayedFile(DevConstants.FORGE_RESOURCES + "/forge_at.cfg"));
            task2.setApplyMarkers(true);
            task2.dependsOn("downloadMcpTools", "mergeJars");
        }

        DecompileTask task3 = makeTask("decompile", DecompileTask.class);
        {
            task3.setInJar(delayedFile(DevConstants.JAR_SRG_CDN));
            task3.setOutJar(delayedFile(DevConstants.ZIP_DECOMP_CDN));
            task3.setFernFlower(delayedFile(Constants.FERNFLOWER));
            task3.setPatch(delayedFile(DevConstants.MCP_PATCH_DIR));
            task3.setAstyleConfig(delayedFile(DevConstants.ASTYLE_CFG));
            task3.dependsOn("downloadMcpTools", "deobfuscateJar");
        }

        ProcessSrcJarTask task4 = makeTask("forgePatchJar", ProcessSrcJarTask.class);
        {
            task4.setInJar(delayedFile(DevConstants.ZIP_DECOMP_CDN));
            task4.setOutJar(delayedFile(DevConstants.ZIP_FORGED_CDN));
            task4.addStage("fml", delayedFile(DevConstants.FML_PATCH_DIR), delayedFile(DevConstants.FML_SOURCES), delayedFile(DevConstants.FML_RESOURCES), delayedFile("{FML_CONF_DIR}/patches/Start.java"), delayedFile(DevConstants.DEOBF_DATA), delayedFile(DevConstants.FML_VERSIONF));
            task4.addStage("forge", delayedFile(DevConstants.FORGE_PATCH_DIR), delayedFile(DevConstants.FORGE_SOURCES), delayedFile(DevConstants.FORGE_RESOURCES));
            task4.addStage("bukkit", null, delayedFile(DevConstants.BUKKIT_SOURCES));
            task4.setDoesCache(false);
            task4.setMaxFuzz(2);
            task4.dependsOn("decompile", "compressDeobfData", "createVersionPropertiesFML");
        }

        RemapSourcesTask task6 = makeTask("remapCleanJar", RemapSourcesTask.class);
        {
            task6.setInJar(delayedFile(DevConstants.ZIP_FORGED_CDN));
            task6.setOutJar(delayedFile(DevConstants.REMAPPED_CLEAN));
            task6.setMethodsCsv(delayedFile(DevConstants.METHODS_CSV));
            task6.setFieldsCsv(delayedFile(DevConstants.FIELDS_CSV));
            task6.setParamsCsv(delayedFile(DevConstants.PARAMS_CSV));
            task6.setDoesCache(true);
            task6.setNoJavadocs();
            task6.dependsOn("forgePatchJar");
        }

        task4 = makeTask("cauldronPatchJar", ProcessSrcJarTask.class);
        {
            //task4.setInJar(delayedFile(ZIP_FORGED_CDN)); UNCOMMENT FOR SRG NAMES
            task4.setInJar(delayedFile(DevConstants.REMAPPED_CLEAN));
            task4.setOutJar(delayedFile(DevConstants.ZIP_PATCHED_CDN));
            task4.addStage("Cauldron", delayedFile(DevConstants.EXTRA_PATCH_DIR));
            task4.setDoesCache(false);
            task4.setMaxFuzz(2);
            task4.dependsOn("forgePatchJar", "remapCleanJar");
        }

        task6 = makeTask("remapCauldronJar", RemapSourcesTask.class);
        {
            task6.setInJar(delayedFile(DevConstants.ZIP_PATCHED_CDN));
            task6.setOutJar(delayedFile(DevConstants.ZIP_RENAMED_CDN));
            task6.setMethodsCsv(delayedFile(DevConstants.METHODS_CSV));
            task6.setFieldsCsv(delayedFile(DevConstants.FIELDS_CSV));
            task6.setParamsCsv(delayedFile(DevConstants.PARAMS_CSV));
            task6.setDoesCache(true);
            task6.setNoJavadocs();
            task6.dependsOn("cauldronPatchJar");
        }
    }

    @SuppressWarnings({ "unchecked", "rawtypes" })
    private void createSourceCopyTasks()
    {
        ExtractTask task = makeTask("extractCleanResources", ExtractTask.class);
        {
            task.exclude(JAVA_FILES);
            task.setIncludeEmptyDirs(false);
            task.from(delayedFile(DevConstants.REMAPPED_CLEAN));
            task.into(delayedFile(DevConstants.ECLIPSE_CLEAN_RES));
            task.dependsOn("extractWorkspace", "remapCleanJar");
        }

        task = makeTask("extractCleanSource", ExtractTask.class);
        {
            task.include(JAVA_FILES);
            task.setIncludeEmptyDirs(false);
            task.from(delayedFile(DevConstants.REMAPPED_CLEAN));
            task.into(delayedFile(DevConstants.ECLIPSE_CLEAN_SRC));
            task.dependsOn("extractCleanResources");
        }

        task = makeTask("extractCauldronResources", ExtractTask.class);
        {
            task.exclude(JAVA_FILES);
            task.from(delayedFile(DevConstants.ZIP_RENAMED_CDN));
            task.into(delayedFile(DevConstants.ECLIPSE_CDN_RES));
            task.dependsOn("remapCauldronJar", "extractWorkspace");
            task.onlyIf(new Spec() {

                @Override
                public boolean isSatisfiedBy(Object arg0)
                {
                    File dir = delayedFile(DevConstants.ECLIPSE_CDN_RES).call();
                    if (!dir.exists())
                        return true;

                    ConfigurableFileTree tree = project.fileTree(dir);
                    tree.include("**/*.java");

                    return !tree.isEmpty();
                }

            });
        }

        task = makeTask("extractCauldronSources", ExtractTask.class);
        {
            task.include(JAVA_FILES);
            task.from(delayedFile(DevConstants.ZIP_RENAMED_CDN));
            task.into(delayedFile(DevConstants.ECLIPSE_CDN_SRC));
            task.dependsOn("extractCauldronResources");
            task.onlyIf(new Spec() {

                @Override
                public boolean isSatisfiedBy(Object arg0)
                {
                    File dir = delayedFile(DevConstants.ECLIPSE_CDN_SRC).call();
                    if (!dir.exists())
                        return true;

                    ConfigurableFileTree tree = project.fileTree(dir);
                    tree.include("**/*.java");

                    return !tree.isEmpty();
                }

            });
        }
    }

    @SuppressWarnings("serial")
    private void createProjectTasks()
    {
        FMLVersionPropTask sub = makeTask("createVersionPropertiesFML", FMLVersionPropTask.class);
        {
            //sub.setTasks("createVersionProperties");
            //sub.setBuildFile(delayedFile("{FML_DIR}/build.gradle"));
            sub.setVersion(new Closure<String>(project)
            {
                @Override
                public String call(Object... args)
                {
                    return FmlDevPlugin.getVersionFromGit(project, new File(delayedString("{FML_DIR}").call()));
                }
            });
            sub.setOutputFile(delayedFile(DevConstants.FML_VERSIONF));
        }

        ExtractTask extract = makeTask("extractRes", ExtractTask.class);
        {
            extract.into(delayedFile(DevConstants.EXTRACTED_RES));
            for (File f : delayedFile("src/main").call().listFiles())
            {
                if (f.isDirectory())
                    continue;
                String path = f.getAbsolutePath();
                if (path.endsWith(".jar") || path.endsWith(".zip"))
                    extract.from(delayedFile(path));
            }
        }

        GenDevProjectsTask task = makeTask("generateProjectClean", GenDevProjectsTask.class);
        {
            task.setTargetDir(delayedFile(DevConstants.ECLIPSE_CLEAN));
            task.setJson(delayedFile(DevConstants.EXTRA_JSON_DEV)); // Change to FmlConstants.JSON_BASE eventually, so that it's the base vanilla json
            task.addSource(delayedFile(DevConstants.ECLIPSE_CLEAN_SRC));
            task.addResource(delayedFile(DevConstants.ECLIPSE_CLEAN_RES));

            task.setMcVersion(delayedString("{MC_VERSION}"));
            task.setMappingChannel(delayedString("{MAPPING_CHANNEL}"));
            task.setMappingVersion(delayedString("{MAPPING_VERSION}"));

            task.dependsOn("extractNatives");
        }

        task = makeTask("generateProjectCauldron", GenDevProjectsTask.class);
        {
            task.setJson(delayedFile(DevConstants.EXTRA_JSON_DEV));
            task.setTargetDir(delayedFile(DevConstants.ECLIPSE_CDN));

            task.addSource(delayedFile(DevConstants.ECLIPSE_CDN_SRC));
            task.addSource(delayedFile(DevConstants.EXTRA_SOURCES));
            task.addTestSource(delayedFile(DevConstants.EXTRA_TEST_SOURCES));

            task.addResource(delayedFile(DevConstants.ECLIPSE_CDN_RES));
            task.addResource(delayedFile(DevConstants.EXTRA_RESOURCES));
            task.addResource(delayedFile(DevConstants.EXTRACTED_RES));
            task.addTestSource(delayedFile(DevConstants.EXTRA_TEST_SOURCES));

            task.setMcVersion(delayedString("{MC_VERSION}"));
            task.setMappingChannel(delayedString("{MAPPING_CHANNEL}"));
            task.setMappingVersion(delayedString("{MAPPING_VERSION}"));

            task.dependsOn("extractRes", "extractNatives","createVersionPropertiesFML");
        }

        makeTask("generateProjects").dependsOn("generateProjectClean", "generateProjectCauldron");
    }

    private void createEclipseTasks()
    {
        SubprojectTask task = makeTask("eclipseClean", SubprojectTask.class);
        {
            task.setBuildFile(delayedFile(DevConstants.ECLIPSE_CLEAN + "/build.gradle"));
            task.setTasks("eclipse");
            task.dependsOn("extractCleanSource", "generateProjects");
        }

        task = makeTask("eclipseCauldron", SubprojectTask.class);
        {
            task.setBuildFile(delayedFile(DevConstants.ECLIPSE_CDN + "/build.gradle"));
            task.setTasks("eclipse");
            task.dependsOn("extractCauldronSources", "generateProjects");
        }

        makeTask("eclipse").dependsOn("eclipseClean", "eclipseCauldron");
    }

    @SuppressWarnings("unused")
    private void createMiscTasks()
    {
        DelayedFile rangeMapClean = delayedFile("{BUILD_DIR}/tmp/rangemapCLEAN.txt");
        DelayedFile rangeMapDirty = delayedFile("{BUILD_DIR}/tmp/rangemapDIRTY.txt");

        ExtractS2SRangeTask extractRange = makeTask("extractRangeCauldron", ExtractS2SRangeTask.class);
        {
            extractRange.setLibsFromProject(delayedFile(DevConstants.ECLIPSE_CDN + "/build.gradle"), "compile", true);
            extractRange.addIn(delayedFile(DevConstants.ECLIPSE_CDN_SRC));
            extractRange.setRangeMap(rangeMapDirty);
        }

        ApplyS2STask applyS2S = makeTask("retroMapCauldron", ApplyS2STask.class);
        {
            applyS2S.addIn(delayedFile(DevConstants.ECLIPSE_CDN_SRC));
            applyS2S.setOut(delayedFile(DevConstants.PATCH_DIRTY));
            applyS2S.addSrg(delayedFile(DevConstants.MCP_2_SRG_SRG));
            applyS2S.addExc(delayedFile(DevConstants.MCP_EXC));
            applyS2S.addExc(delayedFile(DevConstants.SRG_EXC)); // just in case
            applyS2S.setRangeMap(rangeMapDirty);
            applyS2S.dependsOn("genSrgs", extractRange);
            String[] paths = {DevConstants.FML_RESOURCES, DevConstants.FORGE_RESOURCES, DevConstants.EXTRA_RESOURCES};
            for (String path : paths)
            {
                for (File f : project.fileTree(delayedFile(path).call()).getFiles())
                {
                    if(f.getPath().endsWith(".exc"))
                        applyS2S.addExc(f);
                    else if(f.getPath().endsWith(".srg"))
                        applyS2S.addSrg(f);
                }
            }
        }

        extractRange = makeTask("extractRangeClean", ExtractS2SRangeTask.class);
        {
            extractRange.setLibsFromProject(delayedFile(DevConstants.ECLIPSE_CLEAN + "/build.gradle"), "compile", true);
            extractRange.addIn(delayedFile(DevConstants.REMAPPED_CLEAN));
            extractRange.setRangeMap(rangeMapClean);
        }

        applyS2S = makeTask("retroMapClean", ApplyS2STask.class);
        {
            applyS2S.addIn(delayedFile(DevConstants.REMAPPED_CLEAN));
            applyS2S.setOut(delayedFile(DevConstants.PATCH_CLEAN));
            applyS2S.addSrg(delayedFile(DevConstants.MCP_2_SRG_SRG));
            applyS2S.addExc(delayedFile(DevConstants.MCP_EXC));
            applyS2S.addExc(delayedFile(DevConstants.SRG_EXC)); // just in case
            applyS2S.setRangeMap(rangeMapClean);
            applyS2S.dependsOn("genSrgs", extractRange);
        }

        GeneratePatches task2 = makeTask("genPatches", GeneratePatches.class);
        {
            task2.setPatchDir(delayedFile(DevConstants.EXTRA_PATCH_DIR));
            task2.setOriginal(delayedFile(DevConstants.ECLIPSE_CLEAN_SRC));
            task2.setChanged(delayedFile(DevConstants.ECLIPSE_CDN_SRC));
            task2.setOriginalPrefix("../src-base/minecraft");
            task2.setChangedPrefix("../src-work/minecraft");
            task2.getTaskDependencies().getDependencies(task2).clear(); // remove all the old dependants.
            task2.setGroup("Cauldron");
        }

        if (false) // COMMENT OUT SRG PATCHES!
        {
            task2.setPatchDir(delayedFile(DevConstants.EXTRA_PATCH_DIR));
            task2.setOriginal(delayedFile(DevConstants.PATCH_CLEAN)); // was ECLIPSE_CLEAN_SRC
            task2.setChanged(delayedFile(DevConstants.PATCH_DIRTY)); // ECLIPSE_FORGE_SRC
            task2.setOriginalPrefix("../src-base/minecraft");
            task2.setChangedPrefix("../src-work/minecraft");
            task2.dependsOn("retroMapCauldron", "retroMapClean");
            task2.setGroup("Cauldron");
        }

        Delete clean = makeTask("cleanCauldron", Delete.class);
        {
            clean.delete("eclipse");
            clean.setGroup("Clean");
        }
        project.getTasks().getByName("clean").dependsOn("cleanCauldron");

        ObfuscateTask obf = makeTask("obfuscateJar", ObfuscateTask.class);
        {
            obf.setSrg(delayedFile(DevConstants.MCP_2_NOTCH_SRG));
            obf.setExc(delayedFile(DevConstants.JOINED_EXC));
            obf.setReverse(false);
            obf.setPreFFJar(delayedFile(DevConstants.JAR_SRG_CDN));
            obf.setOutJar(delayedFile(DevConstants.REOBF_TMP));
            obf.setBuildFile(delayedFile(DevConstants.ECLIPSE_CDN + "/build.gradle"));
            obf.setMethodsCsv(delayedFile(DevConstants.METHODS_CSV));
            obf.setFieldsCsv(delayedFile(DevConstants.FIELDS_CSV));
            obf.dependsOn("genSrgs");
        }

        GenBinaryPatches task3 = makeTask("genBinPatches", GenBinaryPatches.class);
        {
            task3.setCleanClient(delayedFile(Constants.JAR_CLIENT_FRESH));
            task3.setCleanServer(delayedFile(Constants.JAR_SERVER_FRESH));
            task3.setCleanMerged(delayedFile(Constants.JAR_MERGED));
            task3.setDirtyJar(delayedFile(DevConstants.REOBF_TMP));
            task3.setDeobfDataLzma(delayedFile(DevConstants.DEOBF_DATA));
            task3.setOutJar(delayedFile(DevConstants.BINPATCH_TMP));
            task3.setSrg(delayedFile(DevConstants.JOINED_SRG));
            task3.addPatchList(delayedFileTree(DevConstants.EXTRA_PATCH_DIR));
            task3.addPatchList(delayedFileTree(DevConstants.FORGE_PATCH_DIR));
            task3.addPatchList(delayedFileTree(DevConstants.FML_PATCH_DIR));
            task3.dependsOn("obfuscateJar", "compressDeobfData");
        }

        /*
        ForgeVersionReplaceTask task4 = makeTask("ciWriteBuildNumber", ForgeVersionReplaceTask.class);
        {
            task4.getOutputs().upToDateWhen(Constants.CALL_FALSE);
            task4.setOutputFile(delayedFile(FORGE_VERSION_JAVA));
            task4.setReplacement(delayedString("{BUILD_NUM}"));
        }

        SubmoduleChangelogTask task5 = makeTask("fmlChangelog", SubmoduleChangelogTask.class);
        {
            task5.setSubmodule(delayedFile("fml"));
            task5.setModuleName("FML");
            task5.setPrefix("MinecraftForge/FML");
        }
        */
    }

    @SuppressWarnings("serial")
    private void createPackageTasks()
    {

        ChangelogTask log = makeTask("createChangelog", ChangelogTask.class);
        {
            log.getOutputs().upToDateWhen(Constants.CALL_FALSE);
            log.setServerRoot(delayedString("{JENKINS_SERVER}"));
            log.setJobName(delayedString("{JENKINS_JOB}"));
            log.setAuthName(delayedString("{JENKINS_AUTH_NAME}"));
            log.setAuthPassword(delayedString("{JENKINS_AUTH_PASSWORD}"));
            log.setTargetBuild(delayedString("{BUILD_NUM}"));
            log.setOutput(delayedFile(DevConstants.CHANGELOG));
        }

        /*
        VersionJsonTask vjson = makeTask("generateVersionJson", VersionJsonTask.class);
        {
            vjson.setInput(delayedFile(INSTALL_PROFILE));
            vjson.setOutput(delayedFile(VERSION_JSON));
            vjson.dependsOn("generateInstallJson");
        }
        */

        final DelayedJar uni = makeTask("packageUniversal", DelayedJar.class);
        {
            uni.setClassifier(delayedString("B{BUILD_NUM}").call());
            uni.getInputs().file(delayedFile(DevConstants.EXTRA_JSON_REL));
            uni.getOutputs().upToDateWhen(Constants.CALL_FALSE);
            uni.from(delayedZipTree(DevConstants.BINPATCH_TMP));
            uni.from(delayedFileTree(DevConstants.EXTRA_RESOURCES));
            uni.from(delayedFileTree(DevConstants.FORGE_RESOURCES));
            uni.from(delayedFileTree(DevConstants.FML_RESOURCES));
            uni.from(delayedFileTree(DevConstants.EXTRACTED_RES));
            uni.from(delayedFile(DevConstants.FML_VERSIONF));
            uni.from(delayedFile(DevConstants.FML_LICENSE));
            uni.from(delayedFile(DevConstants.FML_CREDITS));
            uni.from(delayedFile(DevConstants.FORGE_LICENSE));
            uni.from(delayedFile(DevConstants.FORGE_CREDITS));
            uni.from(delayedFile(DevConstants.PAULSCODE_LISCENCE1));
            uni.from(delayedFile(DevConstants.PAULSCODE_LISCENCE2));
            uni.from(delayedFile(DevConstants.DEOBF_DATA));
            uni.from(delayedFile(DevConstants.CHANGELOG));
            uni.from(delayedFile(DevConstants.VERSION_JSON));
            uni.exclude("devbinpatches.pack.lzma");
            uni.setDuplicatesStrategy(DuplicatesStrategy.EXCLUDE);
            uni.setIncludeEmptyDirs(false);
            uni.setManifest(new Closure<Object>(project)
            {
                public Object call()
                {
                    Manifest mani = (Manifest) getDelegate();
                    mani.getAttributes().put("Main-Class", delayedString("{MAIN_CLASS}").call());
                    mani.getAttributes().put("TweakClass", delayedString("{FML_TWEAK_CLASS}").call());
                    mani.getAttributes().put("Class-Path", getServerClassPath(delayedFile(DevConstants.EXTRA_JSON_REL).call()));
                    return null;
                }
            });

//            uni.doLast(new Action<Task>()
//            {
//                @Override
//                public void execute(Task arg0)
//                {
//                    try
//                    {
//                        signJar(((DelayedJar)arg0).getArchivePath(), "forge", "*/*/**", "!paulscode/**");
//                    }
//                    catch (Exception e)
//                    {
//                        Throwables.propagate(e);
//                    }
//                }
//            });

            uni.setDestinationDir(delayedFile("{BUILD_DIR}/distributions").call());
            //uni.dependsOn("genBinPatches", "createChangelog", "createVersionPropertiesFML", "generateVersionJson");
            uni.dependsOn("genBinPatches", "createChangelog", "createVersionPropertiesFML");
        }
        project.getArtifacts().add("archives", uni);

        FileFilterTask task = makeTask("generateInstallJson", FileFilterTask.class);
        {
            task.setInputFile(delayedFile(DevConstants.EXTRA_JSON_REL));
            task.setOutputFile(delayedFile(DevConstants.INSTALL_PROFILE));
            task.addReplacement("@minecraft_version@", delayedString("{MC_VERSION}"));
            task.addReplacement("@version@", delayedString("{VERSION}"));
            task.addReplacement("@project@", delayedString("cauldron"));
            task.addReplacement("@artifact@", delayedString("net.minecraftforge:forge:{MC_VERSION}-{VERSION}"));
            task.addReplacement("@universal_jar@", new Closure<String>(project)
            {
                public String call()
                {
                    return uni.getArchiveName();
                }
            });
            task.addReplacement("@timestamp@", new Closure<String>(project)
            {
                public String call()
                {
                    return (new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssZ")).format(new Date());
                }
            });
        }

        Zip inst = makeTask("packageInstaller", Zip.class);
        {
            inst.setClassifier("installer");
            inst.from(new Closure<File>(project) {
                public File call()
                {
                    return uni.getArchivePath();
                }
            });
            inst.from(delayedFile(DevConstants.INSTALL_PROFILE));
            inst.from(delayedFile(DevConstants.CHANGELOG));
            inst.from(delayedFile(DevConstants.FML_LICENSE));
            inst.from(delayedFile(DevConstants.FML_CREDITS));
            inst.from(delayedFile(DevConstants.FORGE_LICENSE));
            inst.from(delayedFile(DevConstants.FORGE_CREDITS));
            inst.from(delayedFile(DevConstants.PAULSCODE_LISCENCE1));
            inst.from(delayedFile(DevConstants.PAULSCODE_LISCENCE2));
            inst.from(delayedFile(DevConstants.FORGE_LOGO));
            inst.from(delayedZipTree(DevConstants.INSTALLER_BASE), new CopyInto("", "!*.json", "!*.png"));
            inst.dependsOn("packageUniversal", "downloadBaseInstaller", "generateInstallJson");
            inst.rename("forge_logo\\.png", "big_logo.png");
            inst.setExtension("jar");
        }
        project.getArtifacts().add("archives", inst);
    }

    @Override
    public void afterEvaluate()
    {
        super.afterEvaluate();

        SubprojectTask task = (SubprojectTask) project.getTasks().getByName("eclipseClean");
        task.configureProject(getExtension().getSubprojects());
        task.configureProject(getExtension().getCleanProject());

        task = (SubprojectTask) project.getTasks().getByName("eclipseCauldron");
        task.configureProject(getExtension().getSubprojects());
        task.configureProject(getExtension().getCleanProject());
    }
}
