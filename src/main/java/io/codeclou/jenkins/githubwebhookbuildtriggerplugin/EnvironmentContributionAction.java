/*
 * Licensed under MIT License
 * Copyright (c) 2017 Bernhard Grünewaldt
 * Copyright (c) 2017 Denis Yeldandi
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
public class EnvironmentContributionAction implements EnvironmentContributingAction {

    private transient Map<String, String> environmentVariables = new HashMap<>();

    public EnvironmentContributionAction(EnvironmentContributionAction eca) {
        for (String key : eca.environmentVariables.keySet()) {
            this.environmentVariables.put(key, eca.environmentVariables.get(key));
        }
    }

    public EnvironmentContributionAction(GithubWebhookPayload payload) {
        String normalizedBranch = this.normalizeBranchNameOrEmptyString(payload.getRef());
        String normalizedTag = this.normalizeTagNameOrEmptyString(payload.getRef());
	String normalizedJFlags = this.normalizeJFlagsOrEmptyString(payload.getJFlags());
	this.environmentVariables.put("GWBT_TYPE", payload.getType());
        this.environmentVariables.put("GWBT_REF", payload.getRef());
        this.environmentVariables.put("GWBT_TAG", normalizedTag);
        this.environmentVariables.put("GWBT_BRANCH", normalizedBranch);
        this.environmentVariables.put("GWBT_COMMIT_BEFORE", payload.getBefore());
        this.environmentVariables.put("GWBT_COMMIT_AFTER", payload.getAfter());
	this.environmentVariables.put("GWBT_FLAGS", normalizedJFlags);
        this.environmentVariables.put("GWBT_REPO_CLONE_URL", payload.getRepository().getClone_url());
        this.environmentVariables.put("GWBT_REPO_HTML_URL", payload.getRepository().getHtml_url());
        this.environmentVariables.put("GWBT_REPO_FULL_NAME", payload.getRepository().getFull_name());
        this.environmentVariables.put("GWBT_REPO_NAME", payload.getRepository().getName());

	if (payload.isReleaseTag()) {
		this.environmentVariables.put("GWBT_RELEASE", payload.getRelease());
	}

        GithubWebhookPayload.GithubWebhookPayloadSender sender = payload.getSender();
        if (sender != null) {
            String login = sender.getLogin();
            if (login != null) {
                this.environmentVariables.put("GWBT_SENDER_LOGIN", login);
            }
        }

        GithubWebhookPayload.GithubWebhookPayloadPerson pusher = payload.getPusher();
        if (pusher != null) {
            String email = pusher.getEmail();
            if (email != null) {
                this.environmentVariables.put("GWBT_PUSHER_EMAIL", email);
            }
        }
    }

    public void switchToCommitFlags(String ref, ArrayList<GithubWebhookPayload.GithubWebhookPayloadJenkinsFlag> jFlags, GithubWebhookPayload.GithubWebhookPayloadPerson committer) {
        String normalizedJFlags = this.normalizeJFlagsOrEmptyString(jFlags);
        this.environmentVariables.put("GWBT_FLAGS", normalizedJFlags);
        this.environmentVariables.put("GWBT_COMMIT", ref);
        if (committer != null) {
            String email = committer.getEmail();
            if (email != null) {
                this.environmentVariables.put("GWBT_COMMIT_COMMITTER", email);
            }
        }
    }

    private String normalizeJFlagsOrEmptyString(ArrayList<GithubWebhookPayload.GithubWebhookPayloadJenkinsFlag> jFlags) {
	StringBuilder out = new StringBuilder();
	for (GithubWebhookPayload.GithubWebhookPayloadJenkinsFlag flag : jFlags) {
            if (out.length() > 0) {
                out.append(" ");
            }
            if (flag.hasValue()) {
                out.append(flag.getName()).append("=").append(flag.getValue());
            } else {
                out.append(flag.getName());
            }
	}
        return out.toString();
    }

    /*
     * converts "refs/heads/develop" to "develop"
     */
    private String normalizeBranchNameOrEmptyString(String branchname) {
        if (branchname != null && branchname.startsWith("refs/heads/")) {
            return branchname.replace("refs/heads/", "");
        }
        return "";
    }

    /*
     * converts "refs/tags/1.0.0" to "1.0.0"
     */
    private String normalizeTagNameOrEmptyString(String tagname) {
        if (tagname != null && tagname.startsWith("refs/tags/")) {
            return tagname.replace("refs/tags/", "");
        }
        return "";
    }

    protected String getEnvVarInfo() {
        StringBuilder ret = new StringBuilder();
        for (String key : this.environmentVariables.keySet()) {
            ret.append(key).append(":").append(environmentVariables.get(key)).append("\n");
        }
        return ret.toString();
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
