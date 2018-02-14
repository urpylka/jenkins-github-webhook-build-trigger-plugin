/*
 * Licensed under MIT License
 * Copyright (c) 2017 Bernhard Grünewaldt
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
public class EnvironmentContributionAction_Release implements EnvironmentContributingAction {

    private transient Map<String, String> environmentVariables = new HashMap<>();
    private transient String envVarInfo;

    public EnvironmentContributionAction_Release(GithubWebhookPayload_Release payload) {
        StringBuilder info = new StringBuilder();
        info.append("   webhook\n      -> $GWBT_TRIGGER            : ").append("release").append("\n");
        info.append("   release.published_at\n      -> $GWBT_RELEASE_PUBLISHED_AT            : ").append(payload.getRelease().getPublished_at()).append("\n");
        info.append("   release.tag_name\n      -> $GWBT_RELEASE_TAG_NAME            : ").append(payload.getRelease().getTag_name()).append("\n");
        info.append("   release.id\n      -> $GWBT_RELEASE_ID            : ").append(payload.getRelease().getId()).append("\n");
        info.append("   release.body\n      -> $GWBT_RELEASE_BODY            : ").append(payload.getRelease().getBody()).append("\n");

        info.append("   repository.clone_url\n      -> $GWBT_REPO_CLONE_URL : ").append(payload.getRepository().getClone_url()).append("\n\n");
        info.append("   repository.html_url\n      -> $GWBT_REPO_HTML_URL  : ").append(payload.getRepository().getHtml_url()).append("\n\n");
        info.append("   repository.full_name\n      -> $GWBT_REPO_FULL_NAME : ").append(payload.getRepository().getFull_name()).append("\n\n");
        info.append("   repository.name\n      -> $GWBT_REPO_NAME      : ").append(payload.getRepository().getName()).append("\n\n");

        this.envVarInfo = info.toString();
        this.environmentVariables.put("GWBT_TRIGGER", "release");

        this.environmentVariables.put("GWBT_RELEASE_PUBLISHED_AT", payload.getRelease().getPublished_at());
        this.environmentVariables.put("GWBT_RELEASE_TAG_NAME", payload.getRelease().getTag_name());
        this.environmentVariables.put("GWBT_RELEASE_ID", payload.getRelease().getId());
        this.environmentVariables.put("GWBT_RELEASE_BODY", payload.getRelease().getBody());

        this.environmentVariables.put("GWBT_REPO_CLONE_URL", payload.getRepository().getClone_url());
        this.environmentVariables.put("GWBT_REPO_HTML_URL", payload.getRepository().getHtml_url());
        this.environmentVariables.put("GWBT_REPO_FULL_NAME", payload.getRepository().getFull_name());
        this.environmentVariables.put("GWBT_REPO_NAME", payload.getRepository().getName());
    }

    protected String getEnvVarInfo() {
        return this.envVarInfo;
    }

    public String getIconFileName() {
        return null;
    }

    public String getDisplayName() {
        return "EnvironmentContributionAction_Release";
    }

    public String getUrlName() {
        return "EnvironmentContributionAction_Release";
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
