/*
 * Licensed under MIT License
 *
 * Autors:
 * Bernhard Grünewaldt
 * Artem Smirnov @urpylka
 *
 */
 
package io.codeclou.jenkins.githubwebhookbuildtriggerplugin;

import hudson.Extension;
import hudson.model.*;
import hudson.util.HttpResponses;
import io.codeclou.jenkins.githubwebhookbuildtriggerplugin.config.GithubWebhookBuildTriggerPluginBuilder;
import io.codeclou.jenkins.githubwebhookbuildtriggerplugin.webhooksecret.GitHubWebhookUtility;
import jenkins.model.Jenkins;
import org.apache.commons.io.IOUtils;
import org.jenkinsci.plugins.workflow.job.WorkflowJob;
import org.kohsuke.stapler.HttpResponse;
import org.kohsuke.stapler.StaplerRequest;
import org.kohsuke.stapler.interceptor.RequirePOST;
import hudson.security.csrf.CrumbExclusion;
import javax.servlet.FilterChain;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import java.io.StringWriter;
import java.util.ArrayList;
import java.util.Collection;

import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.JSONValue;
import org.json.simple.parser.ParseException;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;

@Extension
public class GithubWebhookBuildTriggerAction implements UnprotectedRootAction {

    private static final String URL_NAME = "github-webhook-build-trigger";

    @Override
    public String getUrlName() {
        return URL_NAME;
    }

    @Override
    public String getIconFileName() {
        return null;
    }

    @Override
    public String getDisplayName() {
        return URL_NAME;
    }

    /*
     * http://jenkins.foo/github-webhook-build-trigger/receive
     */
    @RequirePOST
    public HttpResponse doReceive(HttpServletRequest request, StaplerRequest staplerRequest) throws IOException, ServletException {
        StringWriter writer = new StringWriter();
        IOUtils.copy(request.getInputStream(), writer, "UTF-8");
        String requestBody = writer.toString();

        if (requestBody == null) {
            return HttpResponses.error(500, this.getTextEnvelopedInBanner("   ERROR: payload json is empty at least requestBody is empty!"));
        }

        StringBuilder info = new StringBuilder();

        //
        // WEBHOOK SECRET
        //
        String githubSignature = request.getHeader("x-hub-signature");
        String webhookSecretAsConfiguredByUser = GithubWebhookBuildTriggerPluginBuilder.DescriptorImpl.getDescriptor().getWebhookSecret();
        String webhookSecretMessage ="validating webhook payload against wevhook secret.";
        info.append(">> webhook secret validation").append("\n");
        if (webhookSecretAsConfiguredByUser == null || webhookSecretAsConfiguredByUser.isEmpty()) {
            webhookSecretMessage = "   skipping validation since no webhook secret is configured in \n" +
                                   "   'Jenkins' -> 'Configure' tab under 'Github Webhook Build Trigger' section.";
        } else {
            Boolean isValid = GitHubWebhookUtility.verifySignature(requestBody, githubSignature, webhookSecretAsConfiguredByUser);
            if (!isValid) {
                info.append(webhookSecretMessage).append("\n");
                return HttpResponses.error(500, this.getTextEnvelopedInBanner(info.toString() + "   ERROR: github webhook secret signature check failed. Check your webhook secret."));
            }
            webhookSecretMessage = "   ok. Webhook secret validates against " +  githubSignature + "\n";
        }
        info.append(webhookSecretMessage).append("\n");
        
        try {

            JSONObject requestJsonObject = (JSONObject) JSONValue.parseWithException(requestBody);

            //
            // LOAD REQUEST TO TEMP-FILE
            //
            final String TEMP_FILE_NAME = "ghbt-jenkins-request";
            final String TEMP_FILE_EXT = ".tmp";
            String payload_abs_path = null;
            try {
                final File temp = File.createTempFile(TEMP_FILE_NAME, TEMP_FILE_EXT);
                payload_abs_path = temp.getAbsolutePath();
            } catch (IOException e) {
                return HttpResponses.error(500, this.getTextEnvelopedInBanner("   ERROR: on create temp-file for GH request: " + e.toString()));
            }
            try (FileWriter file = new FileWriter(payload_abs_path)) {
                file.write(requestJsonObject.toJSONString());
                file.flush();
            } catch (IOException e) {
                return HttpResponses.error(500, this.getTextEnvelopedInBanner("   ERROR: on write to temp-file of GH request: " + e.toString()));
            }
            //info.append("Temp-file with GH request: " + payload_abs_path).append("\n\n");


            String github_event = request.getHeader("x-github-event");
            switch(github_event) {
                case "push": {

                    String full_name = null;
                    String checkout_ref = null;
                    String clone_url = null;
                    try {
                        JSONObject repositoryJsonObject = (JSONObject) JSONValue.parseWithException(requestJsonObject.get("repository").toString());
                        JSONObject commitsJsonObject = (JSONObject) JSONValue.parseWithException(requestJsonObject.get("commits").toString());

                        full_name = repositoryJsonObject.get("full_name").toString();
                        //info.append(full_name).append("\n");
                        checkout_ref = commitsJsonObject.get("id").toString();
                        //info.append(checkout_ref).append("\n");
                        clone_url = repositoryJsonObject.get("clone_url").toString();
                        //info.append(clone_url).append("\n\n");

                    } catch (Exception ex) {
                        return HttpResponses.error(500, this.getTextEnvelopedInBanner("   ERROR: on parsing JSON objects: " + ex.toString()));
                    }

                    //
                    // PAYLOAD TO ENVVARS
                    //
                    _EnvironmentContributionAction environmentContributionAction = new _EnvironmentContributionAction(full_name, checkout_ref, clone_url, payload_abs_path, github_event);

                    //
                    // TRIGGER JOBS
                    //
                    String jobNamePrefix = this.normalizeRepoFullName(full_name);
                    StringBuilder jobsTriggered = new StringBuilder();
                    ArrayList<String> jobsAlreadyTriggered = new ArrayList<>();

                    StringBuilder causeNote = new StringBuilder();
                    causeNote.append("github-webhook-build-trigger-plugin:\n");
                    causeNote.append("full_name: " + full_name).append("\n");
                    causeNote.append("checkout_ref: " + checkout_ref).append("\n");
                    causeNote.append("clone_url: " + clone_url).append("\n");
                    causeNote.append("absolutePath: " + payload_abs_path).append("\n");
                    causeNote.append("github_event: " + github_event).append("\n");
                    Cause cause = new Cause.RemoteCause("github.com", causeNote.toString());

                    Collection<Job> jobs = Jenkins.getInstance().getAllItems(Job.class);
                    if (jobs.isEmpty()) {
                        jobsTriggered.append("   WARNING NO JOBS FOUND!\n");
                        jobsTriggered.append("      You either have no jobs or if you are using matrix-based security,\n");
                        jobsTriggered.append("      please give the following rights to 'Anonymous':\n");
                        jobsTriggered.append("      'Job' -> build, discover, read.\n");
                    }
                    for (Job job: jobs) {
                        if (job.getName().startsWith(jobNamePrefix) && ! jobsAlreadyTriggered.contains(job.getName())) {
                            jobsAlreadyTriggered.add(job.getName());
                            if (job instanceof WorkflowJob) {
                                WorkflowJob wjob = (WorkflowJob) job;
                                if (wjob.isBuildable()) {
                                    jobsTriggered.append("   WORKFLOWJOB> ").append(job.getName()).append(" TRIGGERED\n");
                                    wjob.scheduleBuild2(0, environmentContributionAction.transform(), new CauseAction(cause));
                                } else {
                                    jobsTriggered.append("   WORKFLOWJOB> ").append(job.getName()).append(" NOT BUILDABLE. SKIPPING.\n");
                                }
                            } else {
                                AbstractProject projectScheduable = (AbstractProject) job;
                                if (job.isBuildable()) {
                                    jobsTriggered.append("   CLASSICJOB>  ").append(job.getName()).append(" TRIGGERED\n");
                                    projectScheduable.scheduleBuild(0, cause, environmentContributionAction);
                                } else {
                                    jobsTriggered.append("   CLASSICJOB>  ").append(job.getName()).append(" NOT BUILDABLE. SKIPPING.\n");
                                }
                            }
                        }
                    }
                    //
                    // WRITE ADDITONAL INFO
                    //
                    info.append(">> webhook content to env vars").append("\n");
                    info.append(environmentContributionAction.getEnvVarInfo());
                    info.append("\n");
                    info.append(">> jobs triggered with name matching '").append(jobNamePrefix).append("*'").append("\n");
                    info.append(jobsTriggered.toString());
                    return HttpResponses.plainText(this.getTextEnvelopedInBanner(info.toString()));
                }

                case "ping": {

                    //
                    // CHECK IF INITIAL REQUEST (see test-webhook-init-payload.json)
                    // See: https://developer.github.com/webhooks/#ping-event
                    //
                    if (requestJsonObject.get("hook_id").toString() != null) {
                        info.append(">> ping request received: your webhook with ID ");
                        info.append(requestJsonObject.get("hook_id").toString());
                        info.append(" is working :)\n");
                        return HttpResponses.plainText(this.getTextEnvelopedInBanner(info.toString()));
                    }
                    return HttpResponses.error(500, this.getTextEnvelopedInBanner(info.toString() + "   ERROR: requestJsonObject.get(\"hook_id\").toString() != null"));
                }


                case "release": {

                    String full_name = null;
                    String checkout_ref = null;
                    String clone_url = null;
                    try {
                        JSONObject repositoryJsonObject = (JSONObject) JSONValue.parseWithException(requestJsonObject.get("repository").toString());
                        JSONObject releaseJsonObject = (JSONObject) JSONValue.parseWithException(requestJsonObject.get("release").toString());

                        full_name = repositoryJsonObject.get("full_name").toString();
                        //info.append(full_name).append("\n");
                        checkout_ref = releaseJsonObject.get("tag_name").toString();
                        //info.append(checkout_ref).append("\n");
                        clone_url = repositoryJsonObject.get("clone_url").toString();
                        //info.append(clone_url).append("\n\n");

                    } catch (Exception ex) {
                        return HttpResponses.error(500, this.getTextEnvelopedInBanner("   ERROR: on parsing JSON objects: " + ex.toString()));
                    }

                    //
                    // PAYLOAD TO ENVVARS
                    //
                    _EnvironmentContributionAction environmentContributionAction = new _EnvironmentContributionAction(full_name, checkout_ref, clone_url, payload_abs_path, github_event);

                    //
                    // TRIGGER JOBS
                    //
                    String jobNamePrefix = this.normalizeRepoFullName(full_name);
                    StringBuilder jobsTriggered = new StringBuilder();
                    ArrayList<String> jobsAlreadyTriggered = new ArrayList<>();

                    StringBuilder causeNote = new StringBuilder();
                    causeNote.append("github-webhook-build-trigger-plugin:\n");
                    causeNote.append("full_name: " + full_name).append("\n");
                    causeNote.append("checkout_ref: " + checkout_ref).append("\n");
                    causeNote.append("clone_url: " + clone_url).append("\n");
                    causeNote.append("absolutePath: " + payload_abs_path).append("\n");
                    causeNote.append("github_event: " + github_event).append("\n");
                    Cause cause = new Cause.RemoteCause("github.com", causeNote.toString());

                    Collection<Job> jobs = Jenkins.getInstance().getAllItems(Job.class);
                    if (jobs.isEmpty()) {
                        jobsTriggered.append("   WARNING NO JOBS FOUND!\n");
                        jobsTriggered.append("      You either have no jobs or if you are using matrix-based security,\n");
                        jobsTriggered.append("      please give the following rights to 'Anonymous':\n");
                        jobsTriggered.append("      'Job' -> build, discover, read.\n");
                    }
                    for (Job job: jobs) {
                        if (job.getName().startsWith(jobNamePrefix) && ! jobsAlreadyTriggered.contains(job.getName())) {
                            jobsAlreadyTriggered.add(job.getName());
                            if (job instanceof WorkflowJob) {
                                WorkflowJob wjob = (WorkflowJob) job;
                                if (wjob.isBuildable()) {
                                    jobsTriggered.append("   WORKFLOWJOB> ").append(job.getName()).append(" TRIGGERED\n");
                                    wjob.scheduleBuild2(0, environmentContributionAction.transform(), new CauseAction(cause));
                                } else {
                                    jobsTriggered.append("   WORKFLOWJOB> ").append(job.getName()).append(" NOT BUILDABLE. SKIPPING.\n");
                                }
                            } else {
                                AbstractProject projectScheduable = (AbstractProject) job;
                                if (job.isBuildable()) {
                                    jobsTriggered.append("   CLASSICJOB>  ").append(job.getName()).append(" TRIGGERED\n");
                                    projectScheduable.scheduleBuild(0, cause, environmentContributionAction);
                                } else {
                                    jobsTriggered.append("   CLASSICJOB>  ").append(job.getName()).append(" NOT BUILDABLE. SKIPPING.\n");
                                }
                            }
                        }
                    }
                    //
                    // WRITE ADDITONAL INFO
                    //
                    info.append(">> webhook content to env vars").append("\n");
                    info.append(environmentContributionAction.getEnvVarInfo());
                    info.append("\n");
                    info.append(">> jobs triggered with name matching '").append(jobNamePrefix).append("*'").append("\n");
                    info.append(jobsTriggered.toString());
                    return HttpResponses.plainText(this.getTextEnvelopedInBanner(info.toString()));
                }

                default:
                    return HttpResponses.error(500, this.getTextEnvelopedInBanner(info.toString() + "   ERROR: No request.getHeader(\"x-github-event\")"));
            }
        } catch (ParseException ex) {
            return HttpResponses.error(500, this.getTextEnvelopedInBanner(info.toString() + "   ERROR: ParseException: github webhook JSON invalid"));
        }
    }

    /*
     * converts "codeclou/foo" to "codeclou---foo"
     */
    private String normalizeRepoFullName(String reponame) {
        return reponame.replace("/", "---");
    }

    private String getTextEnvelopedInBanner(String text) {
        StringBuilder banner = new StringBuilder();
        banner.append("\n----------------------------------------------------------------------------------\n");
        banner.append("   github-webhook-build-trigger-plugin").append("\n");
        banner.append("----------------------------------------------------------------------------------\n");
        banner.append(text);
        banner.append("\n----------------------------------------------------------------------------------\n");
        return banner.toString();
    }

    @Extension
    public static class TriggerActionCrumbExclusion extends CrumbExclusion {

        @Override
        public boolean process(HttpServletRequest req, HttpServletResponse resp, FilterChain chain) throws IOException, ServletException {
            String pathInfo = req.getPathInfo();
            if (pathInfo != null && pathInfo.contains(getExclusionPath())) {
                chain.doFilter(req, resp);
                return true;
            }
            return false;
        }

        public String getExclusionPath() {
            return "/" + URL_NAME + "/";
        }
    }
}
