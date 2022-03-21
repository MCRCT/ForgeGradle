package ch.minecraftreunitedcollaborationteam.gradle.user.patch;

import static ch.minecraftreunitedcollaborationteam.gradle.user.patch.UserPatchConstants.*;
import ch.minecraftreunitedcollaborationteam.gradle.common.Constants;
import ch.minecraftreunitedcollaborationteam.gradle.tasks.ProcessJarTask;
import ch.minecraftreunitedcollaborationteam.gradle.tasks.ProcessSrcJarTask;

import org.gradle.api.Project;

public class FmlUserPlugin extends UserPatchBasePlugin
{
    @Override
    public String getApiName()
    {
        return "fml";
    }
    
    @Override
    protected String getApiGroup()
    {
        return "{API_GROUP}";
    }
    
    /**
     * Should be with separate with slashes.
     */
    protected String getApiPath(UserPatchExtension exten)
    {
        return getMcVersion(exten).startsWith("1.8") ? "net/minecraftforge" : "cpw/mods";
    }

    @Override
    protected void configureDeobfuscation(ProcessJarTask task)
    {
        task.addTransformerClean(delayedFile(FML_AT));       
    }
    
    @Override
    protected void configurePatching(ProcessSrcJarTask patch)
    {
        patch.addStage("fml", delayedFile(FML_PATCHES_ZIP), delayedFile(SRC_DIR), delayedFile(RES_DIR));
    }
    
    @Override
    protected void doVersionChecks(String version, int buildNumber)
    {
        if (buildNumber < 883) {
            throw new IllegalArgumentException("ForgeGradle 1.2 only works for FML versions 7.2.132.882+");
        }
    }
    
    @Override
    public String resolve(String pattern, Project project, UserPatchExtension exten)
    {
        pattern = super.resolve(pattern, project, exten);
        
        pattern = pattern.replace("{API_GROUP}", getMcVersion(exten).startsWith("1.8") ? "net.minecraftforge" : "cpw.mods");
        
        return pattern;
    }

    @Override
    protected String getVersionsJsonUrl()
    {
        // TODO Auto-generated method stub
        return Constants.FORGE_MAVEN + "/net/minecraftforge/fml/json";
    }
}
