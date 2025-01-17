/*
 * Licensed under MIT License
 * Copyright (c) 2017 Bernhard Grünewaldt
 * Copyright (c) 2019 Denis Yeldandi
 */
package io.codeclou.jenkins.githubwebhookbuildtriggerplugin;

import com.google.gson.Gson;
import com.google.gson.JsonSyntaxException;
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
import java.io.IOException;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.Collection;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import org.joda.time.DateTime;

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

    private String triggerJob(String jobNamePrefix, EnvironmentContributionAction environmentContributionAction) {
        StringBuilder jobsTriggered = new StringBuilder();
        ArrayList<String> jobsAlreadyTriggered = new ArrayList<>();
        StringBuilder causeNote = new StringBuilder();
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
        return jobsTriggered.toString();
    }


    /*
     * http://jenkins.foo/github-webhook-build-trigger/receive
     */
    @RequirePOST
    public HttpResponse doReceive(HttpServletRequest request, StaplerRequest staplerRequest) throws IOException, ServletException {
        StringWriter writer = new StringWriter();
        IOUtils.copy(request.getInputStream(), writer, "UTF-8");
        String requestBody = writer.toString();
        Gson gson = new GsonBuilder().registerTypeAdapter(new TypeToken<DateTime>(){}.getType(), new GithubWebhookPayload.DateTimeConverter()).create();
        GithubWebhookPayload githubWebhookPayload = gson.fromJson(requestBody, GithubWebhookPayload.class);
	githubWebhookPayload.setType(request.getHeader("x-github-event"));
	githubWebhookPayload.findFlags();
	githubWebhookPayload.findRelease();
        StringBuilder info = new StringBuilder();
        if (githubWebhookPayload == null) {
            return HttpResponses.error(500, this.getTextEnvelopedInBanner("   ERROR: payload json is empty at least requestBody is empty!"));
        }
        try {
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
            info.append(webhookSecretMessage).append("\n\n");

            //
            // CHECK IF INITIAL REQUEST (see test-webhook-init-payload.json)
            // See: https://developer.github.com/webhooks/#ping-event
            //
            if (githubWebhookPayload.getHook_id() != null) {
                info.append(">> ping request received: your webhook with ID ");
                info.append(githubWebhookPayload.getHook_id());
                info.append(" is working :)\n");
                return HttpResponses.plainText(this.getTextEnvelopedInBanner(info.toString()));
            }

            //
            // PAYLOAD TO ENVVARS
            //
            EnvironmentContributionAction environmentContributionAction = new EnvironmentContributionAction(githubWebhookPayload);

            info.append("Type: ").append(githubWebhookPayload.getType()).append("\n");

            if (githubWebhookPayload.getType().equals("push") && githubWebhookPayload.hasJCFlags()) {
                for (GithubWebhookPayload.GithubWebhookPayloadJenkinsCommitFlags cflags : githubWebhookPayload.getJCFlags()) {
                    info.append("Flags: ");
                    for (GithubWebhookPayload.GithubWebhookPayloadJenkinsFlag flag : cflags.getJFlags()) {
                        if (flag.hasValue()) {
                            info.append("[").append(flag.getName()).append("=").append(flag.getValue()).append("] ");
                        } else {
                            info.append("[").append(flag.getName()).append("] ");
                        }
                        info.append("\n");
                    }
                    info.append("\n");
                    EnvironmentContributionAction commitEnvironmentContributionAction = new EnvironmentContributionAction(environmentContributionAction);
                    commitEnvironmentContributionAction.switchToCommitFlags(cflags.getRef(), cflags.getJFlags(), cflags.getCommitter());
                    String jobNamePrefix = this.getJobName(githubWebhookPayload);
                    info.append("Triggering jobs starting with "+jobNamePrefix+"\n");
                    info.append("Environment: \n");
                    info.append(commitEnvironmentContributionAction.getEnvVarInfo());
                    info.append("Triggered: "+this.triggerJob(jobNamePrefix, commitEnvironmentContributionAction)+"\n");
                }
            } else if (githubWebhookPayload.getType().equals("create") && githubWebhookPayload.getRef_type().equals("tag")) {
                String jobNamePrefix = this.getJobName(githubWebhookPayload);
                info.append("Triggering jobs starting with "+jobNamePrefix+"\n");
                info.append("Environment: \n");
                info.append(environmentContributionAction.getEnvVarInfo());
                info.append("Triggered: "+this.triggerJob(jobNamePrefix, environmentContributionAction)+"\n");
            }
            return HttpResponses.plainText(this.getTextEnvelopedInBanner(info.toString()));
        } catch (JsonSyntaxException ex) {
            return HttpResponses.error(500, this.getTextEnvelopedInBanner(info.toString() + "   ERROR: github webhook json invalid"));
        }
    }

    /*
     * converts "codeclou/foo" to "codeclou---foo"
     */
    private String normalizeRepoFullName(String reponame) {
        return reponame.replace("/", "---");
    }

    private String normalizeTagName(String reponame, String ref) {
        if (ref.contains("/")) {
            String[] refsplit = ref.split("/", 2);
            return reponame.replace("/", "---") + "---" + refsplit[0];
        } else {
            return reponame.replace("/", "---") + "---" + ref;
        }
    }

    private String getJobName(GithubWebhookPayload payload) {
        if (payload.getType().equals("push")) {
            return this.normalizeRepoFullName(payload.getRepository().getFull_name());
        } else if (payload.getType().equals("create") && payload.getRef_type().equals("tag") && payload.isReleaseTag()) {
            return "RELEASE---" + this.normalizeRepoFullName(payload.getRepository().getFull_name());
        } else if (payload.getType().equals("create") && payload.getRef_type().equals("tag")) {
            return "TAG---" + this.normalizeTagName(payload.getRepository().getFull_name(), payload.getRef());
        } else {
            return "EVERYTHING---" + this.normalizeRepoFullName(payload.getRepository().getFull_name());
        }
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
