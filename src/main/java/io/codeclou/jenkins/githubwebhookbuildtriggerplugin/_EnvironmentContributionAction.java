/*
 * Licensed under MIT License
 *
 * Autors:
 * Bernhard Grünewaldt
 * Artem Smirnov @urpylka
 *
 */

package io.codeclou.jenkins.githubwebhookbuildtriggerplugin;

import hudson.EnvVars;
import hudson.model.*;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/*
 * Inject Environment Variables into the triggered job
 */
public class _EnvironmentContributionAction implements EnvironmentContributingAction {

    private transient Map<String, String> environmentVariables = new HashMap<>();
    private transient String envVarInfo;

    public _EnvironmentContributionAction(String full_name, String checkout_ref, String clone_url, String payload_abs_path, String github_event) {

        StringBuilder info = new StringBuilder();
        info.append("   GWBT_REPO: ").append(full_name).append("\n");
        info.append("   GWBT_REF: ").append(checkout_ref).append("\n");
        info.append("   GWBT_URL: ").append(clone_url).append("\n");
        info.append("   GWBT_FILE: ").append(payload_abs_path).append("\n");
        info.append("   GWBT_EVENT: ").append(github_event).append("\n");
        this.envVarInfo = info.toString();

        this.environmentVariables.put("GWBT_REPO", full_name);
        this.environmentVariables.put("GWBT_REF", checkout_ref);
        this.environmentVariables.put("GWBT_URL", clone_url);
        this.environmentVariables.put("GWBT_FILE", payload_abs_path);
        this.environmentVariables.put("GWBT_EVENT", github_event);
    }

    protected String getEnvVarInfo() {
        return this.envVarInfo;
    }

    public String getIconFileName() {
        return null;
    }

    public String getDisplayName() {
        return "EnvironmentContributionAction";
    }

    public String getUrlName() {
        return "EnvironmentContributionAction";
    }

    @Override
    public void buildEnvVars(AbstractBuild<?, ?> build, EnvVars env) {
        if (env == null) {
            return;
        }
        if (environmentVariables != null) {
            env.putAll(environmentVariables);
        }
    }

    /**
     * Since WorkflowJob does not support EnvironmentContributionAction yet,
     * we need a ParametersAction filled with List ParameterValue
     * See: https://github.com/jenkinsci/workflow-job-plugin/blob/124b171b76394728f9c8504829cf6857abc8bdb5/src/main/java/org/jenkinsci/plugins/workflow/job/WorkflowRun.java#L435
     */
    public ParametersAction transform() {
        List<ParameterValue> paramValues = new ArrayList<>();
        List<String> safeParams = new ArrayList<>();
        for (Map.Entry<String, String> envVar : environmentVariables.entrySet()) {
            paramValues.add(new StringParameterValue(envVar.getKey(), envVar.getValue(), envVar.getValue()));
            safeParams.add(envVar.getKey());
        }
        return new ParametersAction(paramValues, safeParams);
    }
}
